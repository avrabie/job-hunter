package net.s4v3.auth.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("app")
data class AuthProperties(
    /** Fixed public issuer, e.g. https://s4v3.net/auth. Independent of the request's host. */
    @field:NotBlank val issuerUri: String,
    /** Base URL of the UI, used to build password reset links. */
    @field:NotBlank val uiBaseUrl: String,
    @field:Valid val spa: Spa,
    val google: Google = Google(),
) {
    data class Spa(
        @field:NotEmpty val redirectUris: List<String>,
        @field:NotEmpty val postLogoutRedirectUris: List<String>,
    )

    data class Google(val clientId: String? = null, val clientSecret: String? = null) {
        val configured: Boolean get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    }
}
