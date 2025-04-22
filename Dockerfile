FROM gradle:7.2-jdk17 as builder
USER root
COPY . .
RUN gradle --no-daemon build

FROM gcr.io/distroless/java17
ENV TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
COPY --from=builder /home/gradle/build/libs/*.jar /data/app.jar
CMD ["/data/app.jar"]
