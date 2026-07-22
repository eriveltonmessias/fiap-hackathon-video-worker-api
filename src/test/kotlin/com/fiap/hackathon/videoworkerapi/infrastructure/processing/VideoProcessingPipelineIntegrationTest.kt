package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.MinioTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MongoTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.HandleVideoProcessingRequest
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.application.processing.StorageBucket
import com.fiap.hackathon.videoworkerapi.application.processing.VideoProcessingRequest
import com.fiap.hackathon.videoworkerapi.application.processing.VideoProcessor
import com.fiap.hackathon.videoworkerapi.application.processing.VideoStorage
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.infrastructure.storage.MinioBucketInitializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
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
		"spring.kafka.listener.auto-startup=false",
		"app.ffmpeg.max-frames=3",
		"app.outbox.scheduling-enabled=false",
	],
)
@Import(MongoTestcontainersConfiguration::class, MinioTestcontainersConfiguration::class)
class VideoProcessingPipelineIntegrationTest(
	@Autowired private val handler: HandleVideoProcessingRequest,
	@Autowired private val processor: VideoProcessor,
	@Autowired private val repository: ProcessingJobRepository,
	@Autowired private val springDataRepository: SpringDataProcessingJobRepository,
	@Autowired private val storage: VideoStorage,
	@Autowired private val bucketInitializer: MinioBucketInitializer,
) {
	@TempDir
	lateinit var testFiles: Path

	@BeforeEach
	fun prepareDependencies() {
		springDataRepository.deleteAll()
		bucketInitializer.initializeBuckets()
	}

	@Test
	fun `downloads video generates frames uploads zip and completes persisted job`() {
		val videoId = UUID.randomUUID()
		val customerId = UUID.randomUUID()
		val inputKey = ObjectKey.of("customers/$customerId/videos/$videoId/input/lesson.mp4")
		val videoFile = createVideo()
		Files.newInputStream(videoFile).use { video ->
			storage.upload(
				StorageBucket.INPUT,
				inputKey,
				video,
				Files.size(videoFile),
				"video/mp4",
			)
		}
		handler.handle(
			VideoProcessingRequest(
				eventId = UUID.randomUUID(),
				occurredAt = Instant.now().minusSeconds(1),
				videoId = videoId,
				customerId = customerId,
				originalFilename = OriginalFilename.of("lesson.mp4"),
				inputObjectKey = inputKey,
			),
		)

		processor.process(videoId)

		val job = assertNotNull(repository.findByVideoId(videoId))
		assertEquals(ProcessingJobStatus.COMPLETED, job.status)
		assertEquals(3, job.frameCount)
		assertEquals(
			"customers/$customerId/videos/$videoId/output/frames.zip",
			job.outputObjectKey?.value,
		)
		assertNotNull(job.startedAt)
		assertNotNull(job.finishedAt)
		assertNull(job.failureReason)
		val frames = storage.download(StorageBucket.OUTPUT, assertNotNull(job.outputObjectKey)).use(::zipFrames)
		assertEquals(3, frames.size)
		assertEquals(listOf("frame-000001.jpg", "frame-000002.jpg", "frame-000003.jpg"), frames.map { it.first })
		frames.forEach { (_, content) ->
			assertTrue(content.size > 2)
			assertEquals(0xff.toByte(), content[0])
			assertEquals(0xd8.toByte(), content[1])
		}
		assertTrue(Files.list(WORKSPACE_ROOT).use { it.findAny().isEmpty })
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

	companion object {
		private val WORKSPACE_ROOT: Path = Files.createTempDirectory("video-pipeline-test-")

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
