package com.fiap.hackathon.videoworkerapi

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class MongoTestcontainersConfiguration {
	@Bean
	@ServiceConnection
	fun mongoDBContainer(): MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
}
