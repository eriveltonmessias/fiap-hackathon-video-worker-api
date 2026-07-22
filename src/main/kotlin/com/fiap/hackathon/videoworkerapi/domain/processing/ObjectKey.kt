package com.fiap.hackathon.videoworkerapi.domain.processing

@JvmInline
value class ObjectKey private constructor(val value: String) {
	companion object {
		fun of(value: String): ObjectKey {
			val normalized = value.trim()
			require(normalized.isNotEmpty()) { "objectKey must not be blank" }
			require(normalized.length <= 1024) { "objectKey must have at most 1024 characters" }
			require(normalized.none(Char::isISOControl)) { "objectKey must not contain control characters" }
			return ObjectKey(normalized)
		}
	}
}
