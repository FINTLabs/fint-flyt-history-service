package no.novari.flyt.instance.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.novari.flyt.instance.kafka.InstanceRegisteredEventProducerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.EnableScheduling

class InstanceRegisteredOutboxConfigurationTest {
    private val components =
        arrayOf(
            InstanceRegisteredOutboxConfiguration::class.java,
            InstanceRegisteredOutboxCodec::class.java,
            InstanceRegisteredOutboxRepository::class.java,
            InstanceRegisteredOutboxService::class.java,
            InstanceRegisteredOutboxPublisher::class.java,
        )

    @ParameterizedTest
    @ValueSource(strings = ["", "false"])
    fun `disabled feature creates no outbox publisher dependencies or Kafka clients`(flag: String) {
        val runner =
            ApplicationContextRunner().withUserConfiguration(
                *components,
                InstanceRegisteredOutboxKafkaConfiguration::class.java,
                InstanceRegisteredEventProducerService::class.java,
            )
        val configured =
            if (flag.isEmpty()) {
                runner
            } else {
                runner.withPropertyValues(
                    "novari.flyt.instance-functionality.enabled=$flag",
                )
            }
        configured.run {
            assertThat(it).hasNotFailed()
            assertThat(it).doesNotHaveBean(InstanceRegisteredOutboxPublisher::class.java)
            assertThat(it).doesNotHaveBean(InstanceRegisteredOutboxRepository::class.java)
            assertThat(it).doesNotHaveBean(InstanceRegisteredOutboxProperties::class.java)
            assertThat(it).doesNotHaveBean(KafkaTemplate::class.java)
        }
    }

    @Test
    fun `enabled feature binds configuration and creates the outbox components`() {
        ApplicationContextRunner()
            .withUserConfiguration(*components)
            .withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "novari.flyt.instance.outbox.max-records-per-run=7",
            ).withBean(ObjectMapper::class.java, { outboxObjectMapper() })
            .withBean(JdbcTemplate::class.java, { mock<JdbcTemplate>() })
            .withBean(
                InstanceRegisteredEventProducerService::class.java,
                { mock<InstanceRegisteredEventProducerService>() },
            ).withBean(MeterRegistry::class.java, { SimpleMeterRegistry() })
            .run {
                assertThat(it).hasNotFailed()
                assertThat(it).hasSingleBean(InstanceRegisteredOutboxPublisher::class.java)
                assertThat(it.getBean(InstanceRegisteredOutboxProperties::class.java).maxRecordsPerRun).isEqualTo(7)
            }
    }

    @Test
    fun `enabled feature rejects a claim shorter than the publication timeout`() {
        ApplicationContextRunner()
            .withUserConfiguration(InstanceRegisteredOutboxConfiguration::class.java)
            .withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "novari.flyt.instance.outbox.claim-duration=1s",
            ).run { assertThat(it).hasFailed() }
    }

    @Test
    fun `dedicated outbox scheduler does not replace the default scheduler`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    TaskExecutionAutoConfiguration::class.java,
                    TaskSchedulingAutoConfiguration::class.java,
                ),
            ).withUserConfiguration(
                InstanceRegisteredOutboxConfiguration::class.java,
                SchedulingConfiguration::class.java,
            ).withPropertyValues("novari.flyt.instance-functionality.enabled=true")
            .run {
                assertThat(it).hasNotFailed()
                assertThat(it).hasBean("taskScheduler")
                assertThat(it.getBean("instanceRegisteredOutboxScheduler")).isNotSameAs(it.getBean("taskScheduler"))
            }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableScheduling
    class SchedulingConfiguration
}
