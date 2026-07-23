FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache ffmpeg \
	&& addgroup -S -g 10001 app \
	&& adduser -S -D -H -u 10001 -G app app \
	&& mkdir -p /tmp/video-worker \
	&& chown app:app /tmp/video-worker

WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/*-SNAPSHOT.jar app.jar

ENV PROCESSING_TEMP_DIRECTORY=/tmp/video-worker

USER 10001:10001
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
