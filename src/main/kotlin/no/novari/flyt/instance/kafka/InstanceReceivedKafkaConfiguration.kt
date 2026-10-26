package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Supplier

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
@EnableConfigurationProperties(InstanceReceivedKafkaProperties::class)
class InstanceReceivedKafkaConfiguration {
    @Bean(defaultCandidate = false)
    fun instanceReceivedMetadataAdminSupplier(
        producerConfig: ProducerConfig,
        properties: InstanceReceivedKafkaProperties,
    ): Supplier<Admin> {
        val configuration =
            producerConfig.originals().filterKeys { it in AdminClientConfig.configNames() }.toMutableMap()
        val timeoutMs = properties.metadataTimeout.toMillis().toInt()
        configuration[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = timeoutMs
        configuration[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = timeoutMs
        return Supplier { Admin.create(configuration) }
    }
}
