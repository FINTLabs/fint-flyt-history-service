package no.novari.flyt.instance.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "novari.flyt.instance-functionality")
data class InstanceFunctionalityProperties(
    val enabled: Boolean = false,
)
