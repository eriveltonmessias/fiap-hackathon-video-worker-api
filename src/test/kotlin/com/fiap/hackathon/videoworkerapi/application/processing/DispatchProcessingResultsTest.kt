package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DispatchProcessingResultsTest {
	private val publishedAt = FINISHED_AT.plusSeconds(10)

	@Test
	fun `marks result published only after publisher confirmation`() {
		val repository = OutboxTestRepository(::completedJob)
		val publishedEventIds = mutableListOf<UUID>()
		val dispatcher = dispatcher(repository) { job ->
			publishedEventIds.add(assertNotNull(job.resultOutbox).eventId)
		}

		val summary = dispatcher.dispatch()

		assertEquals(ProcessingResultDispatchSummary(1, 0), summary)
		assertEquals(listOf(RESULT_EVENT_ID), publishedEventIds)
		assertEquals(publishedAt, repository.savedJob?.resultOutbox?.publishedAt)
	}

	@Test
	fun `keeps result pending when publisher fails`() {
		val repository = OutboxTestRepository(::completedJob)
		val dispatcher = dispatcher(repository) { throw ProcessingResultPublicationException() }

		val summary = dispatcher.dispatch()

		assertEquals(ProcessingResultDispatchSummary(0, 1), summary)
		assertTrue(completedJob().resultOutbox?.isPending == true)
		assertEquals(0, repository.saveCalls)
	}

	@Test
	fun `repeats same event when publication succeeded but confirmation save failed`() {
		val repository = OutboxTestRepository(::completedJob, failNextSave = true)
		val publishedEventIds = mutableListOf<UUID>()
		val dispatcher = dispatcher(repository) { job ->
			publishedEventIds.add(assertNotNull(job.resultOutbox).eventId)
		}

		assertFailsWith<OutboxConfirmationException> { dispatcher.dispatch() }
		val summary = dispatcher.dispatch()

		assertEquals(ProcessingResultDispatchSummary(1, 0), summary)
		assertEquals(listOf(RESULT_EVENT_ID, RESULT_EVENT_ID), publishedEventIds)
		assertEquals(publishedAt, repository.savedJob?.resultOutbox?.publishedAt)
	}

	private fun dispatcher(
		repository: ProcessingJobRepository,
		publisher: ProcessingResultPublisher,
	): DispatchProcessingResults = DispatchProcessingResults(
		repository = repository,
		publisher = publisher,
		clock = Clock.fixed(publishedAt, ZoneOffset.UTC),
		batchSize = 10,
	)

	private fun completedJob(): VideoProcessingJob = VideoProcessingJob.receive(
		id = JOB_ID,
		requestEventId = REQUEST_EVENT_ID,
		videoId = VIDEO_ID,
		customerId = CUSTOMER_ID,
		originalFilename = OriginalFilename.of("lesson.mp4"),
		inputObjectKey = ObjectKey.of("customers/$CUSTOMER_ID/videos/$VIDEO_ID/input/lesson.mp4"),
		receivedAt = RECEIVED_AT,
	).also {
		it.start(RECEIVED_AT.plusSeconds(1))
		it.markGeneratingFrames(RECEIVED_AT.plusSeconds(2))
		it.markCompressing(2, RECEIVED_AT.plusSeconds(3))
		it.markUploadingResult(RECEIVED_AT.plusSeconds(4))
		it.complete(
			ObjectKey.of("customers/$CUSTOMER_ID/videos/$VIDEO_ID/output/frames.zip"),
			RESULT_EVENT_ID,
			FINISHED_AT,
		)
	}

	private companion object {
		val RECEIVED_AT: Instant = Instant.parse("2026-01-01T10:00:00Z")
		val FINISHED_AT: Instant = RECEIVED_AT.plusSeconds(5)
		val JOB_ID: UUID = UUID.fromString("603620cb-d9de-4b1c-9854-6f14ac3d5153")
		val REQUEST_EVENT_ID: UUID = UUID.fromString("86467e08-c764-413f-9857-75314e3fe607")
		val VIDEO_ID: UUID = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f")
		val CUSTOMER_ID: UUID = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc")
		val RESULT_EVENT_ID: UUID = UUID.fromString("ef2ec18e-9c74-4613-bf03-0a8ab1a762f1")
	}
}

private class OutboxTestRepository(
	private val pendingJob: () -> VideoProcessingJob,
	private var failNextSave: Boolean = false,
) : ProcessingJobRepository {
	var savedJob: VideoProcessingJob? = null
	var saveCalls: Int = 0

	override fun save(job: VideoProcessingJob): VideoProcessingJob {
		saveCalls += 1
		if (failNextSave) {
			failNextSave = false
			throw OutboxConfirmationException()
		}
		savedJob = job
		return job
	}

	override fun saveIfAbsent(job: VideoProcessingJob): Boolean = error("Not used")

	override fun findByVideoId(videoId: UUID): VideoProcessingJob? = null

	override fun findByRequestEventId(requestEventId: UUID): VideoProcessingJob? = null

	override fun findPendingResults(limit: Int): List<VideoProcessingJob> =
		if (savedJob?.resultOutbox?.isPending == false) emptyList() else listOf(pendingJob()).take(limit)
}

private class OutboxConfirmationException : RuntimeException()
