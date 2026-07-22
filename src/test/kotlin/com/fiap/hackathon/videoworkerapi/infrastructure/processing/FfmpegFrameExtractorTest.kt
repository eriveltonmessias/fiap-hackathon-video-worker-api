package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractionException
import com.fiap.hackathon.videoworkerapi.application.processing.FrameExtractionTimeoutException
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FfmpegFrameExtractorTest {
	@TempDir
	lateinit var temporaryDirectory: Path

	private val extractor = FfmpegFrameExtractor(FfmpegProperties(maxFrames = 2))

	@Test
	fun `extracts jpeg frames from a valid video using real ffmpeg`() {
		val inputFile = createVideo("sample.mp4")
		val outputDirectory = temporaryDirectory.resolve("frames")

		val result = extractor.extract(inputFile, outputDirectory)

		assertEquals(2, result.frameCount)
		assertTrue(result.duration >= Duration.ZERO)
		assertEquals(
			listOf("frame-000001.jpg", "frame-000002.jpg"),
			Files.list(outputDirectory).use { entries ->
				entries.map { it.fileName.toString() }.sorted().toList()
			},
		)
	}

	@Test
	fun `treats shell characters in input filename as literal text`() {
		val inputFile = createVideo("video;touch pwned.mp4")
		val outputDirectory = temporaryDirectory.resolve("safe-frames")

		val result = extractor.extract(inputFile, outputDirectory)

		assertEquals(2, result.frameCount)
		assertFalse(Files.exists(temporaryDirectory.resolve("pwned.mp4")))
	}

	@Test
	fun `fails and removes partial output for an invalid video`() {
		val inputFile = temporaryDirectory.resolve("invalid.mp4")
		Files.writeString(inputFile, "not a video")
		val outputDirectory = temporaryDirectory.resolve("invalid-frames")

		assertFailsWith<FrameExtractionException> {
			extractor.extract(inputFile, outputDirectory)
		}

		assertTrue(Files.list(outputDirectory).use { it.findAny().isEmpty })
	}

	@Test
	fun `terminates extraction after configured timeout`() {
		val slowExecutable = temporaryDirectory.resolve("slow-ffmpeg.sh")
		Files.writeString(slowExecutable, "#!/bin/sh\nsleep 5\n", StandardCharsets.UTF_8)
		Files.setPosixFilePermissions(
			slowExecutable,
			setOf(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE,
			),
		)
		val timedExtractor = FfmpegFrameExtractor(
			FfmpegProperties(
				executable = slowExecutable.toString(),
				timeout = Duration.ofMillis(100),
			),
		)
		val inputFile = createVideo("timeout.mp4")

		assertFailsWith<FrameExtractionTimeoutException> {
			timedExtractor.extract(inputFile, temporaryDirectory.resolve("timeout-frames"))
		}
	}

	private fun createVideo(filename: String): Path {
		val output = temporaryDirectory.resolve(filename)
		val process = ProcessBuilder(
			"ffmpeg",
			"-nostdin",
			"-hide_banner",
			"-loglevel",
			"error",
			"-y",
			"-f",
			"lavfi",
			"-i",
			"color=c=blue:s=64x64:d=2",
			"-r",
			"1",
			"-c:v",
			"mpeg4",
			output.toString(),
		)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start()

		assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Test video generation timed out")
		assertEquals(0, process.exitValue(), "Could not generate the test video with FFmpeg")
		return output
	}
}
