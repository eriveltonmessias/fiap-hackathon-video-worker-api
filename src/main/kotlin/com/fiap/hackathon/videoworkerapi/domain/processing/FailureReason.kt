package com.fiap.hackathon.videoworkerapi.domain.processing

@JvmInline
value class FailureReason private constructor(val value: String) {
	companion object {
		fun of(value: String): FailureReason {
			val normalized = value.trim()
			require(normalized.isNotEmpty()) { "failureReason must not be blank" }
			require(normalized.length <= 1000) { "failureReason must have at most 1000 characters" }
			require(normalized.none(Char::isISOControl)) {
				"failureReason must not contain control characters"
			}
			return FailureReason(normalized)
		}
	}
}
