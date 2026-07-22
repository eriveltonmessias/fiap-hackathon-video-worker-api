package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import java.util.UUID

interface ProcessingJobRepository {
	fun save(job: VideoProcessingJob): VideoProcessingJob

	fun findByVideoId(videoId: UUID): VideoProcessingJob?

	fun findByRequestEventId(requestEventId: UUID): VideoProcessingJob?
}
