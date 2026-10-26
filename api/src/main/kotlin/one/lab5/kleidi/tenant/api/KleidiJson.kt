// JSON for the Kleidi tenant API. Server dates are `Date#toISOString()` strings; model files
// declare `@file:UseSerializers(ApiInstantSerializer::class)` so every `Instant` field decodes.
//
// Forward compatibility (as in the staff apps): unknown keys are ignored, models declare only
// the fields the app shows, and enum-like values are open `StringValue`s.

package one.lab5.kleidi.tenant.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import one.lab5.kleidi.tenant.core.parseApiDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val KleidiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}

object ApiInstantSerializer : KSerializer<Instant> {
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KleidiInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return parseApiDate(raw) ?: throw SerializationException("Unrecognised date: $raw")
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(TIMESTAMP.format(value))
    }
}
