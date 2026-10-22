package net.s4v3.auth.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("app")
data class AuthProperties(
    // Nullable with a default so that a missing value fails validation with the property name, not a bare NPE.
    /** Fixed public issuer, e.g. https://s4v3.net/auth. Independent of the request's host. */
    @field:NotBlank val issuerUri: String? = null,
    /** Base URL of the UI, used to build password reset links. */
    @field:NotBlank val uiBaseUrl: String? = null,
    @field:Valid val spa: Spa = Spa(),
    val google: Google = Google(),
    /**
     * Emails whose Google sign-in also gets the ADMIN role (compared ignoring case). Empty grants nothing. Local-password
     * accounts never get ADMIN from this list, because their email is not verified.
     */
    val adminEmails: List<String> = emptyList(),
) {
    data class Spa(
        @field:NotEmpty val redirectUris: List<String> = emptyList(),
        @field:NotEmpty val postLogoutRedirectUris: List<String> = emptyList(),
    )

    data class Google(val clientId: String? = null, val clientSecret: String? = null) {
        val configured: Boolean get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    }
}
