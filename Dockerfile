FROM gradle:7.5-jdk17 AS builder
WORKDIR /home/gradle
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM gcr.io/distroless/java17
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError"
COPY --from=builder /home/gradle/build/libs/*.jar /data/app.jar
CMD ["/data/app.jar"]
