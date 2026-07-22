package com.fiap.hackathon.videoworkerapi.infrastructure.processing

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("app.ffmpeg")
data class FfmpegProperties(
	@field:NotBlank val executable: String = "ffmpeg",
	val timeout: Duration = Duration.ofMinutes(5),
	@field:Positive val framesPerSecond: Int = 1,
	@field:Positive val maxFrames: Int = 10_000,
) {
	init {
		require(!timeout.isZero && !timeout.isNegative) { "FFmpeg timeout must be positive" }
	}
}
