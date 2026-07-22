package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.OriginalFilename
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import com.fiap.hackathon.videoworkerapi.infrastructure.processing.ZipFrameArchiver
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultVideoProcessorTest {
	@TempDir
	lateinit var temporaryDirectory: Path

	private val videoId = UUID.fromString("5a7ff337-81a3-4f75-b93f-e43f1fc0441f")
	private val customerId = UUID.fromString("c3049024-0dbe-4cb0-aa7f-7e74dfe0ebfc")
	private val receivedAt = Instant.parse("2026-01-01T10:00:00Z")
	private val repository = RecordingProcessingJobRepository(newJob())
	private val storage = RecordingVideoStorage()

	@Test
	fun `runs every stage uploads zip and removes temporary workspace`() {
		val processor = processor { _, outputDirectory ->
			Files.createDirectories(outputDirectory)
			Files.write(outputDirectory.resolve("frame-000001.jpg"), byteArrayOf(1))
			Files.write(outputDirectory.resolve("frame-000002.jpg"), byteArrayOf(2))
			FrameExtractionResult(2, Duration.ofSeconds(1))
		}

		processor.process(videoId)

		val job = repository.job
		assertEquals(ProcessingJobStatus.COMPLETED, job.status)
		assertEquals(2, job.frameCount)
		assertEquals(
			"customers/$customerId/videos/$videoId/output/frames.zip",
			job.outputObjectKey?.value,
		)
		assertNull(job.failureReason)
		assertEquals(
			listOf(
				ProcessingJobStatus.PROCESSING,
				ProcessingJobStatus.GENERATING_FRAMES,
				ProcessingJobStatus.COMPRESSING,
				ProcessingJobStatus.UPLOADING_RESULT,
				ProcessingJobStatus.COMPLETED,
			),
			repository.savedStatuses,
		)
		assertEquals(StorageBucket.OUTPUT, storage.uploadedBucket)
		assertEquals(job.outputObjectKey, storage.uploadedObjectKey)
		assertEquals("application/zip", storage.uploadedContentType)
		assertEquals(listOf("frame-000001.jpg", "frame-000002.jpg"), zipEntries(storage.uploadedContent))
		assertDirectoryIsEmpty(temporaryDirectory)
	}

	@Test
	fun `marks failure and removes temporary workspace when extraction times out`() {
		val processor = processor { _, _ ->
			throw FrameExtractionTimeoutException("sensitive input path")
		}

		processor.process(videoId)

		assertEquals(ProcessingJobStatus.FAILED, repository.job.status)
		assertEquals("Frame extraction timed out", repository.job.failureReason?.value)
		assertNull(storage.uploadedContent)
		assertDirectoryIsEmpty(temporaryDirectory)
	}

	private fun processor(frameExtractor: FrameExtractor): VideoProcessor = DefaultVideoProcessor(
		repository = repository,
		storage = storage,
		frameExtractor = frameExtractor,
		frameArchiver = ZipFrameArchiver(),
		clock = Clock.fixed(receivedAt.plusSeconds(10), ZoneOffset.UTC),
		temporaryDirectory = temporaryDirectory,
		resultEventIdGenerator = ProcessingResultEventIdGenerator {
			UUID.fromString("ef2ec18e-9c74-4613-bf03-0a8ab1a762f1")
		},
	)

	private fun newJob(): VideoProcessingJob = VideoProcessingJob.receive(
		id = UUID.fromString("603620cb-d9de-4b1c-9854-6f14ac3d5153"),
		requestEventId = UUID.fromString("86467e08-c764-413f-9857-75314e3fe607"),
		videoId = videoId,
		customerId = customerId,
		originalFilename = OriginalFilename.of("lesson.mp4"),
		inputObjectKey = ObjectKey.of("customers/$customerId/videos/$videoId/input/lesson.mp4"),
		receivedAt = receivedAt,
	)

	private fun zipEntries(content: ByteArray?): List<String> {
		val names = mutableListOf<String>()
		ZipInputStream(ByteArrayInputStream(requireNotNull(content))).use { zip ->
			var entry = zip.nextEntry
			while (entry != null) {
				names.add(entry.name)
				entry = zip.nextEntry
			}
		}
		return names
	}

	private fun assertDirectoryIsEmpty(directory: Path) {
		assertTrue(Files.list(directory).use { it.findAny().isEmpty })
	}
}

private class RecordingProcessingJobRepository(
	var job: VideoProcessingJob,
) : ProcessingJobRepository {
	val savedStatuses = mutableListOf<ProcessingJobStatus>()

	override fun save(job: VideoProcessingJob): VideoProcessingJob = job.also {
		this.job = it
		savedStatuses.add(it.status)
	}

	override fun saveIfAbsent(job: VideoProcessingJob): Boolean = error("Not used")

	override fun findByVideoId(videoId: UUID): VideoProcessingJob? = job.takeIf { it.videoId == videoId }

	override fun findByRequestEventId(requestEventId: UUID): VideoProcessingJob? =
		job.takeIf { it.requestEventId == requestEventId }

	override fun findPendingResults(limit: Int): List<VideoProcessingJob> =
		listOf(job).filter { it.resultOutbox?.isPending == true }.take(limit)
}

private class RecordingVideoStorage : VideoStorage {
	var uploadedBucket: StorageBucket? = null
	var uploadedObjectKey: ObjectKey? = null
	var uploadedContent: ByteArray? = null
	var uploadedContentType: String? = null

	override fun download(bucket: StorageBucket, objectKey: ObjectKey): InputStream =
		ByteArrayInputStream("video".toByteArray())

	override fun upload(
		bucket: StorageBucket,
		objectKey: ObjectKey,
		content: InputStream,
		contentLength: Long,
		contentType: String,
	) {
		val output = ByteArrayOutputStream()
		content.copyTo(output)
		assertEquals(contentLength, output.size().toLong())
		uploadedBucket = bucket
		uploadedObjectKey = objectKey
		uploadedContent = output.toByteArray()
		uploadedContentType = contentType
	}
}
