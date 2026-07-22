package com.fiap.hackathon.videoworkerapi.application.processing

import java.nio.file.Path
import java.time.Duration

fun interface FrameExtractor {
	fun extract(inputFile: Path, outputDirectory: Path): FrameExtractionResult
}

data class FrameExtractionResult(
	val frameCount: Int,
	val duration: Duration,
)

open class FrameExtractionException(message: String) : RuntimeException(message)

class FrameExtractionTimeoutException(message: String) : FrameExtractionException(message)
