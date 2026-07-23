package com.fiap.hackathon.videoworkerapi.infrastructure.observability

import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingFailureType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class MicrometerProcessingMetricsTest {
	private val registry = SimpleMeterRegistry()
	private val metrics = MicrometerProcessingMetrics(registry)

	@Test
	fun `records jobs duration frames failures attempts and retries`() {
		metrics.attemptStarted(retry = false)
		metrics.attemptStarted(retry = true)
		metrics.framesGenerated(12)
		metrics.completed(Duration.ofSeconds(2))
		metrics.failed(ProcessingFailureType.STORAGE, Duration.ofSeconds(1), terminal = false)
		metrics.failed(ProcessingFailureType.FRAME_TIMEOUT, Duration.ofSeconds(3), terminal = true)

		assertEquals(2.0, registry.counter("video.worker.processing.attempts").count())
		assertEquals(1.0, registry.counter("video.worker.processing.retries").count())
		assertEquals(12.0, registry.counter("video.worker.frames.generated").count())
		assertEquals(1.0, registry.counter("video.worker.jobs", "outcome", "completed").count())
		assertEquals(1.0, registry.counter("video.worker.jobs", "outcome", "failed").count())
		assertEquals(1.0, registry.counter("video.worker.failures", "type", "storage").count())
		assertEquals(1.0, registry.counter("video.worker.failures", "type", "frame_timeout").count())
		assertEquals(
			1,
			registry.timer("video.worker.processing.duration", "outcome", "completed").count(),
		)
		assertEquals(1, registry.timer("video.worker.processing.duration", "outcome", "retry").count())
		assertEquals(1, registry.timer("video.worker.processing.duration", "outcome", "failed").count())
	}
}
