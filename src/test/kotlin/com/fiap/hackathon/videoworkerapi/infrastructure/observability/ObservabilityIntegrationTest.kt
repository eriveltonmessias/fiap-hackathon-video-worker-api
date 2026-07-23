package com.fiap.hackathon.videoworkerapi.infrastructure.observability

import com.fiap.hackathon.videoworkerapi.KafkaTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MinioTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.MongoTestcontainersConfiguration
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingFailureType
import com.fiap.hackathon.videoworkerapi.application.processing.ProcessingMetrics
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = [
		"management.server.port=0",
		"spring.kafka.listener.auto-startup=false",
		"app.outbox.scheduling-enabled=false",
	],
)
@Import(
	MongoTestcontainersConfiguration::class,
	KafkaTestcontainersConfiguration::class,
	MinioTestcontainersConfiguration::class,
)
class ObservabilityIntegrationTest(
	@Autowired private val healthGroups: HealthEndpointGroups,
	@Autowired private val mongoHealthIndicator: MongoDependencyHealthIndicator,
	@Autowired private val kafkaHealthIndicator: KafkaDependencyHealthIndicator,
	@Autowired private val minioHealthIndicator: MinioDependencyHealthIndicator,
	@Autowired private val metrics: ProcessingMetrics,
	@LocalManagementPort private val managementPort: Int,
) {
	@Test
	fun `separates liveness from dependency readiness and exposes prometheus metrics`() {
		val liveness = requireNotNull(healthGroups.get("liveness"))
		val readiness = requireNotNull(healthGroups.get("readiness"))

		listOf("mongo", "kafka", "minio").forEach { dependency ->
			assertFalse(liveness.isMember(dependency))
			assertTrue(readiness.isMember(dependency))
		}
		assertEquals(Status.UP, mongoHealthIndicator.health().status)
		assertEquals(Status.UP, kafkaHealthIndicator.health().status)
		assertEquals(Status.UP, minioHealthIndicator.health().status)
		assertTrue(get("/actuator/health/liveness").contains("\"status\":\"UP\""))
		assertTrue(get("/actuator/health/readiness").contains("\"status\":\"UP\""))

		metrics.attemptStarted(retry = true)
		metrics.framesGenerated(4)
		metrics.completed(Duration.ofMillis(250))
		metrics.failed(ProcessingFailureType.STORAGE, Duration.ofMillis(50), terminal = false)
		val prometheus = get("/actuator/prometheus")

		listOf(
			"video_worker_jobs_total",
			"video_worker_processing_duration_seconds",
			"video_worker_frames_generated_total",
			"video_worker_failures_total",
			"video_worker_processing_retries_total",
		).forEach { metricName -> assertTrue(prometheus.contains(metricName), metricName) }
	}

	private fun get(path: String): String {
		val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$managementPort$path")).GET().build()
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
	}
}
