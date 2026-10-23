package net.s4v3.backend.files.internal

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

internal enum class FileKind { CV, OTHER }

/** One `user_file` row. The storage key is internal: the public API hands out a path, never a key. */
internal data class FileRecord(
    val id: UUID,
    val ownerSub: String,
    val name: String,
    val kind: FileKind,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val storageKey: String,
    val master: Boolean,
    val uploadedAt: Instant,
)

/** `user_file`: one row per stored file. Every statement is scoped by the owner's `sub`. */
@Repository
internal class JdbcFileRepository(
    private val jdbc: JdbcTemplate,
    transactions: PlatformTransactionManager,
) {

    private val tx = TransactionTemplate(transactions)

    fun insert(file: FileRecord) {
        jdbc.update(
            """
            insert into user_file (id, owner_sub, name, kind, content_type, size_bytes, sha256, storage_key, master, uploaded_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            file.id, file.ownerSub, file.name, file.kind.name, file.contentType, file.sizeBytes, file.sha256,
            file.storageKey, file.master, Timestamp.from(file.uploadedAt),
        )
    }

    fun find(sub: String, id: UUID): FileRecord? =
        jdbc.query("$SELECT where owner_sub = ? and id = ?", { rs, _ -> record(rs) }, sub, id).firstOrNull()

    /** Newest first, optionally only the files of one kind. */
    fun list(sub: String, kind: FileKind? = null): List<FileRecord> =
        if (kind == null) {
            jdbc.query("$SELECT where owner_sub = ? order by uploaded_at desc", { rs, _ -> record(rs) }, sub)
        } else {
            jdbc.query(
                "$SELECT where owner_sub = ? and kind = ? order by uploaded_at desc",
                { rs, _ -> record(rs) },
                sub, kind.name,
            )
        }

    /** Deletes the row and answers its storage key, so the caller can remove the bytes. Null when there is no such row. */
    fun delete(sub: String, id: UUID): String? =
        jdbc.query(
            "delete from user_file where owner_sub = ? and id = ? returning storage_key",
            { rs, _ -> rs.getString("storage_key") },
            sub, id,
        ).firstOrNull()

    /** Makes the file the master. Clears the old master and sets the new one in one transaction. False when the file is not a CV of this user. */
    fun setMaster(sub: String, id: UUID): Boolean =
        checkNotNull(
            tx.execute { status ->
                jdbc.update("update user_file set master = false where owner_sub = ? and master", sub)
                val set = jdbc.update(
                    "update user_file set master = true where owner_sub = ? and id = ? and kind = 'CV'",
                    sub, id,
                )
                if (set == 0) status.setRollbackOnly()
                set == 1
            },
        )

    fun clearMaster(sub: String) {
        jdbc.update("update user_file set master = false where owner_sub = ? and master", sub)
    }

    fun master(sub: String): FileRecord? =
        jdbc.query("$SELECT where owner_sub = ? and master", { rs, _ -> record(rs) }, sub).firstOrNull()

    private fun record(rs: ResultSet) =
        FileRecord(
            id = rs.getObject("id", UUID::class.java),
            ownerSub = rs.getString("owner_sub"),
            name = rs.getString("name"),
            kind = FileKind.valueOf(rs.getString("kind")),
            contentType = rs.getString("content_type"),
            sizeBytes = rs.getLong("size_bytes"),
            sha256 = rs.getString("sha256"),
            storageKey = rs.getString("storage_key"),
            master = rs.getBoolean("master"),
            uploadedAt = rs.getTimestamp("uploaded_at").toInstant(),
        )

    private companion object {
        const val SELECT = "select id, owner_sub, name, kind, content_type, size_bytes, sha256, storage_key, master, uploaded_at from user_file"
    }
}
