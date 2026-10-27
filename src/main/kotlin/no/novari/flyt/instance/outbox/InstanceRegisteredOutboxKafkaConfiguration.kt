package no.novari.flyt.instance.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.kafkaAdminSupplier
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.health.ProducerFailureTracker
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.ProducerListener
import org.springframework.kafka.support.serializer.JsonSerializer
import java.util.function.Supplier

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
class InstanceRegisteredOutboxKafkaConfiguration {
    @Bean(destroyMethod = "destroy", defaultCandidate = false)
    fun instanceRegisteredOutboxProducerFactory(
        producerConfig: ProducerConfig,
        objectMapper: ObjectMapper,
        properties: InstanceRegisteredOutboxProperties,
    ): DefaultKafkaProducerFactory<String, InstanceObjectDto> {
        val configuration = producerConfig.originals().toMutableMap()
        val timeoutMs = properties.sendTimeout.toMillis()
        configuration[ProducerConfig.ACKS_CONFIG] = "all"
        configuration[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configuration[ProducerConfig.RETRIES_CONFIG] = Int.MAX_VALUE
        val inFlight =
            configuration[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION]?.toString()?.toIntOrNull() ?: 5
        configuration[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = inFlight.coerceIn(1, 5)
        configuration[ProducerConfig.MAX_BLOCK_MS_CONFIG] = timeoutMs
        configuration[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = timeoutMs.toInt()
        configuration[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = (timeoutMs / 3).coerceAtLeast(1).toInt()
        configuration[ProducerConfig.LINGER_MS_CONFIG] = 0
        val interceptors =
            when (val configured = configuration[ProducerConfig.INTERCEPTOR_CLASSES_CONFIG]) {
                is List<*> -> configured.map { if (it is Class<*>) it.name else it.toString() }
                null -> emptyList()
                else -> configured.toString().split(',').map(String::trim)
            }
        configuration[ProducerConfig.INTERCEPTOR_CLASSES_CONFIG] =
            interceptors.filter { it.isNotEmpty() && it != OriginHeaderProducerInterceptor::class.java.name }
        return DefaultKafkaProducerFactory(
            configuration,
            StringSerializer(),
            JsonSerializer(objectMapper.constructType(InstanceObjectDto::class.java), objectMapper),
        )
    }

    @Bean(defaultCandidate = false)
    fun instanceRegisteredOutboxKafkaTemplate(
        @Qualifier("instanceRegisteredOutboxProducerFactory")
        instanceRegisteredOutboxProducerFactory: DefaultKafkaProducerFactory<String, InstanceObjectDto>,
        producerFailureTracker: ProducerFailureTracker? = null,
    ): KafkaTemplate<String, InstanceObjectDto> =
        KafkaTemplate(instanceRegisteredOutboxProducerFactory).apply {
            setProducerListener(
                object : ProducerListener<String, InstanceObjectDto> {
                    override fun onError(
                        producerRecord: ProducerRecord<String, InstanceObjectDto>,
                        recordMetadata: RecordMetadata?,
                        exception: Exception,
                    ) {
                        producerFailureTracker?.onError(
                            ProducerRecord<Any, Any>(producerRecord.topic(), null),
                            recordMetadata,
                            IllegalStateException(exception.javaClass.name),
                        )
                    }
                },
            )
        }

    @Bean(defaultCandidate = false)
    fun instanceRegisteredOutboxAdminSupplier(
        producerConfig: ProducerConfig,
        properties: InstanceRegisteredOutboxProperties,
    ): Supplier<Admin> = kafkaAdminSupplier(producerConfig, properties.sendTimeout)
}
