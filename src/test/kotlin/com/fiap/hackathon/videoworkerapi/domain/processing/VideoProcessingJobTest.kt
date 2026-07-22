package com.fiap.hackathon.videoworkerapi.domain.processing

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoProcessingJobTest {
	private val receivedAt = Instant.parse("2026-01-01T10:00:00Z")
	private val jobId = UUID.fromString("603620cb-d9de-4b1c-9854-6f14ac3d5153")
	private val requestEventId = UUID.fromString("86467e08-c764-413f-9857-75314e3fe607")
	private val videoId = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f")
	private val customerId = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc")
	private val resultEventId = UUID.fromString("ef2ec18e-9c74-4613-bf03-0a8ab1a762f1")

	@Test
	fun `receives a processing job`() {
		val job = newJob()

		assertEquals(jobId, job.id)
		assertEquals(requestEventId, job.requestEventId)
		assertEquals(videoId, job.videoId)
		assertEquals(customerId, job.customerId)
		assertEquals("lesson.mp4", job.originalFilename.value)
		assertEquals("customers/$customerId/videos/$videoId/input/lesson.mp4", job.inputObjectKey.value)
		assertEquals(ProcessingJobStatus.RECEIVED, job.status)
		assertEquals(0, job.attempts)
		assertNull(job.startedAt)
		assertNull(job.finishedAt)
		assertNull(job.frameCount)
		assertNull(job.outputObjectKey)
		assertNull(job.failureReason)
		assertEquals(receivedAt, job.createdAt)
		assertEquals(receivedAt, job.updatedAt)
		assertFalse(job.isTerminal())
	}

	@Test
	fun `completes the processing lifecycle`() {
		val job = newJob()

		job.start(receivedAt.plusSeconds(1))
		assertEquals(ProcessingJobStatus.PROCESSING, job.status)
		assertEquals(1, job.attempts)
		assertEquals(receivedAt.plusSeconds(1), job.startedAt)

		job.markGeneratingFrames(receivedAt.plusSeconds(2))
		assertEquals(ProcessingJobStatus.GENERATING_FRAMES, job.status)

		job.markCompressing(12, receivedAt.plusSeconds(3))
		assertEquals(ProcessingJobStatus.COMPRESSING, job.status)
		assertEquals(12, job.frameCount)

		job.markUploadingResult(receivedAt.plusSeconds(4))
		assertEquals(ProcessingJobStatus.UPLOADING_RESULT, job.status)

		val outputKey = ObjectKey.of("customers/$customerId/videos/$videoId/output/frames.zip")
		job.complete(outputKey, resultEventId, receivedAt.plusSeconds(5))

		assertEquals(ProcessingJobStatus.COMPLETED, job.status)
		assertEquals(outputKey, job.outputObjectKey)
		assertEquals(receivedAt.plusSeconds(5), job.finishedAt)
		assertEquals(receivedAt.plusSeconds(5), job.updatedAt)
		assertEquals(resultEventId, job.resultOutbox?.eventId)
		assertTrue(requireNotNull(job.resultOutbox).isPending)
		assertTrue(job.isTerminal())
	}

	@Test
	fun `fails an active job preserving safe reason and finish time`() {
		val job = newJob()
		job.start(receivedAt.plusSeconds(1))
		job.markGeneratingFrames(receivedAt.plusSeconds(2))
		val reason = FailureReason.of(" Decoder unavailable ")

		job.fail(reason, resultEventId, receivedAt.plusSeconds(3))

		assertEquals(ProcessingJobStatus.FAILED, job.status)
		assertEquals("Decoder unavailable", job.failureReason?.value)
		assertEquals(receivedAt.plusSeconds(3), job.finishedAt)
		assertEquals(receivedAt.plusSeconds(3), job.updatedAt)
		assertTrue(job.isTerminal())
	}

	@Test
	fun `allows failure before processing starts`() {
		val job = newJob()

		job.fail(FailureReason.of("Invalid processing request"), resultEventId, receivedAt.plusSeconds(1))

		assertEquals(ProcessingJobStatus.FAILED, job.status)
		assertEquals(0, job.attempts)
		assertNull(job.startedAt)
		assertEquals(receivedAt.plusSeconds(1), job.finishedAt)
	}

	@Test
	fun `rejects invalid transition without changing the job`() {
		val job = newJob()

		assertFailsWith<IllegalStateException> {
			job.markGeneratingFrames(receivedAt.plusSeconds(1))
		}

		assertEquals(ProcessingJobStatus.RECEIVED, job.status)
		assertEquals(receivedAt, job.updatedAt)
		assertEquals(0, job.attempts)
	}

	@Test
	fun `rejects empty frame extraction without partially changing the job`() {
		val job = newJob()
		job.start(receivedAt.plusSeconds(1))
		job.markGeneratingFrames(receivedAt.plusSeconds(2))

		assertFailsWith<IllegalArgumentException> {
			job.markCompressing(0, receivedAt.plusSeconds(3))
		}

		assertEquals(ProcessingJobStatus.GENERATING_FRAMES, job.status)
		assertNull(job.frameCount)
		assertEquals(receivedAt.plusSeconds(2), job.updatedAt)
	}

	@Test
	fun `rejects older transition time without partially changing the job`() {
		val job = newJob()

		assertFailsWith<IllegalArgumentException> {
			job.start(receivedAt.minusSeconds(1))
		}

		assertEquals(ProcessingJobStatus.RECEIVED, job.status)
		assertEquals(0, job.attempts)
		assertNull(job.startedAt)
	}

	@Test
	fun `terminal jobs reject further transitions`() {
		val completed = completedJob()
		val failed = newJob().also {
			it.fail(FailureReason.of("Permanent failure"), resultEventId, receivedAt.plusSeconds(1))
		}

		assertFailsWith<IllegalStateException> {
			completed.fail(FailureReason.of("Late failure"), resultEventId, receivedAt.plusSeconds(6))
		}
		assertFailsWith<IllegalStateException> {
			failed.start(receivedAt.plusSeconds(2))
		}

		assertEquals(ProcessingJobStatus.COMPLETED, completed.status)
		assertEquals(ProcessingJobStatus.FAILED, failed.status)
	}

	@Test
	fun `restores a consistent completed snapshot`() {
		val outputKey = ObjectKey.of("customers/$customerId/videos/$videoId/output/frames.zip")

		val job = VideoProcessingJob.restore(
			id = jobId,
			requestEventId = requestEventId,
			videoId = videoId,
			customerId = customerId,
			originalFilename = OriginalFilename.of("lesson.mp4"),
			inputObjectKey = inputKey(),
			status = ProcessingJobStatus.COMPLETED,
			outputObjectKey = outputKey,
			failureReason = null,
			resultOutbox = ProcessingResultOutbox.pending(resultEventId, receivedAt.plusSeconds(5)),
			frameCount = 12,
			attempts = 1,
			createdAt = receivedAt,
			startedAt = receivedAt.plusSeconds(1),
			finishedAt = receivedAt.plusSeconds(5),
			updatedAt = receivedAt.plusSeconds(5),
		)

		assertEquals(ProcessingJobStatus.COMPLETED, job.status)
		assertEquals(outputKey, job.outputObjectKey)
		assertTrue(job.isTerminal())
	}

	@Test
	fun `rejects inconsistent restored snapshot`() {
		assertFailsWith<IllegalArgumentException> {
			VideoProcessingJob.restore(
				id = jobId,
				requestEventId = requestEventId,
				videoId = videoId,
				customerId = customerId,
				originalFilename = OriginalFilename.of("lesson.mp4"),
				inputObjectKey = inputKey(),
				status = ProcessingJobStatus.COMPLETED,
				outputObjectKey = null,
				failureReason = null,
				resultOutbox = ProcessingResultOutbox.pending(resultEventId, receivedAt.plusSeconds(5)),
				frameCount = 12,
				attempts = 1,
				createdAt = receivedAt,
				startedAt = receivedAt.plusSeconds(1),
				finishedAt = receivedAt.plusSeconds(5),
				updatedAt = receivedAt.plusSeconds(5),
			)
		}
	}

	@Test
	fun `rejects frames in a job that never started`() {
		assertFailsWith<IllegalArgumentException> {
			VideoProcessingJob.restore(
				id = jobId,
				requestEventId = requestEventId,
				videoId = videoId,
				customerId = customerId,
				originalFilename = OriginalFilename.of("lesson.mp4"),
				inputObjectKey = inputKey(),
				status = ProcessingJobStatus.FAILED,
				outputObjectKey = null,
				failureReason = FailureReason.of("Invalid request"),
				resultOutbox = ProcessingResultOutbox.pending(resultEventId, receivedAt.plusSeconds(1)),
				frameCount = 12,
				attempts = 0,
				createdAt = receivedAt,
				startedAt = null,
				finishedAt = receivedAt.plusSeconds(1),
				updatedAt = receivedAt.plusSeconds(1),
			)
		}
	}

	private fun completedJob(): VideoProcessingJob = newJob().also {
		it.start(receivedAt.plusSeconds(1))
		it.markGeneratingFrames(receivedAt.plusSeconds(2))
		it.markCompressing(12, receivedAt.plusSeconds(3))
		it.markUploadingResult(receivedAt.plusSeconds(4))
		it.complete(
			ObjectKey.of("customers/$customerId/videos/$videoId/output/frames.zip"),
			resultEventId,
			receivedAt.plusSeconds(5),
		)
	}

	private fun newJob(): VideoProcessingJob = VideoProcessingJob.receive(
		id = jobId,
		requestEventId = requestEventId,
		videoId = videoId,
		customerId = customerId,
		originalFilename = OriginalFilename.of(" lesson.mp4 "),
		inputObjectKey = inputKey(),
		receivedAt = receivedAt,
	)

	private fun inputKey(): ObjectKey =
		ObjectKey.of("customers/$customerId/videos/$videoId/input/lesson.mp4")
}
