package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.FailureReason
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingResultOutbox
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document("video_processing_jobs")
data class MongoProcessingJobDocument(
	@Id
	val id: UUID,
	@Indexed(unique = true)
	val requestEventId: UUID,
	@Indexed(unique = true)
	val videoId: UUID,
	val customerId: UUID,
	val originalFilename: String,
	val inputObjectKey: String,
	val status: ProcessingJobStatus,
	val outputObjectKey: String?,
	val failureReason: String?,
	val resultOutbox: MongoProcessingResultOutboxDocument?,
	val frameCount: Int?,
	val attempts: Int,
	val createdAt: Instant,
	val startedAt: Instant?,
	val finishedAt: Instant?,
	val updatedAt: Instant,
)

data class MongoProcessingResultOutboxDocument(
	val eventId: UUID,
	val occurredAt: Instant,
	val publishedAt: Instant?,
)

fun VideoProcessingJob.toDocument(): MongoProcessingJobDocument = MongoProcessingJobDocument(
	id = id,
	requestEventId = requestEventId,
	videoId = videoId,
	customerId = customerId,
	originalFilename = originalFilename.value,
	inputObjectKey = inputObjectKey.value,
	status = status,
	outputObjectKey = outputObjectKey?.value,
	failureReason = failureReason?.value,
	resultOutbox = resultOutbox?.let {
		MongoProcessingResultOutboxDocument(it.eventId, it.occurredAt, it.publishedAt)
	},
	frameCount = frameCount,
	attempts = attempts,
	createdAt = createdAt,
	startedAt = startedAt,
	finishedAt = finishedAt,
	updatedAt = updatedAt,
)

fun MongoProcessingJobDocument.toDomain(): VideoProcessingJob = VideoProcessingJob.restore(
	id = id,
	requestEventId = requestEventId,
	videoId = videoId,
	customerId = customerId,
	originalFilename = OriginalFilename.of(originalFilename),
	inputObjectKey = ObjectKey.of(inputObjectKey),
	status = status,
	outputObjectKey = outputObjectKey?.let(ObjectKey::of),
	failureReason = failureReason?.let(FailureReason::of),
	resultOutbox = resultOutbox?.let {
		ProcessingResultOutbox.restore(
			it.eventId,
			it.occurredAt,
			it.publishedAt,
		)
	},
	frameCount = frameCount,
	attempts = attempts,
	createdAt = createdAt,
	startedAt = startedAt,
	finishedAt = finishedAt,
	updatedAt = updatedAt,
)
