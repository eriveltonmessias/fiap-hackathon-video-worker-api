package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractionCancelledException
import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractionException
import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractionResult
import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractionTimeoutException
import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractor
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
class FfmpegFrameExtractor(
	private val properties: FfmpegProperties,
) : FrameExtractor, DisposableBean {
	private val activeProcesses = ConcurrentHashMap.newKeySet<Process>()
	private val lifecycleLock = Any()

	@Volatile
	private var shuttingDown = false

	override fun extract(inputFile: Path, outputDirectory: Path): FrameExtractionResult {
		require(Files.isRegularFile(inputFile)) { "Input video must be a regular file" }
		prepareEmptyOutputDirectory(outputDirectory)

		val startedAt = System.nanoTime()
		val process = startTrackedProcess(inputFile, outputDirectory)

		try {
			if (!process.waitFor(properties.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				terminate(process)
				deleteGeneratedFrames(outputDirectory)
				throw FrameExtractionTimeoutException("FFmpeg frame extraction timed out")
			}

			if (process.exitValue() != 0) {
				deleteGeneratedFrames(outputDirectory)
				if (shuttingDown) {
					throw FrameExtractionCancelledException("FFmpeg frame extraction was cancelled")
				}
				throw FrameExtractionException("FFmpeg frame extraction failed")
			}

			val frameCount = generatedFrames(outputDirectory).size
			if (frameCount == 0) {
				throw FrameExtractionException("FFmpeg did not generate any frames")
			}

			return FrameExtractionResult(
				frameCount = frameCount,
				duration = Duration.ofNanos(System.nanoTime() - startedAt),
			)
		} catch (exception: InterruptedException) {
			terminate(process)
			deleteGeneratedFrames(outputDirectory)
			Thread.currentThread().interrupt()
			throw FrameExtractionCancelledException("FFmpeg frame extraction was interrupted")
		} finally {
			activeProcesses.remove(process)
		}
	}

	override fun destroy() {
		val processes = synchronized(lifecycleLock) {
			shuttingDown = true
			activeProcesses.toList()
		}
		processes.forEach(::terminate)
	}

	private fun startTrackedProcess(inputFile: Path, outputDirectory: Path): Process = synchronized(lifecycleLock) {
		if (shuttingDown) {
			throw FrameExtractionCancelledException("FFmpeg frame extractor is shutting down")
		}
		startProcess(inputFile, outputDirectory).also(activeProcesses::add)
	}

	private fun startProcess(inputFile: Path, outputDirectory: Path): Process {
		val outputPattern = outputDirectory.resolve(FRAME_PATTERN)
		val command = listOf(
			properties.executable,
			"-nostdin",
			"-hide_banner",
			"-loglevel",
			"error",
			"-y",
			"-i",
			inputFile.toAbsolutePath().normalize().toString(),
			"-vf",
			"fps=${properties.framesPerSecond}",
			"-frames:v",
			properties.maxFrames.toString(),
			outputPattern.toAbsolutePath().normalize().toString(),
		)

		return try {
			ProcessBuilder(command)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start()
		} catch (exception: IOException) {
			throw FrameExtractionException("Could not start FFmpeg frame extraction")
		}
	}

	private fun prepareEmptyOutputDirectory(outputDirectory: Path) {
		Files.createDirectories(outputDirectory)
		Files.list(outputDirectory).use { entries ->
			require(entries.findAny().isEmpty) { "Frame output directory must be empty" }
		}
	}

	private fun generatedFrames(outputDirectory: Path): List<Path> =
		Files.newDirectoryStream(outputDirectory, FRAME_GLOB).use { entries ->
			entries.filter(Files::isRegularFile).toList()
		}

	private fun deleteGeneratedFrames(outputDirectory: Path) {
		generatedFrames(outputDirectory).forEach { Files.deleteIfExists(it) }
	}

	private fun terminate(process: Process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly)
		process.destroy()
		try {
			if (!process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly()
			}
		} catch (exception: InterruptedException) {
			process.destroyForcibly()
			Thread.currentThread().interrupt()
		}
	}

	private companion object {
		const val FRAME_PATTERN = "frame-%06d.jpg"
		const val FRAME_GLOB = "frame-*.jpg"
		const val TERMINATION_GRACE_SECONDS = 1L
	}
}
