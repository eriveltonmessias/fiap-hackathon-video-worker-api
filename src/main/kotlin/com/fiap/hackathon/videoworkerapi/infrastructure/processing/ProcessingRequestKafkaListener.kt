package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.HandleVideoProcessingRequest
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingRequestResult
import com.fiap.hackathon.videoworkerapi.application.processing.VideoProcessor
import com.fiap.hackathon.videoworkerapi.application.processing.VideoProcessingRequest
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ProcessingRequestKafkaListener(
	private val objectMapper: ObjectMapper,
	private val handler: HandleVideoProcessingRequest,
	private val videoProcessor: VideoProcessor,
) {
	@KafkaListener(topics = [VideoProcessingRequested.TOPIC])
	fun consume(record: ConsumerRecord<String, String>) {
		val request = parse(record)
		if (handler.handle(request) != ProcessingRequestResult.ALREADY_REGISTERED) {
			videoProcessor.process(request.videoId)
		}
	}

	private fun parse(record: ConsumerRecord<String, String>): VideoProcessingRequest = try {
		val node = objectMapper.readTree(record.value())
		require(node.isObject) { "payload must be a JSON object" }
		require(node.path("eventType").stringValue() == VideoProcessingRequested.EVENT_TYPE) {
			"invalid eventType"
		}
		val event = objectMapper.treeToValue(node, VideoProcessingRequested::class.java)
		require(record.key() == event.videoId.toString()) { "Kafka key must match videoId" }
		VideoProcessingRequest(
			eventId = event.eventId,
			occurredAt = event.occurredAt,
			videoId = event.videoId,
			customerId = event.customerId,
			originalFilename = OriginalFilename.of(event.originalFilename),
			inputObjectKey = ObjectKey.of(event.inputObjectKey),
		)
	} catch (exception: Exception) {
		throw InvalidProcessingRequestException()
	}
}
