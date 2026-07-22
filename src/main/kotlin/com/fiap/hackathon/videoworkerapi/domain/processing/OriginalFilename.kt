package com.fiap.hackathon.videoworkerapi.domain.processing

@JvmInline
value class OriginalFilename private constructor(val value: String) {
	companion object {
		fun of(value: String): OriginalFilename {
			val normalized = value.trim()
			require(normalized.isNotEmpty()) { "originalFilename must not be blank" }
			require(normalized.length <= 255) { "originalFilename must have at most 255 characters" }
			require('/' !in normalized && '\\' !in normalized) {
				"originalFilename must not contain path separators"
			}
			require(normalized.none(Char::isISOControl)) {
				"originalFilename must not contain control characters"
			}
			return OriginalFilename(normalized)
		}
	}
}
