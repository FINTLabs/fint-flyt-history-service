package no.fintlabs.repository.utils.performance;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class Timer {

    private Long startTime;

    public void start() {
        startTime = System.nanoTime();
    }

    public Duration getElapsedTime() {
        return Duration.ofNanos(System.nanoTime() - startTime);
    }

}
