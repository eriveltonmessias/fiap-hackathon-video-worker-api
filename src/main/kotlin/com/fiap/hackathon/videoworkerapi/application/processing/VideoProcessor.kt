package com.fiap.hackathon.videoworkerapi.application.processing

import com.fiap.hackathon.videoworkerapi.domain.processing.FailureReason
import com.fiap.hackathon.videoworkerapi.domain.processing.ObjectKey
import com.fiap.hackathon.videoworkerapi.domain.processing.ProcessingJobStatus
import com.fiap.hackathon.videoworkerapi.domain.processing.VideoProcessingJob
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.Comparator
import java.util.UUID

fun interface VideoProcessor {
	fun process(videoId: UUID)
}

fun interface ProcessingResultEventIdGenerator {
	fun generate(): UUID
}

class DefaultVideoProcessor(
	private val repository: ProcessingJobRepository,
	private val storage: VideoStorage,
	private val frameExtractor: FrameExtractor,
	private val frameArchiver: FrameArchiver,
	private val clock: Clock,
	private val temporaryDirectory: Path,
	private val resultEventIdGenerator: ProcessingResultEventIdGenerator,
) : VideoProcessor {
	override fun process(videoId: UUID) {
		val job = requireNotNull(repository.findByVideoId(videoId)) { "Processing job was not found" }
		if (job.status != ProcessingJobStatus.RECEIVED) return

		var workspace: Path? = null
		try {
			transition(job, VideoProcessingJob::start)
			Files.createDirectories(temporaryDirectory)
			workspace = Files.createTempDirectory(temporaryDirectory, WORKSPACE_PREFIX)
			val inputFile = workspace.resolve(INPUT_FILENAME)
			val framesDirectory = workspace.resolve(FRAMES_DIRECTORY)
			val archiveFile = workspace.resolve(ARCHIVE_FILENAME)

			storage.download(StorageBucket.INPUT, job.inputObjectKey).use { input ->
				Files.newOutputStream(inputFile).use(input::copyTo)
			}

			transition(job, VideoProcessingJob::markGeneratingFrames)
			val extraction = frameExtractor.extract(inputFile, framesDirectory)
			transition(job) { changedAt -> job.markCompressing(extraction.frameCount, changedAt) }

			frameArchiver.archive(framesDirectory, archiveFile)
			transition(job, VideoProcessingJob::markUploadingResult)
			val outputObjectKey = outputObjectKey(job)
			Files.newInputStream(archiveFile).use { archive ->
				storage.upload(
					bucket = StorageBucket.OUTPUT,
					objectKey = outputObjectKey,
					content = archive,
					contentLength = Files.size(archiveFile),
					contentType = ZIP_CONTENT_TYPE,
				)
			}

			transition(job) { changedAt ->
				job.complete(outputObjectKey, resultEventIdGenerator.generate(), changedAt)
			}
		} catch (exception: Exception) {
			val reason = failureReason(exception) ?: throw exception
			job.fail(reason, resultEventIdGenerator.generate(), nextTimestamp(job))
			repository.save(job)
		} finally {
			workspace?.let(::deleteWorkspace)
		}
	}

	private fun transition(job: VideoProcessingJob, change: VideoProcessingJob.(Instant) -> Unit) {
		job.change(nextTimestamp(job))
		repository.save(job)
	}

	private fun nextTimestamp(job: VideoProcessingJob): Instant = maxOf(Instant.now(clock), job.updatedAt)

	private fun failureReason(exception: Exception): FailureReason? = when (exception) {
		is StorageObjectNotFoundException -> FailureReason.of("Input video was not found")
		is StorageUnavailableException -> FailureReason.of("Storage operation failed")
		is FrameExtractionTimeoutException -> FailureReason.of("Frame extraction timed out")
		is FrameExtractionException -> FailureReason.of("Frame extraction failed")
		is FrameArchivingException -> FailureReason.of("Frame compression failed")
		is IOException -> FailureReason.of("Temporary file operation failed")
		else -> null
	}

	private fun outputObjectKey(job: VideoProcessingJob): ObjectKey = ObjectKey.of(
		"customers/${job.customerId}/videos/${job.videoId}/output/$ARCHIVE_FILENAME",
	)

	private fun deleteWorkspace(workspace: Path) {
		try {
			Files.walk(workspace).use { paths ->
				paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
			}
		} catch (exception: IOException) {
			LOGGER.warn("Could not completely clean processing workspace errorType={}", exception.javaClass.simpleName)
		}
	}

	private companion object {
		const val WORKSPACE_PREFIX = "video-processing-"
		const val INPUT_FILENAME = "input-video"
		const val FRAMES_DIRECTORY = "frames"
		const val ARCHIVE_FILENAME = "frames.zip"
		const val ZIP_CONTENT_TYPE = "application/zip"
		val LOGGER = LoggerFactory.getLogger(DefaultVideoProcessor::class.java)
	}
}
