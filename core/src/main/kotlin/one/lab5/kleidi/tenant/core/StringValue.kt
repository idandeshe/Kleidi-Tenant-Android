// A string-backed value from the server that must keep decoding when the server
// adds a new case. Kotlin enums would fail the whole response on an unknown
// value; these `@JvmInline value class`es keep the raw string and render it
// humanized when no label is known (same pattern as the staff apps).

package one.lab5.kleidi.tenant.core

interface StringValue {
    val raw: String
}

/** `bank_transfer` / `semi-basement` → "Bank transfer" / "Semi basement". */
fun humanize(raw: String): String {
    val words = raw.replace('_', ' ').replace('-', ' ').trim()
    if (words.isEmpty()) return raw
    return words.first().uppercase() + words.drop(1)
}
