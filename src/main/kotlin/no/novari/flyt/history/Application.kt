package no.novari.flyt.history

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
// The libraries are auto-configured, so only the app's own packages are scanned.
@SpringBootApplication(scanBasePackages = ["no.novari.flyt.history", "no.novari.flyt.instance"])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
