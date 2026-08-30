package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType

/** Bounded, allowlisted persistence codec for the profile's capability map. */
internal object MovementCapabilitiesCodec {
    private const val MAX_PAYLOAD_CHARACTERS = 4_096

    fun encode(capabilities: MovementCapabilities): String =
        MovementCapabilityType.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ","
        ) { type ->
            "\"${type.name}\":\"${capabilities[type].name}\""
        }

    fun decode(raw: String): MovementCapabilities {
        if (raw.length > MAX_PAYLOAD_CHARACTERS) return MovementCapabilities.unknown()

        return try {
            val decodedValues = FlatStringJsonObjectParser(raw).parse()
            MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { type ->
                    val persistedLevel = decodedValues[type.name]
                        ?: return@associateWith CapabilityLevel.UNKNOWN
                    try {
                        CapabilityLevel.valueOf(persistedLevel)
                    } catch (error: IllegalArgumentException) {
                        CapabilityLevel.UNKNOWN
                    }
                }
            )
        } catch (error: CapabilityJsonDecodingException) {
            MovementCapabilities.unknown()
        }
    }
}

/** Parses only a JSON object whose keys and values are strings. */
private class FlatStringJsonObjectParser(
    private val source: String
) {
    private var index: Int = 0

    fun parse(): Map<String, String> {
        skipWhitespace()
        expect('{')
        skipWhitespace()
        if (consume('}')) {
            requireEnd()
            return emptyMap()
        }

        val result = linkedMapOf<String, String>()
        while (true) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            val value = parseString()
            if (result.put(key, value) != null) fail()
            skipWhitespace()

            when {
                consume('}') -> break
                consume(',') -> {
                    skipWhitespace()
                    if (peek() == '}') fail()
                }
                else -> fail()
            }
        }

        requireEnd()
        return result
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (true) {
            val character = next() ?: fail()
            when {
                character == '"' -> return result.toString()
                character == '\\' -> result.append(parseEscape())
                character.code < 0x20 -> fail()
                else -> result.append(character)
            }
        }
    }

    private fun parseEscape(): Char {
        return when (val escaped = next() ?: fail()) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> fail()
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (index + 4 > source.length) fail()
        val digits = source.substring(index, index + 4)
        if (digits.any { it.digitToIntOrNull(16) == null }) fail()
        index += 4
        return digits.toInt(16).toChar()
    }

    private fun requireEnd() {
        skipWhitespace()
        if (index != source.length) fail()
    }

    private fun skipWhitespace() {
        while (peek() in JSON_WHITESPACE) index += 1
    }

    private fun expect(expected: Char) {
        if (!consume(expected)) fail()
    }

    private fun consume(expected: Char): Boolean {
        if (peek() != expected) return false
        index += 1
        return true
    }

    private fun peek(): Char? = source.getOrNull(index)

    private fun next(): Char? = source.getOrNull(index)?.also { index += 1 }

    private fun fail(): Nothing = throw CapabilityJsonDecodingException()

    private companion object {
        val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')
    }
}

/** Expected parse failure; it deliberately carries no persisted payload. */
private class CapabilityJsonDecodingException : Exception()
