// `multipart/form-data` body for the proof upload (the staff app's Multipart.kt). The server
// reads the text fields from the parts *before* the file, so add the fields first.

package one.lab5.kleidi.tenant.api

import java.io.ByteArrayOutputStream
import java.util.UUID

class MultipartForm(val boundary: String = "kleidi-${UUID.randomUUID()}") {
    private val data = ByteArrayOutputStream()

    val contentType: String get() = "multipart/form-data; boundary=$boundary"

    fun addField(name: String, value: String) {
        append("--$boundary\r\n")
        append("Content-Disposition: form-data; name=\"${escape(name)}\"\r\n\r\n")
        append(value)
        append("\r\n")
    }

    fun addFile(name: String, fileName: String, mimeType: String, contents: ByteArray) {
        append("--$boundary\r\n")
        append("Content-Disposition: form-data; name=\"${escape(name)}\"; filename=\"${escape(fileName)}\"\r\n")
        append("Content-Type: $mimeType\r\n\r\n")
        data.write(contents)
        append("\r\n")
    }

    /** The finished body (closing boundary appended). */
    val body: ByteArray
        get() = data.toByteArray() + "--$boundary--\r\n".encodeToByteArray()

    private fun append(text: String) = data.write(text.encodeToByteArray())

    private fun escape(value: String) = value.replace("\"", "%22").replace("\r\n", " ")
}
