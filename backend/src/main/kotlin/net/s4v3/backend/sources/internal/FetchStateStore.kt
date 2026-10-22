package net.s4v3.backend.sources.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/** Per-board fetch state in `source_fetch_state`: the last accepted validator and the start of the last run. */
@Component
internal class FetchStateStore(private val jdbc: JdbcClient) {

    fun lastRunAt(sourceId: String): Instant? =
        jdbc.sql("SELECT last_run_at FROM source_fetch_state WHERE source_id = :id").param("id", sourceId)
            .query { rs, _ -> rs.getTimestamp(1).toInstant() }.optional().orElse(null)

    fun etag(sourceId: String): String? =
        jdbc.sql("SELECT etag FROM source_fetch_state WHERE source_id = :id").param("id", sourceId)
            .query { rs, _ -> rs.getString(1) }.optional().orElse(null)

    /** Records that a run started now, keeping the stored validator. */
    fun recordRunStart(sourceId: String, at: Instant) {
        jdbc.sql(
            """
            INSERT INTO source_fetch_state (source_id, last_run_at) VALUES (:id, :at)
            ON CONFLICT (source_id) DO UPDATE SET last_run_at = EXCLUDED.last_run_at
            """.trimIndent(),
        ).param("id", sourceId).param("at", Timestamp.from(at)).update()
    }

    fun saveEtag(sourceId: String, etag: String?) {
        jdbc.sql("UPDATE source_fetch_state SET etag = :etag WHERE source_id = :id")
            .param("etag", etag).param("id", sourceId).update()
    }
}
