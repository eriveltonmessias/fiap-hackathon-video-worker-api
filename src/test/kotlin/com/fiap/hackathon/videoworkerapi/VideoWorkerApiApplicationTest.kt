package com.fiap.hackathon.videoworkerapi

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = [
		"management.server.port=0",
		"spring.data.mongodb.auto-index-creation=false",
	],
)
class VideoWorkerApiApplicationTest {
	@Test
	fun contextLoads() = Unit
}
