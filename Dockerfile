FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache ffmpeg \
	&& addgroup -S -g 10001 app \
	&& adduser -S -D -H -u 10001 -G app app \
	&& mkdir -p /tmp/video-worker \
	&& chown app:app /tmp/video-worker

WORKDIR /app
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY --chown=app:app ${JAR_FILE} app.jar

ENV PROCESSING_TEMP_DIRECTORY=/tmp/video-worker

USER 10001:10001
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
