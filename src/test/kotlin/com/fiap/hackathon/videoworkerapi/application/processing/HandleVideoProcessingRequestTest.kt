package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class HandleVideoProcessingRequestTest {
	private val repository = InMemoryProcessingJobRepository()
	private val generatedId = UUID.fromString("603620cb-d9de-4b1c-9854-6f14ac3d5153")
	private val handler = HandleVideoProcessingRequest(repository, ProcessingJobIdGenerator { generatedId })

	@Test
	fun `registers a new processing request`() {
		val request = request()

		val result = handler.handle(request)

		assertEquals(ProcessingRequestResult.REGISTERED, result)
		val job = requireNotNull(repository.findByVideoId(request.videoId))
		assertEquals(generatedId, job.id)
		assertEquals(request.eventId, job.requestEventId)
		assertEquals(request.occurredAt, job.createdAt)
	}

	@Test
	fun `returns already registered for repeated event or video`() {
		val first = request()
		assertEquals(ProcessingRequestResult.REGISTERED, handler.handle(first))

		assertEquals(ProcessingRequestResult.ALREADY_REGISTERED, handler.handle(first))
		assertEquals(
			ProcessingRequestResult.ALREADY_REGISTERED,
			handler.handle(first.copy(eventId = UUID.randomUUID())),
		)
		assertEquals(1, repository.jobs.size)
	}

	private fun request(): VideoProcessingRequest = VideoProcessingRequest(
		eventId = UUID.fromString("86467e08-c764-413f-9857-75314e3fe607"),
		occurredAt = Instant.parse("2026-01-01T10:00:00Z"),
		videoId = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f"),
		customerId = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc"),
		originalFilename = OriginalFilename.of("lesson.mp4"),
		inputObjectKey = ObjectKey.of("customers/customer/videos/video/input/lesson.mp4"),
	)
}

private class InMemoryProcessingJobRepository : ProcessingJobRepository {
	val jobs = mutableListOf<VideoProcessingJob>()

	override fun save(job: VideoProcessingJob): VideoProcessingJob = job.also {
		jobs.removeAll { existing -> existing.id == job.id }
		jobs.add(job)
	}

	override fun saveIfAbsent(job: VideoProcessingJob): Boolean {
		if (findByVideoId(job.videoId) != null || findByRequestEventId(job.requestEventId) != null) return false
		jobs.add(job)
		return true
	}

	override fun findByVideoId(videoId: UUID): VideoProcessingJob? = jobs.find { it.videoId == videoId }

	override fun findByRequestEventId(requestEventId: UUID): VideoProcessingJob? =
		jobs.find { it.requestEventId == requestEventId }
}
