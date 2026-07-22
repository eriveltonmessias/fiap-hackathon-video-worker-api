package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.HandleVideoProcessingRequest
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobIdGenerator
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class ProcessingApplicationConfiguration {
	@Bean
	fun processingJobIdGenerator(): ProcessingJobIdGenerator = ProcessingJobIdGenerator(UUID::randomUUID)

	@Bean
	fun handleVideoProcessingRequest(
		repository: ProcessingJobRepository,
		idGenerator: ProcessingJobIdGenerator,
	): HandleVideoProcessingRequest = HandleVideoProcessingRequest(repository, idGenerator)
}
