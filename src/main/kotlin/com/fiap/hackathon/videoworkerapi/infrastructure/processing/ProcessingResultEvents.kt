package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import java.time.Instant
import java.util.UUID

data class VideoProcessed(
	val eventId: UUID,
	val eventType: String,
	val occurredAt: Instant,
	val videoId: UUID,
	val outputObjectKey: String,
) {
	companion object {
		const val EVENT_TYPE = "VideoProcessed"
		const val TOPIC = "video.processing.completed"
	}
}

data class VideoProcessingFailed(
	val eventId: UUID,
	val eventType: String,
	val occurredAt: Instant,
	val videoId: UUID,
	val failureReason: String,
) {
	companion object {
		const val EVENT_TYPE = "VideoProcessingFailed"
		const val TOPIC = "video.processing.failed"
	}
}
