package no.novari.flyt.instance.config

import no.novari.flyt.history.Application
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.testcontainers.postgresql.PostgreSQLContainer

internal fun instanceJpaContextRunner(
    postgres: PostgreSQLContainer,
    historySchema: String,
): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                FlywayAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                DataJpaRepositoriesAutoConfiguration::class.java,
                TransactionAutoConfiguration::class.java,
            ),
        ).withUserConfiguration(
            HistoryJpaTestPackages::class.java,
            InstanceDatabaseConfiguration::class.java,
            InstanceJpaConfiguration::class.java,
        ).withPropertyValues(
            "spring.datasource.url=${postgres.jdbcUrl}",
            "spring.datasource.username=${postgres.username}",
            "spring.datasource.password=${postgres.password}",
            "spring.datasource.hikari.schema=$historySchema",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        )

@TestConfiguration(proxyBeanMethods = false)
@AutoConfigurationPackage(basePackageClasses = [Application::class])
internal class HistoryJpaTestPackages
