package no.novari.flyt.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource

class AuthorizationClientProfileConfigurationTest {
    @Test
    fun `authorization client profile configures audit actor display lookup`() {
        val properties =
            YamlPropertiesFactoryBean()
                .apply { setResources(ClassPathResource("application-flyt-authorization-client.yaml")) }
                .`object`

        assertThat(properties)
            .containsEntry(
                "novari.flyt.audit.authorization.base-url",
                "http://fint-flyt-authorization-service:8080\${server.servlet.context-path:}",
            ).containsEntry(
                "novari.flyt.web-resource-server.security.authorization.base-url",
                "http://fint-flyt-authorization-service:8080\${server.servlet.context-path:}",
            )
    }
}
