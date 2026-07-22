package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.DispatchProcessingResults
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingResultPublisher
import jakarta.validation.constraints.Positive
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Clock
import java.time.Duration

@Validated
@ConfigurationProperties("app.outbox")
data class ProcessingResultOutboxProperties(
	@field:Positive val batchSize: Int = 100,
	val publishTimeout: Duration = Duration.ofSeconds(10),
) {
	init {
		require(!publishTimeout.isZero && !publishTimeout.isNegative) { "publishTimeout must be positive" }
	}
}

@EnableScheduling
@Configuration(proxyBeanMethods = false)
class ProcessingResultOutboxConfiguration {
	@Bean
	fun dispatchProcessingResults(
		repository: ProcessingJobRepository,
		publisher: ProcessingResultPublisher,
		processingClock: Clock,
		properties: ProcessingResultOutboxProperties,
	): DispatchProcessingResults = DispatchProcessingResults(
		repository = repository,
		publisher = publisher,
		clock = processingClock,
		batchSize = properties.batchSize,
	)
}

@Component
@ConditionalOnProperty(
	prefix = "app.outbox",
	name = ["scheduling-enabled"],
	havingValue = "true",
	matchIfMissing = true,
)
class ProcessingResultOutboxScheduler(
	private val dispatcher: DispatchProcessingResults,
) {
	@Scheduled(
		initialDelayString = "\${app.outbox.initial-delay:5s}",
		fixedDelayString = "\${app.outbox.fixed-delay:5s}",
	)
	fun dispatchPending() {
		val result = dispatcher.dispatch()
		if (result.published > 0 || result.failed > 0) {
			LOGGER.info(
				"Processing result outbox dispatched published={} failed={}",
				result.published,
				result.failed,
			)
		}
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(ProcessingResultOutboxScheduler::class.java)
	}
}
