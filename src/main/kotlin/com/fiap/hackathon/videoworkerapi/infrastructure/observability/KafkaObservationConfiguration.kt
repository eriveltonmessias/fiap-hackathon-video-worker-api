package com.fiap.hackathon.videoworkerapi.infrastructure.observability

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

@Configuration(proxyBeanMethods = false)
class KafkaObservationConfiguration {
	@Bean
	fun enableKafkaTemplateObservation(
		kafkaTemplate: KafkaTemplate<String, String>,
	): SmartInitializingSingleton = SmartInitializingSingleton {
		kafkaTemplate.setObservationEnabled(true)
	}
}
