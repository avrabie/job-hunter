package net.s4v3.backend.cv.internal

import net.s4v3.backend.cv.CvInfo
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.Instant

/** The stored file with its bytes. Never logged. */
internal class StoredCv(val info: CvInfo, val content: ByteArray)

/** `cv_document`: one row per user. Every statement is scoped by the owner's `sub`. */
@Repository
internal class JdbcCvStore(private val jdbc: JdbcTemplate) {

    fun info(sub: String): CvInfo? =
        jdbc.query(
            "select file_name, content_type, size_bytes, uploaded_at from cv_document where owner_sub = ?",
            { rs, _ -> info(rs) },
            sub,
        ).firstOrNull()

    fun file(sub: String): StoredCv? =
        jdbc.query(
            "select file_name, content_type, size_bytes, uploaded_at, content from cv_document where owner_sub = ?",
            { rs, _ -> StoredCv(info(rs), rs.getBytes("content")) },
            sub,
        ).firstOrNull()

    /** Stores the file, replacing the user's earlier one. */
    fun save(sub: String, cv: AcceptedCv): CvInfo {
        jdbc.update(
            """
            insert into cv_document (owner_sub, file_name, content_type, size_bytes, sha256, content, uploaded_at)
            values (?, ?, ?, ?, ?, ?, now())
            on conflict (owner_sub) do update
               set file_name = excluded.file_name, content_type = excluded.content_type, size_bytes = excluded.size_bytes,
                   sha256 = excluded.sha256, content = excluded.content, uploaded_at = excluded.uploaded_at
            """.trimIndent(),
            sub, cv.displayName, cv.contentType, cv.bytes.size, sha256(cv.bytes), cv.bytes,
        )
        return checkNotNull(info(sub))
    }

    fun delete(sub: String) {
        jdbc.update("delete from cv_document where owner_sub = ?", sub)
    }

    private fun info(rs: ResultSet) =
        CvInfo(
            fileName = rs.getString("file_name"),
            contentType = rs.getString("content_type"),
            sizeBytes = rs.getInt("size_bytes"),
            uploadedAt = rs.getTimestamp("uploaded_at").toInstant(),
        )

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
