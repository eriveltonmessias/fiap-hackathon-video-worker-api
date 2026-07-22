package com.fiap.hackathon.videoworkerapi.infrastructure.storage

import io.minio.MinioClient
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("app.storage.minio")
data class MinioProperties(
	@field:NotBlank val endpoint: String,
	@field:NotBlank val accessKey: String,
	@field:NotBlank val secretKey: String,
	@field:NotBlank val inputBucket: String,
	@field:NotBlank val outputBucket: String,
)

@Configuration(proxyBeanMethods = false)
class MinioConfiguration {
	@Bean
	fun minioClient(properties: MinioProperties): MinioClient = MinioClient.builder()
		.endpoint(properties.endpoint)
		.credentials(properties.accessKey, properties.secretKey)
		.build()
}
