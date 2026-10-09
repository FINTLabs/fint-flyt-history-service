package no.novari.flyt.history.repository

import jakarta.persistence.EntityManagerFactory
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.instance.model.entities.InstanceObject
import no.novari.flyt.instance.model.entities.InstanceObjectCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig::class)
@TestPropertySource(properties = ["novari.flyt.instance-functionality.enabled=false"])
class InstanceEntitiesNotRegisteredTest {
    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `instance entities are absent from the active JPA metamodel`() {
        val entityTypes =
            entityManagerFactory.metamodel.entities
                .map { it.javaType }
                .toSet()

        assertThat(entityTypes)
            .doesNotContain(InstanceObject::class.java, InstanceObjectCollection::class.java)
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }
    }
}
