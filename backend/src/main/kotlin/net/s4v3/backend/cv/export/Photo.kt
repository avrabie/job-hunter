package net.s4v3.backend.cv.export

import net.s4v3.backend.cv.export.internal.PhotoPipeline
import net.s4v3.backend.files.CabinetFile

/** The processed photo of a CV: always the JPEG that [PhotoProcessor] produced, never the original bytes. */
class CvPhoto(val bytes: ByteArray)

/** The image is not usable as a photo (not a JPEG, damaged, too large). The message never carries names or paths. */
class InvalidPhotoException : RuntimeException("The photo is not valid")

/**
 * Turns a cabinet JPEG into the 320 x 320 round photo of the Professional CV: size and dimension caps are checked
 * before decoding, the result is a new JPEG without any metadata and is the same bytes for the same input.
 */
class PhotoProcessor {

    /** @throws InvalidPhotoException for anything that cannot become a photo */
    fun process(file: CabinetFile): ByteArray = PhotoPipeline.process(file.path, file.sizeBytes)
}
