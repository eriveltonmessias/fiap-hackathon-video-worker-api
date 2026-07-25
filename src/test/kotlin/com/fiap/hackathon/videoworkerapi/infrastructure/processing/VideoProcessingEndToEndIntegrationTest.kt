package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MinioTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MongoTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.DispatchProcessingResults
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.application.processing.StorageBucket
import com.fiap.hackathon.videoworkerapi.application.processing.VideoStorage
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import com.fiap.hackathon.videoworkerapi.infrastructure.storage.MinioBucketInitializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = [
		"management.server.port=-1",
		"app.ffmpeg.max-frames=3",
		"app.outbox.scheduling-enabled=false",
		"app.kafka.retry.interval=10ms",
	],
)
@Import(
	MongoTestcontainersConfiguration::class,
	KafkaTestcontainersConfiguration::class,
	MinioTestcontainersConfiguration::class,
)
class VideoProcessingEndToEndIntegrationTest(
	@Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val dispatcher: DispatchProcessingResults,
	@Autowired private val repository: ProcessingJobRepository,
	@Autowired private val springDataRepository: SpringDataProcessingJobRepository,
	@Autowired private val storage: VideoStorage,
	@Autowired private val bucketInitializer: MinioBucketInitializer,
	@Autowired private val kafkaContainer: KafkaContainer,
) {
	@TempDir
	lateinit var testFiles: Path

	@BeforeEach
	fun prepareDependencies() {
		springDataRepository.deleteAll()
		bucketInitializer.initializeBuckets()
	}

	@Test
	fun `request produces zip and success event while duplicate remains idempotent`() {
		val request = request()
		val inputKey = ObjectKey.of(request.inputObjectKey)
		uploadInput(inputKey, createVideo())

		publish(request)

		val completed = awaitJob(request.videoId, ProcessingJobStatus.COMPLETED)
		assertEquals(1, completed.attempts)
		assertEquals(3, completed.frameCount)
		assertEquals(
			"customers/${request.customerId}/videos/${request.videoId}/output/frames.zip",
			completed.outputObjectKey?.value,
		)
		assertNull(completed.failureReason)
		assertZip(completed)
		assertWorkspaceIsEmpty()

		assertEquals(1, dispatcher.dispatch().published)
		assertCompletedContract(completed, awaitResult(VideoProcessed.TOPIC, request.videoId))

		publish(request)
		Thread.sleep(DUPLICATE_SETTLE_TIME.toMillis())

		val afterDuplicate = assertNotNull(repository.findByVideoId(request.videoId))
		assertEquals(1L, springDataRepository.count())
		assertEquals(ProcessingJobStatus.COMPLETED, afterDuplicate.status)
		assertEquals(1, afterDuplicate.attempts)
		assertTrue(repository.findPendingResults(10).isEmpty())
		assertWorkspaceIsEmpty()
	}

	@Test
	fun `missing input produces failed event and cleans temporary workspace`() {
		val request = request()

		publish(request)

		val failed = awaitJob(request.videoId, ProcessingJobStatus.FAILED)
		assertEquals(1, failed.attempts)
		assertEquals("Input video was not found", failed.failureReason?.value)
		assertNull(failed.outputObjectKey)
		assertWorkspaceIsEmpty()

		assertEquals(1, dispatcher.dispatch().published)
		assertFailedContract(failed, awaitResult(VideoProcessingFailed.TOPIC, request.videoId))
		assertTrue(repository.findPendingResults(10).isEmpty())
	}

	private fun uploadInput(inputKey: ObjectKey, videoFile: Path) {
		Files.newInputStream(videoFile).use { video ->
			storage.upload(
				StorageBucket.INPUT,
				inputKey,
				video,
				Files.size(videoFile),
				"video/mp4",
			)
		}
	}

	private fun publish(request: VideoProcessingRequested) {
		kafkaTemplate.send(
			VideoProcessingRequested.TOPIC,
			request.videoId.toString(),
			objectMapper.writeValueAsString(request),
		).get()
	}

	private fun awaitJob(videoId: UUID, status: ProcessingJobStatus): VideoProcessingJob =
		assertNotNull(pollUntil(Duration.ofSeconds(20)) {
			repository.findByVideoId(videoId)?.takeIf { it.status == status }
		})

	private fun awaitResult(topic: String, videoId: UUID): ConsumerRecord<String, String> {
		val properties = mapOf<String, Any>(
			ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
			ConsumerConfig.GROUP_ID_CONFIG to "end-to-end-test-${UUID.randomUUID()}",
			ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
			ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
			ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
		)
		return KafkaConsumer<String, String>(properties).use { consumer ->
			consumer.subscribe(listOf(topic))
			val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
			while (System.nanoTime() < deadline) {
				consumer.poll(Duration.ofMillis(250)).forEach { record ->
					if (record.key() == videoId.toString()) return@use record
				}
			}
			error("Processing result event was not received")
		}
	}

	private fun assertCompletedContract(job: VideoProcessingJob, record: ConsumerRecord<String, String>) {
		val event = objectMapper.readTree(record.value())
		assertCommonContract(event, job, VideoProcessed.EVENT_TYPE)
		assertEquals(job.outputObjectKey?.value, event.path("outputObjectKey").stringValue())
		assertEquals(5, event.size())
	}

	private fun assertFailedContract(job: VideoProcessingJob, record: ConsumerRecord<String, String>) {
		val event = objectMapper.readTree(record.value())
		assertCommonContract(event, job, VideoProcessingFailed.EVENT_TYPE)
		assertEquals(job.failureReason?.value, event.path("failureReason").stringValue())
		assertEquals(5, event.size())
	}

	private fun assertCommonContract(event: JsonNode, job: VideoProcessingJob, eventType: String) {
		assertEquals(job.videoId.toString(), event.path("videoId").stringValue())
		assertEquals(job.resultOutbox?.eventId.toString(), event.path("eventId").stringValue())
		assertEquals(job.resultOutbox?.occurredAt.toString(), event.path("occurredAt").stringValue())
		assertEquals(eventType, event.path("eventType").stringValue())
	}

	private fun assertZip(job: VideoProcessingJob) {
		val frames = storage.download(StorageBucket.OUTPUT, assertNotNull(job.outputObjectKey)).use(::zipFrames)
		assertEquals(3, frames.size)
		assertEquals(listOf("frame-000001.jpg", "frame-000002.jpg", "frame-000003.jpg"), frames.map { it.first })
		frames.forEach { (_, content) ->
			assertTrue(content.size > 2)
			assertEquals(0xff.toByte(), content[0])
			assertEquals(0xd8.toByte(), content[1])
		}
	}

	private fun createVideo(): Path {
		val output = testFiles.resolve("sample.mp4")
		val process = ProcessBuilder(
			"ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
			"-f", "lavfi", "-i", "color=c=blue:s=64x64:d=4",
			"-r", "1", "-c:v", "mpeg4", output.toString(),
		)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start()
		assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Test video generation timed out")
		assertEquals(0, process.exitValue(), "Could not generate the test video with FFmpeg")
		return output
	}

	private fun zipFrames(input: java.io.InputStream): List<Pair<String, ByteArray>> {
		val frames = mutableListOf<Pair<String, ByteArray>>()
		ZipInputStream(input).use { zip ->
			var entry = zip.nextEntry
			while (entry != null) {
				frames.add(entry.name to zip.readAllBytes())
				entry = zip.nextEntry
			}
		}
		return frames
	}

	private fun assertWorkspaceIsEmpty() {
		assertTrue(Files.list(WORKSPACE_ROOT).use { it.findAny().isEmpty })
	}

	private fun request(): VideoProcessingRequested {
		val videoId = UUID.randomUUID()
		val customerId = UUID.randomUUID()
		return VideoProcessingRequested(
			eventId = UUID.randomUUID(),
			eventType = VideoProcessingRequested.EVENT_TYPE,
			occurredAt = Instant.now().minusSeconds(1),
			videoId = videoId,
			customerId = customerId,
			originalFilename = "lesson.mp4",
			inputObjectKey = "customers/$customerId/videos/$videoId/input/lesson.mp4",
		)
	}

	private fun <T> pollUntil(timeout: Duration, action: () -> T?): T? {
		val deadline = System.nanoTime() + timeout.toNanos()
		while (System.nanoTime() < deadline) {
			action()?.let { return it }
			Thread.sleep(100)
		}
		return null
	}

	companion object {
		private val WORKSPACE_ROOT: Path = Files.createTempDirectory("video-end-to-end-test-")
		private val DUPLICATE_SETTLE_TIME: Duration = Duration.ofMillis(500)

		@JvmStatic
		@DynamicPropertySource
		fun processingProperties(registry: DynamicPropertyRegistry) {
			registry.add("app.processing.temporary-directory") { WORKSPACE_ROOT.toString() }
		}

		@JvmStatic
		@AfterAll
		fun deleteWorkspaceRoot() {
			Files.walk(WORKSPACE_ROOT).use { paths ->
				paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
			}
		}
	}
}
