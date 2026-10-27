package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.apache.kafka.clients.admin.Admin
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
    ): Supplier<Admin> = kafkaAdminSupplier(producerConfig, properties.metadataTimeout)
}
