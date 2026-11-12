package no.novari.flyt.instance.config

import no.novari.flyt.history.ClockConfiguration
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.InstanceCleanupService
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.InstanceRepository
import no.novari.flyt.instance.InstanceService
import no.novari.flyt.instance.kafka.InstanceDeletedEventProducerService
import no.novari.flyt.instance.model.InstanceMappingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
class InstanceFunctionalityDisabledTest {
    @Test
    fun `missing flag keeps dispatched deletion active and everything else inactive`() {
        contextRunner().run { context ->
            assertThat(context.environment.getProperty("novari.flyt.instance-functionality.enabled")).isNull()
            assertDeletionOnly(context)
        }
    }

    @Test
    fun `false flag keeps dispatched deletion active and everything else inactive`() {
        contextRunner()
            .withPropertyValues("novari.flyt.instance-functionality.enabled=false")
            .run(::assertDeletionOnly)
    }

    private fun contextRunner() =
        instanceJpaContextRunner(postgreSQLContainer, "disabled_instance_history")
            .withPropertyValues("novari.flyt.instance.database.schema=mapping_test_instance")
            .withBean(InstanceDeletedEventProducerService::class.java, { mock<InstanceDeletedEventProducerService>() })
            .withUserConfiguration(
                InstanceRegistrationService::class.java,
                InstanceReceivedOffsetRepository::class.java,
                InstanceCleanupService::class.java,
                InstanceService::class.java,
                InstanceMappingService::class.java,
                InstanceDeletionService::class.java,
                ClockConfiguration::class.java,
            )

    private fun assertDeletionOnly(context: AssertableApplicationContext) {
        assertThat(context).hasNotFailed()
        assertThat(context).hasSingleBean(EventRepository::class.java)
        assertThat(context).hasSingleBean(InstanceRepository::class.java)
        assertThat(context).hasSingleBean(InstanceDeletionService::class.java)
        assertThat(context).doesNotHaveBean(InstanceRegistrationService::class.java)
        assertThat(context).doesNotHaveBean(InstanceReceivedOffsetRepository::class.java)
        assertThat(context).doesNotHaveBean(InstanceCleanupService::class.java)
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17").withInitScript("db/instance-fixture/init.sql")
    }
}
