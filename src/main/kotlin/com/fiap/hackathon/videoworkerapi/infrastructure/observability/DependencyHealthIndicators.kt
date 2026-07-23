package com.fiap.hackathon.videoworkerapi.infrastructure.observability

import com.fiap.hackathon.videoworkerapi.infrastructure.storage.MinioProperties
import com.mongodb.client.MongoClient
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.apache.kafka.clients.admin.Admin
import org.bson.Document
import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component("mongoHealthIndicator")
class MongoDependencyHealthIndicator(
	private val mongoClient: MongoClient,
) : AbstractHealthIndicator() {
	override fun doHealthCheck(builder: Health.Builder) {
		mongoClient.getDatabase(DATABASE_NAME).runCommand(Document(PING_COMMAND, 1))
		builder.up()
	}

	private companion object {
		const val DATABASE_NAME = "admin"
		const val PING_COMMAND = "ping"
	}
}

@Component("kafkaHealthIndicator")
class KafkaDependencyHealthIndicator(
	private val kafkaAdmin: KafkaAdmin,
) : AbstractHealthIndicator() {
	override fun doHealthCheck(builder: Health.Builder) {
		Admin.create(kafkaAdmin.configurationProperties).use { admin ->
			val nodes = admin.describeCluster().nodes()
				.get(kafkaAdmin.operationTimeout.toLong(), TimeUnit.SECONDS)
			check(nodes.isNotEmpty()) { "Kafka cluster has no brokers" }
		}
		builder.up()
	}
}

@Component("minioHealthIndicator")
class MinioDependencyHealthIndicator(
	private val minioClient: MinioClient,
	private val properties: MinioProperties,
) : AbstractHealthIndicator() {
	override fun doHealthCheck(builder: Health.Builder) {
		val bucketsAvailable = listOf(properties.inputBucket, properties.outputBucket).all { bucket ->
			minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
		}
		check(bucketsAvailable) { "Required storage buckets are unavailable" }
		builder.up()
	}
}
