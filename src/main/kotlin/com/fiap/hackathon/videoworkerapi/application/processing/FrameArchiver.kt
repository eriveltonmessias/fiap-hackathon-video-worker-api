package com.fiap.hackathon.videoworkerapi.application.processing

import java.nio.file.Path

fun interface FrameArchiver {
	fun archive(framesDirectory: Path, archiveFile: Path)
}

class FrameArchivingException : RuntimeException("Frame compression failed")
