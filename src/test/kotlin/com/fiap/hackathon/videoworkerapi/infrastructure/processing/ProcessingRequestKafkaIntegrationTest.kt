package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MongoTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.application.processing.VideoProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.kafka.core.KafkaTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = [
		"management.server.port=-1",
		"app.storage.minio.initialize-buckets=false",
		"app.outbox.scheduling-enabled=false",
	],
)
@Import(
	MongoTestcontainersConfiguration::class,
	KafkaTestcontainersConfiguration::class,
	StubVideoProcessorConfiguration::class,
)
class ProcessingRequestKafkaIntegrationTest(
	@Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val repository: ProcessingJobRepository,
	@Autowired private val springDataRepository: SpringDataProcessingJobRepository,
	@Autowired private val listener: ProcessingRequestKafkaListener,
	@Autowired private val videoProcessor: RecordingVideoProcessor,
) {
	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
		videoProcessor.processedVideoIds.clear()
	}

	@Test
	fun `valid Kafka payload creates one job`() {
		val event = event()

		kafkaTemplate.send(
			VideoProcessingRequested.TOPIC,
			event.videoId.toString(),
			objectMapper.writeValueAsString(event),
		).get()

		val job = awaitJob(event.videoId)
		assertEquals(event.eventId, job.requestEventId)
		assertEquals(event.customerId, job.customerId)
		assertEquals(event.originalFilename, job.originalFilename.value)
		assertEquals(event.inputObjectKey, job.inputObjectKey.value)
		assertEquals(listOf(event.videoId), videoProcessor.processedVideoIds)
	}

	@Test
	fun `repeated event and video are idempotent`() {
		val event = event()
		listener.consume(record(event))
		listener.consume(record(event))
		val sameVideo = event(eventId = UUID.randomUUID(), videoId = event.videoId)
		listener.consume(record(sameVideo))

		assertEquals(1L, springDataRepository.count())
		assertEquals(event.eventId, repository.findByVideoId(event.videoId)?.requestEventId)
		assertEquals(listOf(event.videoId), videoProcessor.processedVideoIds)
	}

	@Test
	fun `invalid payload is recovered and consumer continues`() {
		val invalidVideoId = UUID.randomUUID()
		kafkaTemplate.send(
			VideoProcessingRequested.TOPIC,
			invalidVideoId.toString(),
			"{\"eventType\":\"VideoProcessingRequested\",\"videoId\":\"invalid\"}",
		).get()

		val valid = event()
		kafkaTemplate.send(
			VideoProcessingRequested.TOPIC,
			valid.videoId.toString(),
			objectMapper.writeValueAsString(valid),
		).get()

		assertNotNull(awaitJob(valid.videoId))
		assertNull(repository.findByVideoId(invalidVideoId))
		assertEquals(1L, springDataRepository.count())
	}

	@Test
	fun `rejects a Kafka key that does not match video id`() {
		val event = event()
		val invalidRecord = ConsumerRecord(
			VideoProcessingRequested.TOPIC,
			0,
			0,
			UUID.randomUUID().toString(),
			objectMapper.writeValueAsString(event),
		)

		val exception = kotlin.test.assertFailsWith<InvalidProcessingRequestException> {
			listener.consume(invalidRecord)
		}

		assertEquals("Invalid video processing request", exception.message)
		assertNull(repository.findByVideoId(event.videoId))
	}

	private fun awaitJob(videoId: UUID) = assertNotNull(pollUntil(Duration.ofSeconds(15)) {
		repository.findByVideoId(videoId)
	})

	private fun record(event: VideoProcessingRequested): ConsumerRecord<String, String> = ConsumerRecord(
		VideoProcessingRequested.TOPIC,
		0,
		0,
		event.videoId.toString(),
		objectMapper.writeValueAsString(event),
	)

	private fun event(
		eventId: UUID = UUID.randomUUID(),
		videoId: UUID = UUID.randomUUID(),
	): VideoProcessingRequested = VideoProcessingRequested(
		eventId = eventId,
		eventType = VideoProcessingRequested.EVENT_TYPE,
		occurredAt = Instant.now().minusSeconds(1),
		videoId = videoId,
		customerId = UUID.randomUUID(),
		originalFilename = "lesson.mp4",
		inputObjectKey = "customers/customer/videos/$videoId/input/lesson.mp4",
	)

	private fun <T> pollUntil(timeout: Duration, action: () -> T?): T? {
		val deadline = System.nanoTime() + timeout.toNanos()
		while (System.nanoTime() < deadline) {
			action()?.let { return it }
			Thread.sleep(100)
		}
		return null
	}
}

@TestConfiguration(proxyBeanMethods = false)
class StubVideoProcessorConfiguration {
	@Bean
	@Primary
	fun recordingVideoProcessor(): RecordingVideoProcessor = RecordingVideoProcessor()
}

class RecordingVideoProcessor : VideoProcessor {
	val processedVideoIds = CopyOnWriteArrayList<UUID>()

	override fun process(videoId: UUID) {
		processedVideoIds.add(videoId)
	}
}
