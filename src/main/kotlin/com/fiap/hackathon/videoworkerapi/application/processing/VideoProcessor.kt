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
import java.time.Duration
import java.time.Instant
import java.util.Comparator
import java.util.UUID

fun interface VideoProcessor {
	fun process(videoId: UUID)
}

fun interface ProcessingResultEventIdGenerator {
	fun generate(): UUID
}

class TransientVideoProcessingException : RuntimeException("Transient video processing failure")

class DefaultVideoProcessor(
	private val repository: ProcessingJobRepository,
	private val storage: VideoStorage,
	private val frameExtractor: FrameExtractor,
	private val frameArchiver: FrameArchiver,
	private val clock: Clock,
	private val temporaryDirectory: Path,
	private val resultEventIdGenerator: ProcessingResultEventIdGenerator,
	private val metrics: ProcessingMetrics = ProcessingMetrics.NOOP,
) : VideoProcessor {
	override fun process(videoId: UUID) {
		val job = requireNotNull(repository.findByVideoId(videoId)) { "Processing job was not found" }
		if (job.isTerminal()) return
		val startedAtNanos = System.nanoTime()
		val retry = job.status != ProcessingJobStatus.RECEIVED

		var workspace: Path? = null
		try {
			if (job.status == ProcessingJobStatus.RECEIVED) {
				transition(job, VideoProcessingJob::start)
			} else {
				transition(job, VideoProcessingJob::retry)
			}
			metrics.attemptStarted(retry)
			LOGGER.info("Video processing attempt started attempt={} retry={}", job.attempts, retry)
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
			metrics.framesGenerated(extraction.frameCount)

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
			val duration = elapsedSince(startedAtNanos)
			metrics.completed(duration)
			LOGGER.info(
				"Video processing completed attempt={} frames={} durationMs={}",
				job.attempts,
				extraction.frameCount,
				duration.toMillis(),
			)
		} catch (exception: Exception) {
			val failure = permanentFailure(exception)
			val transientFailureType = transientFailureType(exception)
			when {
				failure != null -> {
					job.fail(failure.reason, resultEventIdGenerator.generate(), nextTimestamp(job))
					repository.save(job)
					val duration = elapsedSince(startedAtNanos)
					metrics.failed(failure.type, duration, terminal = true)
					LOGGER.warn(
						"Video processing failed attempt={} failureType={} durationMs={}",
						job.attempts,
						failure.type,
						duration.toMillis(),
					)
				}

				transientFailureType != null -> {
					val duration = elapsedSince(startedAtNanos)
					metrics.failed(transientFailureType, duration, terminal = false)
					LOGGER.warn(
						"Video processing attempt failed attempt={} failureType={} durationMs={}",
						job.attempts,
						transientFailureType,
						duration.toMillis(),
					)
					throw TransientVideoProcessingException()
				}

				else -> {
					val duration = elapsedSince(startedAtNanos)
					metrics.failed(ProcessingFailureType.UNKNOWN, duration, terminal = false)
					LOGGER.error(
						"Unexpected video processing failure attempt={} errorType={} durationMs={}",
						job.attempts,
						exception.javaClass.simpleName,
						duration.toMillis(),
					)
					throw exception
				}
			}
		} finally {
			workspace?.let(::deleteWorkspace)
		}
	}

	private fun transition(job: VideoProcessingJob, change: VideoProcessingJob.(Instant) -> Unit) {
		job.change(nextTimestamp(job))
		repository.save(job)
	}

	private fun nextTimestamp(job: VideoProcessingJob): Instant = maxOf(Instant.now(clock), job.updatedAt)

	private fun permanentFailure(exception: Exception): ProcessingFailure? = when (exception) {
		is StorageObjectNotFoundException -> ProcessingFailure(
			FailureReason.of("Input video was not found"),
			ProcessingFailureType.INPUT_NOT_FOUND,
		)
		is FrameExtractionTimeoutException -> ProcessingFailure(
			FailureReason.of("Frame extraction timed out"),
			ProcessingFailureType.FRAME_TIMEOUT,
		)
		is FrameExtractionCancelledException -> null
		is FrameExtractionException -> ProcessingFailure(
			FailureReason.of("Frame extraction failed"),
			ProcessingFailureType.FRAME_EXTRACTION,
		)
		else -> null
	}

	private fun transientFailureType(exception: Exception): ProcessingFailureType? = when (exception) {
		is FrameExtractionCancelledException -> ProcessingFailureType.CANCELLED
		is StorageUnavailableException -> ProcessingFailureType.STORAGE
		is FrameArchivingException -> ProcessingFailureType.COMPRESSION
		is IOException -> ProcessingFailureType.TEMPORARY_FILE
		else -> null
	}

	private fun elapsedSince(startedAtNanos: Long): Duration = Duration.ofNanos(System.nanoTime() - startedAtNanos)

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

	private data class ProcessingFailure(
		val reason: FailureReason,
		val type: ProcessingFailureType,
	)
}
