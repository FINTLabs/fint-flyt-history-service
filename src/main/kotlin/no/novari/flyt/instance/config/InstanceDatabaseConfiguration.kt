package no.novari.flyt.instance.config

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InstanceDatabaseProperties::class)
class InstanceDatabaseConfiguration {
    @Bean
    fun instanceSchemaNamingCustomizer(properties: InstanceDatabaseProperties): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { hibernateProperties ->
            hibernateProperties["hibernate.physical_naming_strategy"] = InstanceSchemaNamingStrategy(properties)
        }
}
