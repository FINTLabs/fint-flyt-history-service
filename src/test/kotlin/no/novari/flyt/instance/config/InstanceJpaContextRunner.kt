package no.novari.flyt.instance.config

import no.novari.flyt.history.Application
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.testcontainers.containers.PostgreSQLContainer

internal fun instanceJpaContextRunner(
    postgres: PostgreSQLContainer<*>,
    historySchema: String,
): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                FlywayAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                JpaRepositoriesAutoConfiguration::class.java,
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
