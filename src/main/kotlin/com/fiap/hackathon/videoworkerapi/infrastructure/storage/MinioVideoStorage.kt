package com.fiap.hackathon.videoworkerapi.infrastructure.storage

import com.fiap.hackathon.videoworkerapi.application.processing.StorageBucket
import com.fiap.hackathon.videoworkerapi.application.processing.StorageObjectNotFoundException
import com.fiap.hackathon.videoworkerapi.application.processing.StorageUnavailableException
import com.fiap.hackathon.videoworkerapi.application.processing.VideoStorage
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import org.springframework.stereotype.Component
import java.io.FilterInputStream
import java.io.InputStream

@Component
class MinioVideoStorage(
	private val minioClient: MinioClient,
	private val properties: MinioProperties,
) : VideoStorage {
	override fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream = try {
		val bucketName = bucketName(bucket)
		minioClient.statObject(
			StatObjectArgs.builder()
				.bucket(bucketName)
				.`object`(objectKey.value)
				.build(),
		)
		SafeStorageInputStream(
			minioClient.getObject(
				GetObjectArgs.builder()
					.bucket(bucketName)
					.`object`(objectKey.value)
					.build(),
			),
		)
	} catch (exception: ErrorResponseException) {
		if (exception.errorResponse().code() in MISSING_OBJECT_CODES) {
			throw StorageObjectNotFoundException()
		}
		throw StorageUnavailableException("download")
	} catch (exception: Exception) {
		throw StorageUnavailableException("download")
	}

	private class SafeStorageInputStream(input: InputStream) : FilterInputStream(input) {
		override fun read(): Int = safely { super.read() }

		override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
			safely { super.read(buffer, offset, length) }

		override fun skip(count: Long): Long = safely { super.skip(count) }

		override fun available(): Int = safely { super.available() }

		override fun reset() {
			safely { super.reset() }
		}

		override fun close() {
			safely { super.close() }
		}

		private fun <T> safely(operation: () -> T): T = try {
			operation()
		} catch (exception: Exception) {
			throw StorageUnavailableException("download")
		}
	}

	override fun upload(
		bucket: StorageBucket,
		objectKey: ObjectKey,
		content: InputStream,
		contentLength: Long,
		contentType: String,
	) {
		require(contentLength >= 0) { "contentLength must not be negative" }
		require(contentType.isNotBlank()) { "contentType must not be blank" }
		try {
			minioClient.putObject(
				PutObjectArgs.builder()
					.bucket(bucketName(bucket))
					.`object`(objectKey.value)
					.stream(content, contentLength, MULTIPART_PART_SIZE)
					.contentType(contentType)
					.build(),
			)
		} catch (exception: Exception) {
			throw StorageUnavailableException("upload")
		}
	}

	private fun bucketName(bucket: StorageBucket): String = when (bucket) {
		StorageBucket.INPUT -> properties.inputBucket
		StorageBucket.OUTPUT -> properties.outputBucket
	}

	private companion object {
		const val MULTIPART_PART_SIZE = 10L * 1024 * 1024
		val MISSING_OBJECT_CODES = setOf("NoSuchKey", "NoSuchObject", "NotFound")
	}
}
