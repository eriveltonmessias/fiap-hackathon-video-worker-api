package com.fiap.hackathon.videoworkerapi.domain.processing

import java.time.Instant
import java.util.UUID

class VideoProcessingJob private constructor(
	val id: UUID,
	val requestEventId: UUID,
	val videoId: UUID,
	val customerId: UUID,
	val originalFilename: OriginalFilename,
	val inputObjectKey: ObjectKey,
	status: ProcessingJobStatus,
	outputObjectKey: ObjectKey?,
	failureReason: FailureReason?,
	resultOutbox: ProcessingResultOutbox?,
	frameCount: Int?,
	attempts: Int,
	val createdAt: Instant,
	startedAt: Instant?,
	finishedAt: Instant?,
	updatedAt: Instant,
) {
	var status: ProcessingJobStatus = status
		private set

	var outputObjectKey: ObjectKey? = outputObjectKey
		private set

	var failureReason: FailureReason? = failureReason
		private set

	var resultOutbox: ProcessingResultOutbox? = resultOutbox
		private set

	var frameCount: Int? = frameCount
		private set

	var attempts: Int = attempts
		private set

	var startedAt: Instant? = startedAt
		private set

	var finishedAt: Instant? = finishedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		validateSnapshot()
	}

	fun start(changedAt: Instant) {
		ensureStatus(ProcessingJobStatus.RECEIVED)
		ensureChangedAt(changedAt)
		attempts += 1
		startedAt = changedAt
		status = ProcessingJobStatus.PROCESSING
		updatedAt = changedAt
	}

	fun retry(changedAt: Instant) {
		check(status != ProcessingJobStatus.RECEIVED && !isTerminal()) {
			"Job in status $status cannot be retried"
		}
		ensureChangedAt(changedAt)
		attempts += 1
		frameCount = null
		status = ProcessingJobStatus.PROCESSING
		updatedAt = changedAt
	}

	fun markGeneratingFrames(changedAt: Instant) {
		transition(ProcessingJobStatus.PROCESSING, ProcessingJobStatus.GENERATING_FRAMES, changedAt)
	}

	fun markCompressing(extractedFrameCount: Int, changedAt: Instant) {
		ensureStatus(ProcessingJobStatus.GENERATING_FRAMES)
		require(extractedFrameCount > 0) { "frameCount must be greater than zero" }
		ensureChangedAt(changedAt)
		frameCount = extractedFrameCount
		status = ProcessingJobStatus.COMPRESSING
		updatedAt = changedAt
	}

	fun markUploadingResult(changedAt: Instant) {
		transition(ProcessingJobStatus.COMPRESSING, ProcessingJobStatus.UPLOADING_RESULT, changedAt)
	}

	fun complete(objectKey: ObjectKey, resultEventId: UUID, changedAt: Instant) {
		ensureStatus(ProcessingJobStatus.UPLOADING_RESULT)
		ensureChangedAt(changedAt)
		outputObjectKey = objectKey
		status = ProcessingJobStatus.COMPLETED
		finishedAt = changedAt
		updatedAt = changedAt
		resultOutbox = ProcessingResultOutbox.pending(resultEventId, changedAt)
	}

	fun fail(reason: FailureReason, resultEventId: UUID, changedAt: Instant) {
		check(!isTerminal()) { "Job in status $status cannot transition to FAILED" }
		ensureChangedAt(changedAt)
		failureReason = reason
		status = ProcessingJobStatus.FAILED
		finishedAt = changedAt
		updatedAt = changedAt
		resultOutbox = ProcessingResultOutbox.pending(resultEventId, changedAt)
	}

	fun markResultPublished(publishedAt: Instant) {
		check(isTerminal()) { "Only a terminal job can publish a result" }
		resultOutbox = checkNotNull(resultOutbox).markPublished(publishedAt)
	}

	fun isTerminal(): Boolean = status == ProcessingJobStatus.COMPLETED || status == ProcessingJobStatus.FAILED

	private fun transition(
		expected: ProcessingJobStatus,
		next: ProcessingJobStatus,
		changedAt: Instant,
	) {
		ensureStatus(expected)
		ensureChangedAt(changedAt)
		status = next
		updatedAt = changedAt
	}

	private fun ensureStatus(expected: ProcessingJobStatus) {
		check(status == expected) { "Job in status $status cannot transition from expected status $expected" }
	}

	private fun ensureChangedAt(changedAt: Instant) {
		require(!changedAt.isBefore(updatedAt)) { "changedAt must not be before updatedAt" }
	}

	private fun validateSnapshot() {
		require(!updatedAt.isBefore(createdAt)) { "updatedAt must not be before createdAt" }
		require(attempts >= 0) { "attempts must not be negative" }
		frameCount?.let { require(it > 0) { "frameCount must be greater than zero" } }
		require(startedAt == null || !startedAt!!.isBefore(createdAt)) { "startedAt must not be before createdAt" }
		require(startedAt == null || !startedAt!!.isAfter(updatedAt)) { "startedAt must not be after updatedAt" }
		require(finishedAt == null || !finishedAt!!.isBefore(startedAt ?: createdAt)) {
			"finishedAt must not be before the job start"
		}
		require(finishedAt == null || finishedAt == updatedAt) { "finishedAt must match updatedAt" }
		require((attempts == 0) == (startedAt == null)) { "attempts and startedAt must be consistent" }
		require(frameCount == null || startedAt != null) { "frameCount requires a started job" }
		require(resultOutbox == null || isTerminal()) { "Only a terminal job can have a result outbox" }
		require(resultOutbox == null || resultOutbox!!.occurredAt == finishedAt) {
			"Result occurredAt must match finishedAt"
		}

		when (status) {
			ProcessingJobStatus.RECEIVED -> {
				require(attempts == 0) { "RECEIVED job must not have attempts" }
				requireNoResult()
			}

			ProcessingJobStatus.PROCESSING,
			ProcessingJobStatus.GENERATING_FRAMES,
			-> {
				requireStarted()
				require(frameCount == null) { "$status job must not have a frame count" }
				requireNoResult()
			}

			ProcessingJobStatus.COMPRESSING,
			ProcessingJobStatus.UPLOADING_RESULT,
			-> {
				requireStarted()
				require(frameCount != null) { "$status job must have a frame count" }
				requireNoResult()
			}

			ProcessingJobStatus.COMPLETED -> {
				requireStarted()
				require(frameCount != null) { "COMPLETED job must have a frame count" }
				require(outputObjectKey != null) { "COMPLETED job must have an output object key" }
				require(failureReason == null) { "COMPLETED job must not have a failure reason" }
				require(finishedAt != null) { "COMPLETED job must have finishedAt" }
				require(resultOutbox != null) { "COMPLETED job must have a result outbox" }
			}

			ProcessingJobStatus.FAILED -> {
				require(outputObjectKey == null) { "FAILED job must not have an output object key" }
				require(failureReason != null) { "FAILED job must have a failure reason" }
				require(finishedAt != null) { "FAILED job must have finishedAt" }
				require(resultOutbox != null) { "FAILED job must have a result outbox" }
			}
		}
	}

	private fun requireStarted() {
		require(attempts > 0 && startedAt != null) { "$status job must have been started" }
	}

	private fun requireNoResult() {
		require(outputObjectKey == null) { "$status job must not have an output object key" }
		require(failureReason == null) { "$status job must not have a failure reason" }
		require(finishedAt == null) { "$status job must not have finishedAt" }
	}

	companion object {
		fun receive(
			id: UUID,
			requestEventId: UUID,
			videoId: UUID,
			customerId: UUID,
			originalFilename: OriginalFilename,
			inputObjectKey: ObjectKey,
			receivedAt: Instant,
		): VideoProcessingJob = VideoProcessingJob(
			id = id,
			requestEventId = requestEventId,
			videoId = videoId,
			customerId = customerId,
			originalFilename = originalFilename,
			inputObjectKey = inputObjectKey,
			status = ProcessingJobStatus.RECEIVED,
			outputObjectKey = null,
			failureReason = null,
			resultOutbox = null,
			frameCount = null,
			attempts = 0,
			createdAt = receivedAt,
			startedAt = null,
			finishedAt = null,
			updatedAt = receivedAt,
		)

		@Suppress("LongParameterList")
		fun restore(
			id: UUID,
			requestEventId: UUID,
			videoId: UUID,
			customerId: UUID,
			originalFilename: OriginalFilename,
			inputObjectKey: ObjectKey,
			status: ProcessingJobStatus,
			outputObjectKey: ObjectKey?,
			failureReason: FailureReason?,
			resultOutbox: ProcessingResultOutbox?,
			frameCount: Int?,
			attempts: Int,
			createdAt: Instant,
			startedAt: Instant?,
			finishedAt: Instant?,
			updatedAt: Instant,
		): VideoProcessingJob = VideoProcessingJob(
			id = id,
			requestEventId = requestEventId,
			videoId = videoId,
			customerId = customerId,
			originalFilename = originalFilename,
			inputObjectKey = inputObjectKey,
			status = status,
			outputObjectKey = outputObjectKey,
			failureReason = failureReason,
			resultOutbox = resultOutbox,
			frameCount = frameCount,
			attempts = attempts,
			createdAt = createdAt,
			startedAt = startedAt,
			finishedAt = finishedAt,
			updatedAt = updatedAt,
		)
	}
}
