package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import java.time.Instant
import java.util.UUID

data class VideoProcessingRequested(
	val eventId: UUID,
	val eventType: String,
	val occurredAt: Instant,
	val videoId: UUID,
	val customerId: UUID,
	val originalFilename: String,
	val inputObjectKey: String,
) {
	companion object {
		const val EVENT_TYPE = "VideoProcessingRequested"
		const val TOPIC = "video.processing.requested"
	}
}

class InvalidProcessingRequestException : RuntimeException("Invalid video processing request")
