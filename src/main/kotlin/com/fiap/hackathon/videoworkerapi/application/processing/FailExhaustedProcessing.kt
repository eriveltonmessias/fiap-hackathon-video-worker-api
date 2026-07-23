package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.FailureReason
import java.time.Clock
import java.time.Instant
import java.util.UUID

class FailExhaustedProcessing(
	private val repository: ProcessingJobRepository,
	private val resultEventIdGenerator: ProcessingResultEventIdGenerator,
	private val clock: Clock,
) {
	fun fail(videoId: UUID) {
		val job = repository.findByVideoId(videoId) ?: return
		if (job.isTerminal()) return
		val changedAt = maxOf(Instant.now(clock), job.updatedAt)
		job.fail(
			FailureReason.of("Processing retries exhausted"),
			resultEventIdGenerator.generate(),
			changedAt,
		)
		repository.save(job)
	}
}
