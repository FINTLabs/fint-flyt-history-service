package no.novari.flyt.history

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(scanBasePackages = ["no.novari"])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
