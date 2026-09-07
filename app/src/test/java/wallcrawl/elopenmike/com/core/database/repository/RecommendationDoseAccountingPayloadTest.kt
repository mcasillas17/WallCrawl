package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting

/**
 * The stored form of a recommendation's dose accounting.
 *
 * Decoding is strict and total: a record can be read back from an archive anyone could have
 * edited, so anything that is not exactly well formed is refused rather than coerced into a
 * plausible-looking count.
 */
class RecommendationDoseAccountingPayloadTest {

    @Test
    fun accounting_roundTrips() {
        val accounting = listOf(
            MuscleDoseAccounting("Back", completedSets = 0, proposedSets = 5, allowanceSets = 6),
            MuscleDoseAccounting("Chest", completedSets = 4, proposedSets = 2, allowanceSets = 6)
        )

        val decoded = RecommendationDoseAccountingPayload.decode(
            RecommendationDoseAccountingPayload.encode(accounting)
        )

        assertThat(decoded).isEqualTo(accounting)
    }

    @Test
    fun anAbsentAllowance_roundTripsAsAbsentRatherThanZero() {
        val accounting = listOf(
            MuscleDoseAccounting("Chest", completedSets = 1, proposedSets = 2, allowanceSets = null)
        )

        val decoded = RecommendationDoseAccountingPayload.decode(
            RecommendationDoseAccountingPayload.encode(accounting)
        )

        assertThat(decoded).isEqualTo(accounting)
        assertThat(decoded!!.single().allowanceSets).isNull()
    }

    @Test
    fun noAccountingAtAll_roundTripsAsAnEmptyList() {
        val encoded = RecommendationDoseAccountingPayload.encode(emptyList())

        assertThat(RecommendationDoseAccountingPayload.decode(encoded)).isEmpty()
    }

    @Test
    fun encoding_isCanonicalSoEqualAccountingSerializesIdentically() {
        val one = listOf(
            MuscleDoseAccounting("Chest", 4, 2, 6),
            MuscleDoseAccounting("Back", 0, 5, 6)
        )
        val other = listOf(
            MuscleDoseAccounting("Back", 0, 5, 6),
            MuscleDoseAccounting("Chest", 4, 2, 6)
        )

        assertThat(RecommendationDoseAccountingPayload.encode(one))
            .isEqualTo(RecommendationDoseAccountingPayload.encode(other))
    }

    @Test
    fun aPayloadWithoutTheHeader_isRefused() {
        assertThat(RecommendationDoseAccountingPayload.decode("Chest\t4\t2\t6")).isNull()
    }

    @Test
    fun aLineWithTheWrongFieldCount_isRefused() {
        assertThat(decodeEntries("Chest\t4\t2")).isNull()
        assertThat(decodeEntries("Chest\t4\t2\t6\t9")).isNull()
    }

    @Test
    fun aBlankOrOverlongMuscle_isRefused() {
        assertThat(decodeEntries("\t4\t2\t6")).isNull()
        assertThat(decodeEntries("${"m".repeat(65)}\t4\t2\t6")).isNull()
    }

    @Test
    fun aNegativeOrUnparsableCount_isRefused() {
        assertThat(decodeEntries("Chest\t-1\t2\t6")).isNull()
        assertThat(decodeEntries("Chest\t4\t-2\t6")).isNull()
        assertThat(decodeEntries("Chest\tfour\t2\t6")).isNull()
    }

    @Test
    fun aNonPositiveAllowance_isRefusedBecauseAConfiguredAllowanceIsNeverZero() {
        assertThat(decodeEntries("Chest\t4\t2\t0")).isNull()
    }

    @Test
    fun aRepeatedMuscle_isRefused() {
        assertThat(decodeEntries("Chest\t4\t2\t6\nChest\t1\t1\t6")).isNull()
    }

    @Test
    fun moreMusclesThanAnyCatalogHolds_isRefused() {
        val entries = (0..64).joinToString("\n") { "muscle-$it\t1\t1\t6" }

        assertThat(decodeEntries(entries)).isNull()
    }

    private fun decodeEntries(entries: String): List<MuscleDoseAccounting>? =
        RecommendationDoseAccountingPayload.decode(
            "${RecommendationDoseAccountingPayload.PAYLOAD_HEADER}\n$entries"
        )
}
