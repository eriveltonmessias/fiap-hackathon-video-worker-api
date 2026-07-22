package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZipFrameArchiverTest {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `archives every jpeg frame in deterministic order`() {
		val framesDirectory = Files.createDirectory(temporaryDirectory.resolve("frames"))
		Files.write(framesDirectory.resolve("frame-000002.jpg"), byteArrayOf(2))
		Files.write(framesDirectory.resolve("frame-000001.jpg"), byteArrayOf(1))
		Files.writeString(framesDirectory.resolve("ignored.txt"), "ignored")
		val archiveFile = temporaryDirectory.resolve("frames.zip")

		ZipFrameArchiver().archive(framesDirectory, archiveFile)

		ZipFile(archiveFile.toFile()).use { zip ->
			val entries = zip.entries().asSequence().toList()
			assertEquals(listOf("frame-000001.jpg", "frame-000002.jpg"), entries.map { it.name })
			assertContentEquals(byteArrayOf(1), zip.getInputStream(entries[0]).readAllBytes())
			assertContentEquals(byteArrayOf(2), zip.getInputStream(entries[1]).readAllBytes())
		}
	}
}
