package com.fiap.hackathon.videoworkerapi.infrastructure.storage

import com.fiap.hackathon.videoworkerapi.application.processing.StorageUnavailableException
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.errors.ErrorResponseException
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class MinioBucketInitializer(
	private val minioClient: MinioClient,
	private val properties: MinioProperties,
) : ApplicationRunner {
	override fun run(args: ApplicationArguments) {
		if (properties.initializeBuckets) initializeBuckets()
	}

	fun initializeBuckets() {
		setOf(properties.inputBucket, properties.outputBucket).forEach(::createIfMissing)
	}

	private fun createIfMissing(bucket: String) {
		try {
			if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
			}
		} catch (exception: ErrorResponseException) {
			if (exception.errorResponse().code() !in BUCKET_EXISTS_CODES) {
				throw StorageUnavailableException("initialization")
			}
		} catch (exception: Exception) {
			throw StorageUnavailableException("initialization")
		}
	}

	private companion object {
		val BUCKET_EXISTS_CODES = setOf("BucketAlreadyExists", "BucketAlreadyOwnedByYou")
	}
}
