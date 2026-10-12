package no.novari.flyt.instance.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "novari.flyt.instance.database")
data class InstanceDatabaseProperties(
    val schema: String = "",
)
