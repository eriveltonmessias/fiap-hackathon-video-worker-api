package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import java.io.InputStream

enum class StorageBucket {
	INPUT,
	OUTPUT,
}

interface VideoStorage {
	fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream

	fun upload(
		bucket: StorageBucket,
		objectKey: ObjectKey,
		content: InputStream,
		contentLength: Long,
		contentType: String,
	)
}

class StorageObjectNotFoundException : RuntimeException("Storage object was not found")

class StorageUnavailableException(operation: String) : RuntimeException("Storage $operation failed")
