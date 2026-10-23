package no.novari.flyt.history.repository

import jakarta.persistence.EntityManagerFactory
import no.novari.flyt.history.ClockConfiguration
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.InstanceRepository
import no.novari.flyt.instance.InstanceService
import no.novari.flyt.instance.config.instanceJpaContextRunner
import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.entities.InstanceObject
import no.novari.flyt.instance.model.entities.InstanceObjectCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
class InstanceEntitiesNotRegisteredTest {
    @Test
    fun `missing flag keeps instance entities and repository inactive without instance schema configuration`() {
        contextRunner().run { context ->
            assertThat(context.environment.getProperty("novari.flyt.instance-functionality.enabled")).isNull()
            assertHistoryOnly(context)
        }
    }

    @Test
    fun `false flag keeps instance entities and repository inactive without instance schema configuration`() {
        contextRunner()
            .withPropertyValues("novari.flyt.instance-functionality.enabled=false")
            .run { context -> assertHistoryOnly(context) }
    }

    private fun contextRunner() =
        instanceJpaContextRunner(postgreSQLContainer, "disabled_instance_history")
            .withUserConfiguration(
                InstanceRegistrationService::class.java,
                InstanceReceivedOffsetRepository::class.java,
                InstanceService::class.java,
                InstanceMappingService::class.java,
                ClockConfiguration::class.java,
            )

    private fun assertHistoryOnly(context: AssertableApplicationContext) {
        assertThat(context).hasNotFailed()
        assertThat(context).hasSingleBean(EntityManagerFactory::class.java)
        assertThat(context).hasSingleBean(PlatformTransactionManager::class.java)
        assertThat(context).hasSingleBean(EventRepository::class.java)
        assertThat(context).doesNotHaveBean(InstanceRepository::class.java)
        assertThat(context).doesNotHaveBean(InstanceRegistrationService::class.java)
        assertThat(context).doesNotHaveBean(InstanceReceivedOffsetRepository::class.java)
        assertThat(context).doesNotHaveBean(InstanceService::class.java)
        assertThat(context).doesNotHaveBean(InstanceMappingService::class.java)
        assertThat(context.environment.getProperty("novari.flyt.instance.database.schema")).isNull()
        val entityTypes =
            context
                .getBean<EntityManagerFactory>()
                .metamodel.entities
                .map { it.javaType }
        assertThat(entityTypes).contains(EventEntity::class.java)
        assertThat(entityTypes).doesNotContain(InstanceObject::class.java, InstanceObjectCollection::class.java)
        assertThat(context.getBean<EventRepository>().count()).isZero()
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }
}
