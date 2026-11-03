package no.novari.flyt.instance.config

import no.novari.flyt.instance.kafka.InstanceRetryProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
@EnableConfigurationProperties(InstanceRetryProperties::class)
class InstanceRetryConfiguration
