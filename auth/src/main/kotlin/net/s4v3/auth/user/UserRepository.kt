package net.s4v3.auth.user

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : CrudRepository<User, UUID> {

    @Query("SELECT * FROM users WHERE lower(email) = lower(:email)")
    fun findByEmail(@Param("email") email: String): User?

    @Query("SELECT * FROM users WHERE provider = :provider AND provider_id = :providerId")
    fun findByProvider(@Param("provider") provider: String, @Param("providerId") providerId: String): User?
}
