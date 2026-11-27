package no.novari.flyt.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource

class AuthorizationClientProfileConfigurationTest {
    @Test
    fun `authorization client profile configures the audit lookup and the FlytAccess client`() {
        val properties =
            YamlPropertiesFactoryBean()
                .apply { setResources(ClassPathResource("application-flyt-authorization-client.yaml")) }
                .`object`

        assertThat(properties)
            .containsEntry(
                "novari.flyt.audit.authorization.base-url",
                "http://fint-flyt-authorization-service:8080\${server.servlet.context-path:}",
            ).containsEntry(
                // Its presence turns on FlytAccess in flyt-security-starter.
                "spring.security.oauth2.client.registration.authorization-service.client-id",
                "\${fint.flyt.authorization.sso.client-id}",
            )
    }
}
