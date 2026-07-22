package com.fiap.hackathon.videoworkerapi.infrastructure.storage

import com.fiap.hackathon.videoworkerapi.MinioTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.StorageBucket
import com.fiap.hackathon.videoworkerapi.application.processing.StorageObjectNotFoundException
import com.fiap.hackathon.videoworkerapi.application.processing.StorageUnavailableException
import com.fiap.hackathon.videoworkerapi.application.processing.VideoStorage
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.io.ByteArrayInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = [
		"management.server.port=-1",
		"spring.data.mongodb.auto-index-creation=false",
		"spring.kafka.listener.auto-startup=false",
	],
)
@Import(MinioTestcontainersConfiguration::class)
class MinioVideoStorageIntegrationTest(
	@Autowired private val videoStorage: VideoStorage,
	@Autowired private val bucketInitializer: MinioBucketInitializer,
	@Autowired private val minioClient: MinioClient,
	@Autowired private val properties: MinioProperties,
) {
	@Test
	fun `initializes buckets idempotently and streams input and output objects`() {
		bucketInitializer.initializeBuckets()
		bucketInitializer.initializeBuckets()

		assertTrue(bucketExists(properties.inputBucket))
		assertTrue(bucketExists(properties.outputBucket))

		val inputKey = ObjectKey.of("customers/customer-id/videos/video-id/input/source.mp4")
		val video = "fake-video-content".toByteArray()
		videoStorage.upload(
			StorageBucket.INPUT,
			inputKey,
			ByteArrayInputStream(video),
			video.size.toLong(),
			"video/mp4",
		)
		videoStorage.download(StorageBucket.INPUT, inputKey).use {
			assertContentEquals(video, it.readAllBytes())
		}

		val outputKey = ObjectKey.of("customers/customer-id/videos/video-id/output/frames.zip")
		val zip = "fake-zip-content".toByteArray()
		videoStorage.upload(
			StorageBucket.OUTPUT,
			outputKey,
			ByteArrayInputStream(zip),
			zip.size.toLong(),
			"application/zip",
		)
		videoStorage.download(StorageBucket.OUTPUT, outputKey).use {
			assertContentEquals(zip, it.readAllBytes())
		}
	}

	@Test
	fun `translates missing object without exposing its key`() {
		val secretKey = ObjectKey.of("customers/secret-customer/videos/secret-video/input/source.mp4")

		val exception = assertFailsWith<StorageObjectNotFoundException> {
			videoStorage.download(StorageBucket.INPUT, secretKey).use { it.read() }
		}

		assertEquals("Storage object was not found", exception.message)
		assertFalse(exception.message.orEmpty().contains(secretKey.value))
	}

	@Test
	fun `translates unavailable storage without exposing configuration`() {
		val unavailableProperties = properties.copy(
			endpoint = "http://127.0.0.1:1",
			accessKey = "sensitive-access-key",
			secretKey = "sensitive-secret-key",
		)
		val unavailableStorage = MinioVideoStorage(
			MinioClient.builder()
				.endpoint(unavailableProperties.endpoint)
				.credentials(unavailableProperties.accessKey, unavailableProperties.secretKey)
				.build(),
			unavailableProperties,
		)
		val objectKey = ObjectKey.of("customers/sensitive/video.mp4")

		val exception = assertFailsWith<StorageUnavailableException> {
			unavailableStorage.download(StorageBucket.INPUT, objectKey).use { it.read() }
		}

		assertEquals("Storage download failed", exception.message)
		listOf(
			unavailableProperties.endpoint,
			unavailableProperties.accessKey,
			unavailableProperties.secretKey,
			objectKey.value,
		).forEach { sensitiveValue ->
			assertFalse(exception.message.orEmpty().contains(sensitiveValue))
		}
	}

	private fun bucketExists(bucket: String): Boolean = minioClient.bucketExists(
		BucketExistsArgs.builder().bucket(bucket).build(),
	)
}
