package no.novari.flyt.instance.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InstanceFunctionalityProperties::class)
class InstanceFunctionalityConfiguration
