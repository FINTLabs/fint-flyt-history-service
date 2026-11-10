package no.novari.flyt.instance.config

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InstanceDatabaseProperties::class)
class InstanceDatabaseConfiguration {
    @Bean
    fun instanceSchemaNamingCustomizer(
        properties: InstanceDatabaseProperties,
        dataSourceProperties: DataSourceProperties,
    ): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { hibernateProperties ->
            hibernateProperties["hibernate.physical_naming_strategy"] =
                InstanceSchemaNamingStrategy(instanceSchema(properties.schema, dataSourceProperties.username))
        }
}

private const val OWN_SCHEMA = "fint_flyt_history_service_db"
private const val INSTANCE_SERVICE_SCHEMA = "fint_flyt_instance_service_db"

/**
 * The database user and its schema follow `<tenant>_<application>_db`, so the instance-service schema shares the
 * tenant prefix with our own. Returns null when it cannot be derived and is not configured.
 */
internal fun instanceSchema(
    configuredSchema: String,
    databaseUsername: String?,
): String? =
    configuredSchema.ifBlank {
        databaseUsername?.takeIf { it.endsWith(OWN_SCHEMA) }?.removeSuffix(OWN_SCHEMA)?.plus(INSTANCE_SERVICE_SCHEMA)
    }
