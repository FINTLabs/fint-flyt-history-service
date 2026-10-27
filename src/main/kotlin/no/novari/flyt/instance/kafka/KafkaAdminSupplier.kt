package no.novari.flyt.instance.kafka

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.ProducerConfig
import java.time.Duration
import java.util.function.Supplier

internal fun kafkaAdminSupplier(
    producerConfig: ProducerConfig,
    timeout: Duration,
): Supplier<Admin> {
    val configuration = producerConfig.originals().filterKeys { it in AdminClientConfig.configNames() }.toMutableMap()
    configuration[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = timeout.toMillis().toInt()
    configuration[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = timeout.toMillis().toInt()
    return Supplier { Admin.create(configuration) }
}
