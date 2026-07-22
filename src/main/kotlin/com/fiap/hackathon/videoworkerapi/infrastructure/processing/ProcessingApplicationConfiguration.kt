package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.DefaultVideoProcessor
import com.fiap.hackathon.videoworkerapi.application.processing.FrameArchiver
import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractor
import com.fiap.hackathon.videoworkerapi.application.processing.HandleVideoProcessingRequest
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobIdGenerator
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.application.processing.VideoProcessor
import com.fiap.hackathon.videoworkerapi.application.processing.VideoStorage
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

@ConfigurationProperties("app.processing")
data class ProcessingProperties(
	val temporaryDirectory: Path = Path.of(System.getProperty("java.io.tmpdir")),
)

@Configuration(proxyBeanMethods = false)
class ProcessingApplicationConfiguration {
	@Bean
	fun processingJobIdGenerator(): ProcessingJobIdGenerator = ProcessingJobIdGenerator(UUID::randomUUID)

	@Bean
	fun handleVideoProcessingRequest(
		repository: ProcessingJobRepository,
		idGenerator: ProcessingJobIdGenerator,
	): HandleVideoProcessingRequest = HandleVideoProcessingRequest(repository, idGenerator)

	@Bean
	fun processingClock(): Clock = Clock.systemUTC()

	@Bean
	fun videoProcessor(
		repository: ProcessingJobRepository,
		storage: VideoStorage,
		frameExtractor: FrameExtractor,
		frameArchiver: FrameArchiver,
		processingClock: Clock,
		processingProperties: ProcessingProperties,
	): VideoProcessor = DefaultVideoProcessor(
		repository = repository,
		storage = storage,
		frameExtractor = frameExtractor,
		frameArchiver = frameArchiver,
		clock = processingClock,
		temporaryDirectory = processingProperties.temporaryDirectory,
	)
}
