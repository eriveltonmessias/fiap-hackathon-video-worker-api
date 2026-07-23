package com.fiap.hackathon.videoworkerapi.application.processing

import java.time.Duration

enum class ProcessingFailureType {
	INPUT_NOT_FOUND,
	FRAME_TIMEOUT,
	FRAME_EXTRACTION,
	STORAGE,
	COMPRESSION,
	TEMPORARY_FILE,
	CANCELLED,
	RETRIES_EXHAUSTED,
	UNKNOWN,
}

interface ProcessingMetrics {
	fun attemptStarted(retry: Boolean)

	fun framesGenerated(count: Int)

	fun completed(duration: Duration)

	fun failed(type: ProcessingFailureType, duration: Duration?, terminal: Boolean)

	companion object {
		val NOOP: ProcessingMetrics = object : ProcessingMetrics {
			override fun attemptStarted(retry: Boolean) = Unit
			override fun framesGenerated(count: Int) = Unit
			override fun completed(duration: Duration) = Unit
			override fun failed(type: ProcessingFailureType, duration: Duration?, terminal: Boolean) = Unit
		}
	}
}
