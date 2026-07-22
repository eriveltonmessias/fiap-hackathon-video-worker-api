package com.fiap.hackathon.videoworkerapi

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class KafkaTestcontainersConfiguration {
	@Bean
	@ServiceConnection
	fun kafkaContainer(): KafkaContainer =
		KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0"))
}
