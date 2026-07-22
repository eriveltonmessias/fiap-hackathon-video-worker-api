package com.fiap.hackathon.videoworkerapi.domain.processing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProcessingValuesTest {
	@Test
	fun `normalizes validated values`() {
		assertEquals("video.mp4", OriginalFilename.of(" video.mp4 ").value)
		assertEquals("input/video.mp4", ObjectKey.of(" input/video.mp4 ").value)
		assertEquals("Processing failed", FailureReason.of(" Processing failed ").value)
	}

	@Test
	fun `rejects unsafe original filenames`() {
		listOf("", "   ", "../video.mp4", "folder/video.mp4", "folder\\video.mp4", "video\n.mp4").forEach {
			assertFailsWith<IllegalArgumentException> { OriginalFilename.of(it) }
		}
	}

	@Test
	fun `rejects invalid object keys`() {
		assertFailsWith<IllegalArgumentException> { ObjectKey.of(" ") }
		assertFailsWith<IllegalArgumentException> { ObjectKey.of("input/video\n.mp4") }
		assertFailsWith<IllegalArgumentException> { ObjectKey.of("a".repeat(1025)) }
	}

	@Test
	fun `rejects unsafe failure reasons`() {
		assertFailsWith<IllegalArgumentException> { FailureReason.of(" ") }
		assertFailsWith<IllegalArgumentException> { FailureReason.of("failure\rwith control") }
		assertFailsWith<IllegalArgumentException> { FailureReason.of("a".repeat(1001)) }
	}
}
