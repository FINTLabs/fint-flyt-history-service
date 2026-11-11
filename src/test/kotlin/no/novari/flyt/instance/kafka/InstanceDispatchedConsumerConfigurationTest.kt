package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.InstanceCleanupService
import no.novari.flyt.instance.InstanceDeletionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class InstanceDispatchedConsumerConfigurationTest {
    @Test
    fun `deletion functionality creates no beans or Kafka collaborators before cutover`() {
        val runner =
            ApplicationContextRunner().withUserConfiguration(
                InstanceDispatchedConsumerConfiguration::class.java,
                InstanceDeletedEventProducerService::class.java,
                InstanceDeletionService::class.java,
                InstanceCleanupService::class.java,
            )
        for (properties in listOf(emptyArray(), arrayOf("novari.flyt.instance-functionality.enabled=false"))) {
            runner.withPropertyValues(*properties).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(InstanceDispatchedConsumerConfiguration::class.java)
                assertThat(context).doesNotHaveBean(InstanceDeletedEventProducerService::class.java)
                assertThat(context).doesNotHaveBean(InstanceDeletionService::class.java)
                assertThat(context).doesNotHaveBean(InstanceCleanupService::class.java)
            }
        }
    }
}
