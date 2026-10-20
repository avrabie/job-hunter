package net.s4v3.auth.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.s4v3.auth.password.ValidPassword

data class RegistrationRequest(
    @field:NotBlank @field:Email @field:Size(max = 254) val email: String?,
    @field:NotBlank @field:ValidPassword val password: String?,
    @field:Size(max = 100) val firstName: String? = null,
    @field:Size(max = 100) val lastName: String? = null,
) {
    override fun toString() = "RegistrationRequest(email=$email, password=***)"
}

data class RegistrationResponse(val email: String)
