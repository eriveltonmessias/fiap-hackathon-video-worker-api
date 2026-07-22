package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingResultPublicationException
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingResultPublisher
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

@Component
class KafkaProcessingResultPublisher(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val objectMapper: ObjectMapper,
	private val properties: ProcessingResultOutboxProperties,
) : ProcessingResultPublisher {
	override fun publish(job: VideoProcessingJob) {
		val outbox = checkNotNull(job.resultOutbox) { "Job must have a result outbox" }
		check(outbox.isPending) { "Processing result was already published" }
		val (topic, payload) = when (job.status) {
			ProcessingJobStatus.COMPLETED -> VideoProcessed.TOPIC to VideoProcessed(
				eventId = outbox.eventId,
				eventType = VideoProcessed.EVENT_TYPE,
				occurredAt = outbox.occurredAt,
				videoId = job.videoId,
				outputObjectKey = checkNotNull(job.outputObjectKey).value,
			)

			ProcessingJobStatus.FAILED -> VideoProcessingFailed.TOPIC to VideoProcessingFailed(
				eventId = outbox.eventId,
				eventType = VideoProcessingFailed.EVENT_TYPE,
				occurredAt = outbox.occurredAt,
				videoId = job.videoId,
				failureReason = checkNotNull(job.failureReason).value,
			)

			else -> error("Only terminal jobs can publish processing results")
		}

		try {
			kafkaTemplate.send(topic, job.videoId.toString(), objectMapper.writeValueAsString(payload))
				.get(properties.publishTimeout.toMillis(), TimeUnit.MILLISECONDS)
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw ProcessingResultPublicationException()
		} catch (exception: Exception) {
			throw ProcessingResultPublicationException()
		}
	}
}
