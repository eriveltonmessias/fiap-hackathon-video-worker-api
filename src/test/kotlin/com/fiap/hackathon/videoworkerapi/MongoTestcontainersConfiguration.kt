package com.fiap.hackathon.videoworkerapi

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@TestConfiguration(proxyBeanMethods = false)
class MongoTestcontainersConfiguration {
	@Bean
	@ServiceConnection
	fun mongoDBContainer(): MongoDBContainer =
		MongoDBContainer(DockerImageName.parse("mongo:7.0"))
			.withStartupTimeout(Duration.ofMinutes(2))
			.withStartupAttempts(3)
}
