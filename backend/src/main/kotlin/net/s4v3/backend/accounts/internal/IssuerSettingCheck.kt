package net.s4v3.backend.accounts.internal

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Profiles

/**
 * Fails startup early, with a message that names the setting, when the token issuer is not configured.
 * Without it the backend would start but could not validate any token. The `test` profile is exempt:
 * tests either never validate tokens or point the setting at a mock issuer.
 */
internal class IssuerSettingCheck : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (environment.acceptsProfiles(Profiles.of("test"))) return
        if (environment.getProperty(ISSUER_PROPERTY).isNullOrBlank()) {
            throw IllegalStateException(
                "Missing required setting '$ISSUER_PROPERTY' (environment variable SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI): " +
                    "the issuer URI of the auth service whose tokens this backend accepts.",
            )
        }
    }

    private companion object {
        const val ISSUER_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri"
    }
}
