package net.s4v3.backend.files.internal

import net.s4v3.backend.files.CabinetFile
import net.s4v3.backend.files.FileCabinet
import org.springframework.stereotype.Service

@Service
internal class FileCabinetService(
    private val files: JdbcFileRepository,
    private val storage: FileStorage,
) : FileCabinet {

    override fun masterCv(sub: String): CabinetFile? {
        val master = files.master(sub) ?: return null
        return CabinetFile(master.id, master.name, master.contentType, master.sizeBytes, storage.open(master.storageKey))
    }
}
