package no.novari.flyt.instance.outbox

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
@EnableConfigurationProperties(InstanceRegisteredOutboxProperties::class)
class InstanceRegisteredOutboxConfiguration {
    @Bean(defaultCandidate = false)
    fun instanceRegisteredOutboxScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("instance-outbox-")
        }
}
