package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingJobRepository
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Component
import java.util.UUID

interface SpringDataProcessingJobRepository : MongoRepository<MongoProcessingJobDocument, UUID> {
	fun findByVideoId(videoId: UUID): MongoProcessingJobDocument?

	fun findByRequestEventId(requestEventId: UUID): MongoProcessingJobDocument?

	@Query("{ 'resultOutbox': { \$ne: null }, 'resultOutbox.publishedAt': null }")
	fun findPendingResults(pageable: Pageable): List<MongoProcessingJobDocument>
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

	override fun findPendingResults(limit: Int): List<VideoProcessingJob> {
		require(limit > 0) { "limit must be greater than zero" }
		val page = PageRequest.of(0, limit, Sort.by("resultOutbox.occurredAt").ascending())
		return repository.findPendingResults(page).map(MongoProcessingJobDocument::toDomain)
	}
}
