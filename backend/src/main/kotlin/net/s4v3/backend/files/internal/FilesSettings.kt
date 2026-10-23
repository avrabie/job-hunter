package net.s4v3.backend.files.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(FilesSettings::class)
internal class FilesConfig

/**
 * `jobhunter.files.*`. `dir` is the root of the files volume; `minFreeBytes` is the space that uploads must leave free, so
 * a full volume cannot stop the database that shares the node disk. `maxOtherBytes` and `maxCvBytes` are the upload limits.
 */
@ConfigurationProperties("jobhunter.files")
internal data class FilesSettings(
    val dir: String = "/data/files",
    val minFreeBytes: Long = 2_147_483_648L,
    /** The largest file of kind `OTHER`: 10 GiB. */
    val maxOtherBytes: Long = 10_737_418_240L,
    /** The largest file of kind `CV`: 5 MB. */
    val maxCvBytes: Long = 5_242_880L,
    /** How long a download ticket is valid. */
    val ticketSeconds: Long = 60L,
)
