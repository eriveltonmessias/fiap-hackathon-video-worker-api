package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration(proxyBeanMethods = false)
class ProcessingRequestKafkaConfiguration {
	@Bean
	fun processingRequestErrorHandler(): CommonErrorHandler {
		val recoverer = { record: org.apache.kafka.clients.consumer.ConsumerRecord<*, *>, exception: Exception ->
			LOGGER.warn(
				"Discarding processing request topic={} partition={} offset={} errorType={}",
				record.topic(),
				record.partition(),
				record.offset(),
				exception.javaClass.simpleName,
			)
		}
		return DefaultErrorHandler(recoverer, FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS)).apply {
			addNotRetryableExceptions(InvalidProcessingRequestException::class.java)
		}
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(ProcessingRequestKafkaConfiguration::class.java)
	}
}
