package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.FrameArchiver
import com.fiap.hackathon.videoworkerapi.application.processing.FrameArchivingException
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Component
class ZipFrameArchiver : FrameArchiver {
	override fun archive(framesDirectory: Path, archiveFile: Path) {
		try {
			val frames = Files.list(framesDirectory).use { entries ->
				entries
					.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
					.filter { it.fileName.toString().endsWith(JPEG_EXTENSION, ignoreCase = true) }
					.sorted()
					.toList()
			}
			require(frames.isNotEmpty()) { "At least one frame is required" }

			ZipOutputStream(Files.newOutputStream(archiveFile)).use { zip ->
				frames.forEach { frame ->
					zip.putNextEntry(ZipEntry(frame.fileName.toString()))
					Files.newInputStream(frame).use { it.copyTo(zip) }
					zip.closeEntry()
				}
			}
		} catch (exception: Exception) {
			runCatching { Files.deleteIfExists(archiveFile) }
			throw FrameArchivingException()
		}
	}

	private companion object {
		const val JPEG_EXTENSION = ".jpg"
	}
}
