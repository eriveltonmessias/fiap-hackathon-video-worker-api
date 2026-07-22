package com.fiap.hackathon.videoworkerapi.domain.processing

enum class ProcessingJobStatus {
	RECEIVED,
	PROCESSING,
	GENERATING_FRAMES,
	COMPRESSING,
	UPLOADING_RESULT,
	COMPLETED,
	FAILED,
}
