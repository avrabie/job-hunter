package net.s4v3.backend

import net.s4v3.backend.accounts.CurrentUser
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * The pattern every per-user resource follows: each query is scoped by the current user's id, and a record
 * that belongs to someone else is indistinguishable from one that does not exist (404, never 403).
 * Test-only; the table is created by the tests.
 */
@TestComponent
@RestController
class TestNotesEndpoints(private val currentUser: CurrentUser, private val jdbc: JdbcClient) {

    data class Note(val id: Long, val text: String)

    @PostMapping("/api/test/notes")
    fun create(@RequestBody text: String): Note {
        val id = jdbc.sql("insert into test_note (owner_sub, text) values (:owner, :text) returning id")
            .param("owner", currentUser.id()).param("text", text)
            .query(Long::class.java).single()
        return Note(id, text)
    }

    @GetMapping("/api/test/notes")
    fun list(): List<Note> =
        jdbc.sql("select id, text from test_note where owner_sub = :owner order by id")
            .param("owner", currentUser.id())
            .query { rs, _ -> Note(rs.getLong("id"), rs.getString("text")) }
            .list()

    @GetMapping("/api/test/notes/{id}")
    fun get(@PathVariable id: Long): Note =
        jdbc.sql("select id, text from test_note where id = :id and owner_sub = :owner")
            .param("id", id).param("owner", currentUser.id())
            .query { rs, _ -> Note(rs.getLong("id"), rs.getString("text")) }
            .optional()
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
}
