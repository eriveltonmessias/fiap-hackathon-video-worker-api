package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MongoTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.DispatchProcessingResults
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.domain.processing.FailureReason
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = [
		"management.server.port=-1",
		"spring.kafka.listener.auto-startup=false",
		"app.storage.minio.initialize-buckets=false",
		"app.outbox.scheduling-enabled=false",
	],
)
@Import(MongoTestcontainersConfiguration::class, KafkaTestcontainersConfiguration::class)
class ProcessingResultOutboxIntegrationTest(
	@Autowired private val dispatcher: DispatchProcessingResults,
	@Autowired private val repository: ProcessingJobRepository,
	@Autowired private val springDataRepository: SpringDataProcessingJobRepository,
	@Autowired private val objectMapper: ObjectMapper,
	@Autowired private val kafkaContainer: KafkaContainer,
) {
	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
	}

	@Test
	fun `publishes completed and failed contracts then confirms their outboxes`() {
		val completed = completedJob()
		val failed = failedJob()
		repository.save(completed)
		repository.save(failed)

		val summary = dispatcher.dispatch()

		assertEquals(2, summary.published)
		assertEquals(0, summary.failed)
		val records = consumeResults()
		assertCompletedContract(completed, assertNotNull(records[VideoProcessed.TOPIC]))
		assertFailedContract(failed, assertNotNull(records[VideoProcessingFailed.TOPIC]))
		assertTrue(repository.findPendingResults(10).isEmpty())
		assertNotNull(repository.findByVideoId(completed.videoId)?.resultOutbox?.publishedAt)
		assertNotNull(repository.findByVideoId(failed.videoId)?.resultOutbox?.publishedAt)
	}

	private fun consumeResults(): Map<String, ConsumerRecord<String, String>> {
		val properties = mapOf<String, Any>(
			ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
			ConsumerConfig.GROUP_ID_CONFIG to "outbox-test-${UUID.randomUUID()}",
			ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
			ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
			ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
		)
		return KafkaConsumer<String, String>(properties).use { consumer ->
			consumer.subscribe(listOf(VideoProcessed.TOPIC, VideoProcessingFailed.TOPIC))
			val recordsByTopic = mutableMapOf<String, ConsumerRecord<String, String>>()
			val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
			while (recordsByTopic.size < 2 && System.nanoTime() < deadline) {
				consumer.poll(Duration.ofMillis(250)).forEach { recordsByTopic[it.topic()] = it }
			}
			recordsByTopic
		}
	}

	private fun assertCompletedContract(job: VideoProcessingJob, record: ConsumerRecord<String, String>) {
		val event = objectMapper.readTree(record.value())
		assertEquals(job.videoId.toString(), record.key())
		assertCommonContract(event, job, VideoProcessed.EVENT_TYPE)
		assertEquals(job.outputObjectKey?.value, event.path("outputObjectKey").stringValue())
		assertEquals(5, event.size())
	}

	private fun assertFailedContract(job: VideoProcessingJob, record: ConsumerRecord<String, String>) {
		val event = objectMapper.readTree(record.value())
		assertEquals(job.videoId.toString(), record.key())
		assertCommonContract(event, job, VideoProcessingFailed.EVENT_TYPE)
		assertEquals(job.failureReason?.value, event.path("failureReason").stringValue())
		assertEquals(5, event.size())
	}

	private fun assertCommonContract(event: JsonNode, job: VideoProcessingJob, eventType: String) {
		val outbox = assertNotNull(job.resultOutbox)
		assertEquals(outbox.eventId.toString(), event.path("eventId").stringValue())
		assertEquals(eventType, event.path("eventType").stringValue())
		assertEquals(outbox.occurredAt.toString(), event.path("occurredAt").stringValue())
		assertEquals(job.videoId.toString(), event.path("videoId").stringValue())
	}

	private fun completedJob(): VideoProcessingJob = newJob(COMPLETED_VIDEO_ID).also {
		it.start(RECEIVED_AT.plusSeconds(1))
		it.markGeneratingFrames(RECEIVED_AT.plusSeconds(2))
		it.markCompressing(3, RECEIVED_AT.plusSeconds(3))
		it.markUploadingResult(RECEIVED_AT.plusSeconds(4))
		it.complete(
			ObjectKey.of("customers/$CUSTOMER_ID/videos/$COMPLETED_VIDEO_ID/output/frames.zip"),
			COMPLETED_EVENT_ID,
			RECEIVED_AT.plusSeconds(5),
		)
	}

	private fun failedJob(): VideoProcessingJob = newJob(FAILED_VIDEO_ID).also {
		it.fail(
			FailureReason.of("Input video was not found"),
			FAILED_EVENT_ID,
			RECEIVED_AT.plusSeconds(6),
		)
	}

	private fun newJob(videoId: UUID): VideoProcessingJob = VideoProcessingJob.receive(
		id = UUID.randomUUID(),
		requestEventId = UUID.randomUUID(),
		videoId = videoId,
		customerId = CUSTOMER_ID,
		originalFilename = OriginalFilename.of("lesson.mp4"),
		inputObjectKey = ObjectKey.of("customers/$CUSTOMER_ID/videos/$videoId/input/lesson.mp4"),
		receivedAt = RECEIVED_AT,
	)

	private companion object {
		val RECEIVED_AT: Instant = Instant.parse("2026-01-01T10:00:00Z")
		val CUSTOMER_ID: UUID = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc")
		val COMPLETED_VIDEO_ID: UUID = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f")
		val FAILED_VIDEO_ID: UUID = UUID.fromString("efc02bb9-a370-4019-9021-dddacbfc3400")
		val COMPLETED_EVENT_ID: UUID = UUID.fromString("ef2ec18e-9c74-4613-bf03-0a8ab1a762f1")
		val FAILED_EVENT_ID: UUID = UUID.fromString("1894f104-b09c-4aa2-93c5-c03868c1c696")
	}
}
