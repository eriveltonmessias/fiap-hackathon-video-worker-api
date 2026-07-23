package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class DeadLetterFailureType {
	INVALID_REQUEST,
	RETRIES_EXHAUSTED,
}

data class VideoProcessingRequestDeadLettered(
	val eventId: UUID,
	val eventType: String,
	val occurredAt: Instant,
	val sourceTopic: String,
	val sourcePartition: Int,
	val sourceOffset: Long,
	val videoId: UUID?,
	val failureType: DeadLetterFailureType,
) {
	companion object {
		const val EVENT_TYPE = "VideoProcessingRequestDeadLettered"
		const val TOPIC = "video.processing.requested.dlq"
	}
}

class DeadLetterPublicationException : RuntimeException("Dead letter publication failed")

@Component
class ProcessingRequestDeadLetterPublisher(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
	private val properties: ProcessingRetryProperties,
) {
	fun publish(
		record: ConsumerRecord<*, *>,
		videoId: UUID?,
		failureType: DeadLetterFailureType,
	) {
		val event = VideoProcessingRequestDeadLettered(
			eventId = deterministicEventId(record),
			eventType = VideoProcessingRequestDeadLettered.EVENT_TYPE,
			occurredAt = record.timestamp().takeIf { it >= 0 }
				?.let(Instant::ofEpochMilli)
				?: Instant.EPOCH,
			sourceTopic = record.topic(),
			sourcePartition = record.partition(),
			sourceOffset = record.offset(),
			videoId = videoId,
			failureType = failureType,
		)
		try {
			val key = videoId?.toString() ?: "${record.topic()}:${record.partition()}:${record.offset()}"
			kafkaTemplate.send(
				VideoProcessingRequestDeadLettered.TOPIC,
				key,
				objectMapper.writeValueAsString(event),
			).get(properties.dlqPublishTimeout.toMillis(), TimeUnit.MILLISECONDS)
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw DeadLetterPublicationException()
		} catch (exception: Exception) {
			throw DeadLetterPublicationException()
		}
	}

	private fun deterministicEventId(record: ConsumerRecord<*, *>): UUID {
		val sourceId = "${record.topic()}:${record.partition()}:${record.offset()}"
		return UUID.nameUUIDFromBytes(sourceId.toByteArray(StandardCharsets.UTF_8))
	}
}
