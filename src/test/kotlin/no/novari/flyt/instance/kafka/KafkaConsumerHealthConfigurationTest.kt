package no.novari.flyt.instance.kafka

import no.novari.kafka.health.KafkaConsumersHealthIndicator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityHealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.endpoint.ApiVersion
import org.springframework.boot.actuate.endpoint.SecurityContext
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthContributorRegistry
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.HealthEndpointGroups
import org.springframework.boot.actuate.health.HealthEndpointWebExtension
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.listener.MessageListenerContainer
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(
    classes = [KafkaConsumerHealthConfigurationTest.HealthTestConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.config.location=classpath:application-flyt-logging.yaml"],
)
class KafkaConsumerHealthConfigurationTest {
    @Autowired lateinit var health: HealthEndpoint

    @Autowired lateinit var groups: HealthEndpointGroups

    @Autowired lateinit var registry: HealthContributorRegistry
    private lateinit var web: HealthEndpointWebExtension

    @Autowired lateinit var running: AtomicBoolean

    @Autowired lateinit var connected: AtomicBoolean

    @Autowired lateinit var context: ConfigurableApplicationContext

    @BeforeEach
    fun applicationReady() {
        assertThat(context.environment.getProperty("management.endpoint.health.group.liveness.include"))
            .isEqualTo("livenessState")
        assertThat(groups.names).contains("consumers", "liveness", "readiness")
        assertThat(registry.stream().map { it.name }.toList())
            .contains("kafkaConsumers", "kafkaConnectivity", "livenessState", "readinessState")
        web = HealthEndpointWebExtension(registry, groups, Duration.ofSeconds(10))
        running.set(true)
        connected.set(true)
        AvailabilityChangeEvent.publish(context, LivenessState.CORRECT)
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC)
    }

    @Test
    fun `stopped consumer reports DOWN without failing liveness startup or readiness`() {
        assertThat(probeStatus("consumers")).isEqualTo(200)
        running.set(false)

        assertThat(probeStatus("consumers")).isEqualTo(503)
        assertThat(health.health().status).isEqualTo(Status.DOWN)
        assertThat(probeStatus("liveness")).isEqualTo(200)
        assertThat(probeStatus("readiness")).isEqualTo(200)
    }

    @Test
    fun `liveness still detects a broken application independently of consumer health`() {
        AvailabilityChangeEvent.publish(context, LivenessState.BROKEN)

        assertThat(probeStatus("liveness")).isEqualTo(503)
        assertThat(probeStatus("consumers")).isEqualTo(200)
    }

    @Test
    fun `readiness retains Kafka connectivity checks without affecting liveness`() {
        connected.set(false)

        assertThat(probeStatus("readiness")).isEqualTo(503)
        assertThat(probeStatus("liveness")).isEqualTo(200)
        assertThat(probeStatus("consumers")).isEqualTo(200)
    }

    @Test
    fun `base and every generated overlay use liveness for startup rather than aggregate consumer health`() {
        val base = readYaml(Path.of("kustomize/base/flais.yaml"))
        val probes = (base.getValue("spec") as Map<*, *>)["probes"] as Map<*, *>
        assertThat((probes["startup"] as Map<*, *>)["path"])
            .isEqualTo((probes["liveness"] as Map<*, *>)["path"])
            .isEqualTo("/actuator/health/liveness")
        val overlays =
            Files.walk(Path.of("kustomize/overlays")).use { paths ->
                paths.filter { it.fileName.toString() == "kustomization.yaml" }.toList()
            }
        assertThat(overlays).isNotEmpty()
        overlays.forEach { overlay ->
            val patches = readYaml(overlay).getValue("patches") as List<*>
            val operations =
                patches.flatMap {
                    val patch = (it as Map<*, *>)["patch"] as? String
                    if (patch == null) emptyList() else Yaml().load<List<Map<String, Any>>>(patch)
                }
            val startup = operations.single { it["path"] == "/spec/probes/startup/path" }["value"]
            val liveness = operations.single { it["path"] == "/spec/probes/liveness/path" }["value"]
            assertThat(startup).describedAs(overlay.toString()).isEqualTo(liveness)
            assertThat(startup as String).endsWith("/actuator/health/liveness")
        }
    }

    private fun probeStatus(group: String): Int =
        web.health(ApiVersion.V3, WebServerNamespace.SERVER, SecurityContext.NONE, false, group).status

    private fun readYaml(path: Path): Map<String, Any> = Files.newBufferedReader(path).use { Yaml().load(it) }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(
        HealthEndpointAutoConfiguration::class,
        ApplicationAvailabilityAutoConfiguration::class,
        AvailabilityHealthContributorAutoConfiguration::class,
        AvailabilityProbesAutoConfiguration::class,
    )
    class HealthTestConfiguration {
        @Bean fun running() = AtomicBoolean(true)

        @Bean fun connected() = AtomicBoolean(true)

        @Bean fun consumer(running: AtomicBoolean): MessageListenerContainer =
            mock<MessageListenerContainer>().also {
                whenever(it.isAutoStartup).thenReturn(true)
                whenever(it.isRunning).thenAnswer { running.get() }
                whenever(it.listenerId).thenReturn("instanceReceivedEventConsumer")
                doAnswer {
                    running.set(true)
                    null
                }.whenever(it).start()
                doAnswer { invocation ->
                    running.set(false)
                    invocation.getArgument<Runnable>(0).run()
                    null
                }.whenever(it).stop(any<Runnable>())
            }

        @Bean fun kafkaConsumersHealthIndicator(containers: ObjectProvider<MessageListenerContainer>) =
            KafkaConsumersHealthIndicator(containers)

        @Bean fun kafkaConnectivityHealthIndicator(connected: AtomicBoolean): HealthIndicator =
            HealthIndicator {
                if (connected.get()) Health.up().build() else Health.down().build()
            }
    }
}
