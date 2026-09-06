package wallcrawl.elopenmike.com.core.io

import java.io.FilterReader
import java.io.Reader

/**
 * Stops an oversized document while it is still being read.
 *
 * Both untrusted JSON inputs this app parses — the bundled catalog and a user-supplied
 * archive — need the same guard, so there is one implementation of it. The limit is
 * enforced per chunk, so a single unbounded string value fails before it can be
 * materialised rather than after.
 *
 * [onExceeded] raises whatever failure its caller reports parse problems with, so neither
 * parser has to translate an exception type belonging to the other.
 */
internal class BoundedCharacterReader(
    input: Reader,
    private val maximumCharacters: Long,
    private val onExceeded: (Long) -> Nothing
) : FilterReader(input) {

    private var charactersRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value != -1) record(1)
        return value
    }

    /**
     * The one place characters are counted.
     *
     * `Reader.read(CharBuffer)` is inherited unchanged on purpose: it delegates here, so
     * overriding it as well would count the same characters twice and halve the effective
     * limit.
     */
    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) record(count)
        return count
    }

    /** Skipped characters were still delivered by the source, so they count too. */
    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0L) record(skipped)
        return skipped
    }

    private fun record(count: Long) {
        charactersRead += count
        if (charactersRead > maximumCharacters) onExceeded(maximumCharacters)
    }

    private fun record(count: Int) = record(count.toLong())
}
