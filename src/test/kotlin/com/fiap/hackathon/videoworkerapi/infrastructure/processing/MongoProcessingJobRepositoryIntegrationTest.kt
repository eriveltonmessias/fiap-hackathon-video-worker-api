package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.MongoTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = [
		"management.server.port=-1",
		"app.storage.minio.initialize-buckets=false",
	],
)
@Import(MongoTestcontainersConfiguration::class)
class MongoProcessingJobRepositoryIntegrationTest {
	@Autowired
	private lateinit var processingJobRepository: ProcessingJobRepository

	@Autowired
	private lateinit var springDataRepository: SpringDataProcessingJobRepository

	@BeforeEach
	fun cleanDatabase() {
		springDataRepository.deleteAll()
	}

	@Test
	fun `saves and restores a completed job without data loss`() {
		val job = completedJob()

		val saved = processingJobRepository.save(job)
		val byVideo = processingJobRepository.findByVideoId(job.videoId)
		val byEvent = processingJobRepository.findByRequestEventId(job.requestEventId)

		assertJobEquals(job, saved)
		assertJobEquals(job, requireNotNull(byVideo))
		assertJobEquals(job, requireNotNull(byEvent))
	}

	@Test
	fun `returns null when job does not exist`() {
		assertNull(processingJobRepository.findByVideoId(UUID.randomUUID()))
		assertNull(processingJobRepository.findByRequestEventId(UUID.randomUUID()))
	}

	@Test
	fun `rejects a second job for the same video`() {
		val first = newJob()
		processingJobRepository.save(first)
		val duplicate = newJob(id = UUID.randomUUID(), requestEventId = UUID.randomUUID())

		assertFailsWith<DuplicateKeyException> {
			processingJobRepository.save(duplicate)
		}
	}

	@Test
	fun `rejects a repeated request event for another video`() {
		val first = newJob()
		processingJobRepository.save(first)
		val duplicate = newJob(id = UUID.randomUUID(), videoId = UUID.randomUUID())

		assertFailsWith<DuplicateKeyException> {
			processingJobRepository.save(duplicate)
		}
	}

	private fun completedJob(): VideoProcessingJob = newJob().also {
		it.start(RECEIVED_AT.plusSeconds(1))
		it.markGeneratingFrames(RECEIVED_AT.plusSeconds(2))
		it.markCompressing(24, RECEIVED_AT.plusSeconds(3))
		it.markUploadingResult(RECEIVED_AT.plusSeconds(4))
		it.complete(ObjectKey.of("customers/$CUSTOMER_ID/videos/$VIDEO_ID/output/frames.zip"), RECEIVED_AT.plusSeconds(5))
	}

	private fun newJob(
		id: UUID = JOB_ID,
		requestEventId: UUID = REQUEST_EVENT_ID,
		videoId: UUID = VIDEO_ID,
	): VideoProcessingJob = VideoProcessingJob.receive(
		id = id,
		requestEventId = requestEventId,
		videoId = videoId,
		customerId = CUSTOMER_ID,
		originalFilename = OriginalFilename.of("lesson.mp4"),
		inputObjectKey = ObjectKey.of("customers/$CUSTOMER_ID/videos/$videoId/input/lesson.mp4"),
		receivedAt = RECEIVED_AT,
	)

	private fun assertJobEquals(expected: VideoProcessingJob, actual: VideoProcessingJob) {
		assertEquals(expected.id, actual.id)
		assertEquals(expected.requestEventId, actual.requestEventId)
		assertEquals(expected.videoId, actual.videoId)
		assertEquals(expected.customerId, actual.customerId)
		assertEquals(expected.originalFilename, actual.originalFilename)
		assertEquals(expected.inputObjectKey, actual.inputObjectKey)
		assertEquals(expected.status, actual.status)
		assertEquals(expected.outputObjectKey, actual.outputObjectKey)
		assertEquals(expected.failureReason, actual.failureReason)
		assertEquals(expected.frameCount, actual.frameCount)
		assertEquals(expected.attempts, actual.attempts)
		assertEquals(expected.createdAt, actual.createdAt)
		assertEquals(expected.startedAt, actual.startedAt)
		assertEquals(expected.finishedAt, actual.finishedAt)
		assertEquals(expected.updatedAt, actual.updatedAt)
		assertEquals(ProcessingJobStatus.COMPLETED, actual.status)
	}

	private companion object {
		val RECEIVED_AT: Instant = Instant.parse("2026-01-01T10:00:00Z")
		val JOB_ID: UUID = UUID.fromString("603620cb-d9de-4b1c-9854-6f14ac3d5153")
		val REQUEST_EVENT_ID: UUID = UUID.fromString("86467e08-c764-413f-9857-75314e3fe607")
		val VIDEO_ID: UUID = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f")
		val CUSTOMER_ID: UUID = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc")
	}
}
