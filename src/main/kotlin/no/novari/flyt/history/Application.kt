package no.novari.flyt.history

import no.novari.flyt.audit.config.EnableFlytAuditing
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableFlytAuditing
@SpringBootApplication(scanBasePackages = ["no.novari"])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
