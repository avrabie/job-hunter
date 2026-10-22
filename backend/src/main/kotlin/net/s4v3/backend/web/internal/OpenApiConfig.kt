package net.s4v3.backend.web.internal

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The header of the generated API description. Every operation needs a bearer access token from the auth service
 * (`/auth`); the documentation itself is not part of the description. Examples must stay synthetic.
 */
@Configuration
internal class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info().title("Job Hunter API").version("v1")
                    .description("REST API of the Job Hunter backend. Send an access token from the auth service as a bearer token."),
            )
            .components(
                Components().addSecuritySchemes(
                    BEARER,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER))

    companion object {
        const val BEARER = "bearer"
    }
}
