package net.s4v3.auth.password

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class PasswordResetRequest(@field:NotBlank val email: String?)

data class PasswordResetConfirmRequest(
    @field:NotBlank val token: String?,
    @field:NotBlank @field:ValidPassword val newPassword: String?,
) {
    override fun toString() = "PasswordResetConfirmRequest(token=***, newPassword=***)"
}

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String?,
    @field:NotBlank @field:ValidPassword val newPassword: String?,
) {
    override fun toString() = "ChangePasswordRequest(currentPassword=***, newPassword=***)"
}

@RestController
@RequestMapping("/api")
class PasswordController(private val passwords: PasswordService) {

    /** Always 200 with the same body, whether or not the email has an account. */
    @PostMapping("/password-reset/request")
    fun request(@Valid @RequestBody body: PasswordResetRequest): Map<String, String> {
        passwords.requestReset(body.email!!)
        return mapOf("status" to "ok")
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirm(@Valid @RequestBody body: PasswordResetConfirmRequest) {
        passwords.confirmReset(body.token!!, body.newPassword!!)
    }

    /** Acts only on the user named by the access token's `sub`; the request carries no user identifier. */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun change(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody body: ChangePasswordRequest) {
        passwords.change(UUID.fromString(jwt.subject), body.currentPassword!!, body.newPassword!!)
    }
}
