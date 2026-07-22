package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface SpringDataProcessingJobRepository : MongoRepository<MongoProcessingJobDocument, UUID> {
	fun findByVideoId(videoId: UUID): MongoProcessingJobDocument?

	fun findByRequestEventId(requestEventId: UUID): MongoProcessingJobDocument?
}

@Component
class MongoProcessingJobRepository(
	private val repository: SpringDataProcessingJobRepository,
) : ProcessingJobRepository {
	override fun save(job: VideoProcessingJob): VideoProcessingJob = repository.save(job.toDocument()).toDomain()

	override fun saveIfAbsent(job: VideoProcessingJob): Boolean = try {
		repository.insert(job.toDocument())
		true
	} catch (exception: DuplicateKeyException) {
		false
	}

	override fun findByVideoId(videoId: UUID): VideoProcessingJob? = repository.findByVideoId(videoId)?.toDomain()

	override fun findByRequestEventId(requestEventId: UUID): VideoProcessingJob? =
		repository.findByRequestEventId(requestEventId)?.toDomain()
}
