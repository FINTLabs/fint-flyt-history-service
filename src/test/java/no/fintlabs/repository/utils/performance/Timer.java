package no.fintlabs.repository.utils.performance;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class Timer {

    private final Long startTime;

    private Timer(Long startTime) {
        this.startTime = startTime;
    }

    public static Timer start() {
        return new Timer(System.nanoTime());
    }

    public Duration getElapsedTime() {
        return Duration.ofNanos(System.nanoTime() - startTime);
    }

}
