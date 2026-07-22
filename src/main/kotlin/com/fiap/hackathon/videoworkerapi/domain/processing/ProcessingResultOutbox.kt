package com.fiap.hackathon.videoworkerapi.domain.processing

import java.time.Instant
import java.util.UUID

@ConsistentCopyVisibility
data class ProcessingResultOutbox private constructor(
	val eventId: UUID,
	val occurredAt: Instant,
	val publishedAt: Instant?,
) {
	val isPending: Boolean
		get() = publishedAt == null

	fun markPublished(at: Instant): ProcessingResultOutbox {
		check(isPending) { "Processing result was already published" }
		require(!at.isBefore(occurredAt)) { "publishedAt must not be before occurredAt" }
		return copy(publishedAt = at)
	}

	companion object {
		fun pending(eventId: UUID, occurredAt: Instant): ProcessingResultOutbox =
			ProcessingResultOutbox(eventId, occurredAt, null)

		fun restore(eventId: UUID, occurredAt: Instant, publishedAt: Instant?): ProcessingResultOutbox {
			require(publishedAt == null || !publishedAt.isBefore(occurredAt)) {
				"publishedAt must not be before occurredAt"
			}
			return ProcessingResultOutbox(eventId, occurredAt, publishedAt)
		}
	}
}
