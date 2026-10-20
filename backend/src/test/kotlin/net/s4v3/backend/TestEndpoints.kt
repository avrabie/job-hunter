package net.s4v3.backend

import net.s4v3.backend.accounts.CurrentUser
import org.springframework.boot.test.context.TestComponent
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Endpoints that exist only in tests. Not component-scanned; import explicitly with `@Import`. */
@TestComponent
@RestController
class TestEndpoints(private val currentUser: CurrentUser) {

    @GetMapping("/api/test/me")
    fun me() = currentUser.id()

    @GetMapping("/api/test/ping")
    fun ping(authentication: Authentication) = authentication.name

    @GetMapping("/api/test/thread")
    fun thread() = Thread.currentThread().isVirtual.toString()

    @GetMapping("/api/test/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun admin() = "admin"
}
