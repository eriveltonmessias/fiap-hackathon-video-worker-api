package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import java.time.Clock
import java.time.Instant

fun interface ProcessingResultPublisher {
	fun publish(job: VideoProcessingJob)
}

class ProcessingResultPublicationException : RuntimeException("Processing result publication failed")

data class ProcessingResultDispatchSummary(
	val published: Int,
	val failed: Int,
)

class DispatchProcessingResults(
	private val repository: ProcessingJobRepository,
	private val publisher: ProcessingResultPublisher,
	private val clock: Clock,
	private val batchSize: Int,
) {
	init {
		require(batchSize > 0) { "batchSize must be greater than zero" }
	}

	fun dispatch(): ProcessingResultDispatchSummary {
		var published = 0
		var failed = 0
		repository.findPendingResults(batchSize).forEach { job ->
			try {
				publisher.publish(job)
				job.markResultPublished(maxOf(Instant.now(clock), checkNotNull(job.finishedAt)))
				repository.save(job)
				published += 1
			} catch (exception: ProcessingResultPublicationException) {
				failed += 1
			}
		}
		return ProcessingResultDispatchSummary(published, failed)
	}
}
