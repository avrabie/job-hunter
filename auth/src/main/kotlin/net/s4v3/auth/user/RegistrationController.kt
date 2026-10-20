package net.s4v3.auth.user

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class RegistrationController(private val registration: UserRegistrationService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegistrationRequest): RegistrationResponse {
        val user = registration.register(request.email!!, request.password!!, request.firstName, request.lastName)
        return RegistrationResponse(user.email)
    }
}
