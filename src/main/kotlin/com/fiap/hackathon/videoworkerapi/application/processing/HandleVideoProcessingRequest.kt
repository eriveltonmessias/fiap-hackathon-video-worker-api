package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import java.time.Instant
import java.util.UUID

data class VideoProcessingRequest(
	val eventId: UUID,
	val occurredAt: Instant,
	val videoId: UUID,
	val customerId: UUID,
	val originalFilename: OriginalFilename,
	val inputObjectKey: ObjectKey,
)

fun interface ProcessingJobIdGenerator {
	fun generate(): UUID
}

class HandleVideoProcessingRequest(
	private val repository: ProcessingJobRepository,
	private val idGenerator: ProcessingJobIdGenerator,
) {
	fun handle(request: VideoProcessingRequest): ProcessingRequestResult {
		if (repository.findByRequestEventId(request.eventId) != null) {
			return ProcessingRequestResult.ALREADY_REGISTERED
		}
		if (repository.findByVideoId(request.videoId) != null) {
			return ProcessingRequestResult.ALREADY_REGISTERED
		}

		val job = VideoProcessingJob.receive(
			id = idGenerator.generate(),
			requestEventId = request.eventId,
			videoId = request.videoId,
			customerId = request.customerId,
			originalFilename = request.originalFilename,
			inputObjectKey = request.inputObjectKey,
			receivedAt = request.occurredAt,
		)

		return if (repository.saveIfAbsent(job)) {
			ProcessingRequestResult.REGISTERED
		} else {
			ProcessingRequestResult.ALREADY_REGISTERED
		}
	}
}

enum class ProcessingRequestResult {
	REGISTERED,
	ALREADY_REGISTERED,
}
