package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.FailExhaustedProcessing
import jakarta.validation.constraints.Positive
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.util.UUID

@Validated
@ConfigurationProperties("app.kafka.retry")
data class ProcessingRetryProperties(
	@field:Positive val maxAttempts: Long = 3,
	val interval: Duration = Duration.ofSeconds(1),
	val dlqPublishTimeout: Duration = Duration.ofSeconds(10),
) {
	init {
		require(!interval.isNegative) { "Retry interval must not be negative" }
		require(!dlqPublishTimeout.isZero && !dlqPublishTimeout.isNegative) {
			"DLQ publish timeout must be positive"
		}
	}
}

@Configuration(proxyBeanMethods = false)
class ProcessingRequestKafkaConfiguration {
	@Bean
	fun processingRequestErrorHandler(
		deadLetterPublisher: ProcessingRequestDeadLetterPublisher,
		failExhaustedProcessing: FailExhaustedProcessing,
		properties: ProcessingRetryProperties,
	): CommonErrorHandler {
		val recoverer = { record: ConsumerRecord<*, *>, exception: Exception ->
			val videoId = record.key()?.toString()?.let { key ->
				runCatching { UUID.fromString(key) }.getOrNull()
			}
			val failureType = if (exception.hasCause<InvalidProcessingRequestException>()) {
				DeadLetterFailureType.INVALID_REQUEST
			} else {
				DeadLetterFailureType.RETRIES_EXHAUSTED
			}
			deadLetterPublisher.publish(record, videoId, failureType)
			if (failureType == DeadLetterFailureType.RETRIES_EXHAUSTED && videoId != null) {
				failExhaustedProcessing.fail(videoId)
			}
			LOGGER.warn(
				"Recovered processing request topic={} partition={} offset={} failureType={}",
				record.topic(),
				record.partition(),
				record.offset(),
				failureType,
			)
		}
		val retries = properties.maxAttempts - 1
		return DefaultErrorHandler(recoverer, FixedBackOff(properties.interval.toMillis(), retries)).apply {
			addNotRetryableExceptions(InvalidProcessingRequestException::class.java)
		}
	}

	private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
		var current: Throwable? = this
		while (current != null) {
			if (current is T) return true
			current = current.cause
		}
		return false
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(ProcessingRequestKafkaConfiguration::class.java)
	}
}
