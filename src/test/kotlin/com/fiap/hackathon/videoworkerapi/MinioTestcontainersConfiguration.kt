package com.fiap.hackathon.videoworkerapi

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class MinioTestcontainersConfiguration {
	@Bean(initMethod = "start", destroyMethod = "stop")
	fun minioContainer(): MinioTestContainer = MinioTestContainer(
		DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"),
	).withExposedPorts(MINIO_API_PORT)
		.withEnv("MINIO_ROOT_USER", ACCESS_KEY)
		.withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
		.withCommand("server", "/data")
		.waitingFor(Wait.forHttp("/minio/health/live").forPort(MINIO_API_PORT))

	@Bean
	fun minioProperties(container: MinioTestContainer): DynamicPropertyRegistrar = DynamicPropertyRegistrar { registry ->
		registry.add("app.storage.minio.endpoint") {
			"http://${container.host}:${container.getMappedPort(MINIO_API_PORT)}"
		}
		registry.add("app.storage.minio.access-key") { ACCESS_KEY }
		registry.add("app.storage.minio.secret-key") { SECRET_KEY }
	}

	companion object {
		const val ACCESS_KEY = "fiapx"
		const val SECRET_KEY = "fiapx12345"
		const val MINIO_API_PORT = 9000
	}
}

class MinioTestContainer(imageName: DockerImageName) : GenericContainer<MinioTestContainer>(imageName)
