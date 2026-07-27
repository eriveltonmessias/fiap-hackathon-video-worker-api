package com.fiap.hackathon.videoworkerapi.infrastructure.observability

import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingFailureType
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@Component
class MicrometerProcessingMetrics(
	private val registry: MeterRegistry,
) : ProcessingMetrics {
	private val activeJobs = AtomicInteger()

	init {
		registry.gauge(ACTIVE_METRIC, activeJobs)
	}

	override fun attemptStarted(retry: Boolean) {
		activeJobs.incrementAndGet()
		registry.counter(ATTEMPTS_METRIC).increment()
		if (retry) registry.counter(RETRIES_METRIC).increment()
	}

	override fun framesGenerated(count: Int) {
		require(count > 0) { "frame count must be greater than zero" }
		registry.counter(FRAMES_METRIC).increment(count.toDouble())
	}

	override fun completed(duration: Duration) {
		decrementActiveJobs()
		registry.counter(JOBS_METRIC, OUTCOME_TAG, COMPLETED_OUTCOME).increment()
		recordDuration(duration, COMPLETED_OUTCOME)
	}

	override fun failed(type: ProcessingFailureType, duration: Duration?, terminal: Boolean) {
		decrementActiveJobs()
		registry.counter(FAILURES_METRIC, FAILURE_TYPE_TAG, type.name.lowercase()).increment()
		if (terminal) registry.counter(JOBS_METRIC, OUTCOME_TAG, FAILED_OUTCOME).increment()
		duration?.let { recordDuration(it, if (terminal) FAILED_OUTCOME else RETRY_OUTCOME) }
	}

	private fun recordDuration(duration: Duration, outcome: String) {
		Timer.builder(DURATION_METRIC)
			.tag(OUTCOME_TAG, outcome)
			.publishPercentileHistogram()
			.register(registry)
			.record(duration)
	}

	private fun decrementActiveJobs() {
		activeJobs.updateAndGet { current -> maxOf(0, current - 1) }
	}

	private companion object {
		const val ATTEMPTS_METRIC = "video.worker.processing.attempts"
		const val RETRIES_METRIC = "video.worker.processing.retries"
		const val FRAMES_METRIC = "video.worker.frames.generated"
		const val JOBS_METRIC = "video.worker.jobs"
		const val FAILURES_METRIC = "video.worker.failures"
		const val DURATION_METRIC = "video.worker.processing.duration"
		const val ACTIVE_METRIC = "video.worker.processing.active"
		const val OUTCOME_TAG = "outcome"
		const val FAILURE_TYPE_TAG = "type"
		const val COMPLETED_OUTCOME = "completed"
		const val FAILED_OUTCOME = "failed"
		const val RETRY_OUTCOME = "retry"
	}
}
