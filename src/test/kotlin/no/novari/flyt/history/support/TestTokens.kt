package no.novari.flyt.history.support

import no.novari.flyt.security.UserRole
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant
import java.util.UUID

/**
 * Tokens for `@WebMvcTest`s that import `FlytSecurityAutoConfiguration`. Only decoding is stubbed: the token value
 * names the caller, and the library's validators and role policy run as in production.
 */
object TestTokens {
    const val INTERNAL_CLIENT_ID = "test-internal-client"
    const val USER_AUDIENCE = "test-frontend"
    const val TENANT_ORG_ID = "fintlabs.no"

    /** For `@TestPropertySource`. */
    const val ORG_PROPERTY = "fint.org-id=$TENANT_ORG_ID"
    const val INTERNAL_CLIENT_PROPERTY = "fint.flyt.authorization.sso.client-id=$INTERNAL_CLIENT_ID"
    const val USER_AUDIENCE_PROPERTY = "novari.flyt.security.user.audience=$USER_AUDIENCE"

    // "~" because a bearer token may not contain ":".
    fun client(clientId: String = INTERNAL_CLIENT_ID) = "client~$clientId"

    /** A user token; [role] `null` gives a user without FLYT roles. */
    fun user(
        role: UserRole? = UserRole.USER,
        organizationId: String = TENANT_ORG_ID,
        objectIdentifier: UUID = UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"),
    ) = "user~$organizationId~${role?.name ?: "NONE"}~$objectIdentifier"

    fun stub(jwtDecoder: JwtDecoder) {
        whenever(jwtDecoder.decode(any())).thenAnswer { decode(it.getArgument(0)) }
    }

    private fun decode(token: String): Jwt {
        val parts = token.split("~")
        val builder =
            Jwt
                .withTokenValue(token)
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", "end-user-profile fint-api")
        return when (parts.first()) {
            "client" -> {
                builder.subject(parts[1]).audience(listOf(parts[1])).build()
            }

            "user" -> {
                builder
                    .subject("opaque-user-id")
                    .audience(listOf(USER_AUDIENCE))
                    .claim("organizationid", parts[1])
                    .claim("roles", listOfNotNull(UserRole.entries.find { it.name == parts[2] }?.claimValue))
                    .claim("objectidentifier", parts[3])
                    .build()
            }

            else -> {
                throw BadJwtException("Invalid token")
            }
        }
    }
}
