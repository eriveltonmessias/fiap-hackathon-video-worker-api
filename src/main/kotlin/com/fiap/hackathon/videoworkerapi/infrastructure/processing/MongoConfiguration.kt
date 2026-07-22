package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import org.bson.UuidRepresentation
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MongoConfiguration {
	@Bean
	fun standardUuidRepresentation(): MongoClientSettingsBuilderCustomizer =
		MongoClientSettingsBuilderCustomizer { builder ->
			builder.uuidRepresentation(UuidRepresentation.STANDARD)
		}
}
