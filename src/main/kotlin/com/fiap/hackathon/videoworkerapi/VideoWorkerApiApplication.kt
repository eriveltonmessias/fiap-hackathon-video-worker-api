package com.fiap.hackathon.videoworkerapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class VideoWorkerApiApplication

fun main(args: Array<String>) {
	runApplication<VideoWorkerApiApplication>(*args)
}
