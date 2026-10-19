package no.novari.flyt.instance.config

import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.InstanceRepository
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration(proxyBeanMethods = false)
@ConditionalOnInstanceFunctionality
@EntityScan(basePackages = ["no.novari.flyt.history", "no.novari.flyt.instance"])
@EnableJpaRepositories(
    basePackageClasses = [EventRepository::class, InstanceRepository::class],
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager",
)
class InstanceJpaConfiguration
