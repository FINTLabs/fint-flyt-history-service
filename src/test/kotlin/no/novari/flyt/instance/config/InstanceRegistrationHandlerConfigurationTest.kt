package no.novari.flyt.instance.config

import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceRegistrationHandler
import no.novari.flyt.instance.InstanceRegistrationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock

class InstanceRegistrationHandlerConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(
                InstanceRegistrationHandler::class.java,
                InstanceReceivedOffsetRepository::class.java,
            )

    @Test
    fun `missing flag does not register the handler or require its dependencies`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(InstanceRegistrationHandler::class.java)
            assertThat(context).doesNotHaveBean(InstanceReceivedOffsetRepository::class.java)
        }
    }

    @Test
    fun `false flag does not register the handler or require its dependencies`() {
        contextRunner.withPropertyValues("novari.flyt.instance-functionality.enabled=false").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(InstanceRegistrationHandler::class.java)
            assertThat(context).doesNotHaveBean(InstanceReceivedOffsetRepository::class.java)
        }
    }

    @Test
    fun `true flag registers the handler with the configured dependencies`() {
        contextRunner
            .withPropertyValues(
                "novari.flyt.instance-functionality.enabled=true",
                "fint.application-id=registration-test-app",
            ).withBean(InstanceRegistrationService::class.java, { mock<InstanceRegistrationService>() })
            .withBean(InstanceErrorEventService::class.java, { mock<InstanceErrorEventService>() })
            .withBean(Clock::class.java, { Clock.systemUTC() })
            .withBean(JdbcTemplate::class.java, { mock<JdbcTemplate>() })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(InstanceRegistrationHandler::class.java)
                assertThat(context).hasSingleBean(InstanceReceivedOffsetRepository::class.java)
            }
    }
}
