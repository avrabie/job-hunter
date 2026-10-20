package net.s4v3.auth.user

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID
import org.springframework.security.core.userdetails.User as SecurityUser

/**
 * Loads a user by email for form login. The returned [UserDetails] is named after the user's UUID, so the
 * authentication's principal name, and with it the token `sub`, is the UUID and not the email.
 */
@Service
class AccountUserDetailsService(
    private val users: UserRepository,
    passwordEncoder: PasswordEncoder,
) : UserDetailsService {

    /** Stands in for a missing password: a valid hash of a random value nobody knows, so nothing matches it. */
    private val unusablePassword: String = passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID())!!

    override fun loadUserByUsername(email: String): UserDetails {
        val user = users.findByEmail(email.trim()) ?: throw UsernameNotFoundException("Unknown user")
        return SecurityUser(
            user.id.toString(),
            user.password ?: unusablePassword,
            user.enabled, true, true, true,
            user.roleNames.map { SimpleGrantedAuthority("ROLE_$it") },
        )
    }
}
