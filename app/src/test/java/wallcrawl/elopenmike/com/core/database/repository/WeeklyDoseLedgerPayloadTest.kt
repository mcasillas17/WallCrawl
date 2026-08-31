package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

/**
 * The cached payload is untrusted local data: decoding is strict, and anything that does
 * not decode exactly must read back as "no usable cache" so the ledger is recomputed.
 */
class WeeklyDoseLedgerPayloadTest {

    private val ledger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = "Asia/Kolkata",
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        directPrimarySets = linkedMapOf("Chest" to 5, "Back" to 3),
        secondaryInvolvement = linkedMapOf("Triceps" to 5),
        unattributedWorkSets = linkedMapOf(LedgerOmissionReason.METADATA_NOT_APPROVED to 4)
    )

    @Test
    fun encodingIsDeterministicAndIndependentOfInsertionOrder() {
        val reordered = ledger.copy(
            directPrimarySets = linkedMapOf("Back" to 3, "Chest" to 5)
        )

        assertThat(WeeklyDoseLedgerPayload.encode(reordered))
            .isEqualTo(WeeklyDoseLedgerPayload.encode(ledger))
    }

    @Test
    fun aPayloadDecodesBackToTheSameCountsInCanonicalOrder() {
        val decoded = WeeklyDoseLedgerPayload.decode(
            payload = WeeklyDoseLedgerPayload.encode(ledger),
            policyVersion = ledger.policyVersion,
            weekStartEpochDay = ledger.weekStartEpochDay,
            timeZoneId = ledger.timeZoneId,
            catalogVersion = ledger.catalogVersion,
            reviewPolicyVersion = ledger.reviewPolicyVersion
        )

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.directPrimarySets).containsExactly("Back", 3, "Chest", 5).inOrder()
        assertThat(decoded.secondaryInvolvement).containsExactly("Triceps", 5)
        assertThat(decoded.unattributedWorkSets)
            .containsExactly(LedgerOmissionReason.METADATA_NOT_APPROVED, 4)
        assertThat(WeeklyDoseLedgerPayload.encode(decoded))
            .isEqualTo(WeeklyDoseLedgerPayload.encode(ledger))
    }

    @Test
    fun anEmptyLedgerRoundTripsAsAnExplicitlyEmptyLedgerRatherThanAMissingCache() {
        val empty = ledger.copy(
            directPrimarySets = emptyMap(),
            secondaryInvolvement = emptyMap(),
            unattributedWorkSets = emptyMap()
        )

        val decoded = decode(WeeklyDoseLedgerPayload.encode(empty))

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.directPrimarySets).isEmpty()
        assertThat(decoded.unattributedWorkSets).isEmpty()
    }

    @Test
    fun everyMalformedPayloadDecodesToNullInsteadOfPartialOrGuessedCounts() {
        val encoded = WeeklyDoseLedgerPayload.encode(ledger)
        val malformed = mapOf(
            "empty" to "",
            "blank" to "   ",
            "missing header" to encoded.lines().drop(1).joinToString("\n"),
            "wrong header version" to encoded.replace("v1", "v2"),
            "unknown entry kind" to "$encoded\ntertiary\tChest\t1",
            "unknown omission reason" to encoded.replace(
                LedgerOmissionReason.METADATA_NOT_APPROVED.name,
                "SOME_FUTURE_REASON"
            ),
            "too few fields" to "$encoded\nprimary\t7",
            "too many fields" to "$encoded\nprimary\tChest\t1\textra",
            "non numeric count" to "$encoded\nprimary\tShoulders\tmany",
            "zero count" to "$encoded\nprimary\tShoulders\t0",
            "negative count" to "$encoded\nprimary\tShoulders\t-3",
            "count above bound" to "$encoded\nprimary\tShoulders\t2000000000",
            "blank muscle" to "$encoded\nprimary\t \t1",
            "oversized muscle" to "$encoded\nprimary\t${"M".repeat(200)}\t1",
            "duplicate key" to "$encoded\nprimary\tChest\t2",
            "too many entries" to buildString {
                append(encoded)
                repeat(200) { index -> append("\nprimary\tMuscle$index\t1") }
            }
        )

        malformed.forEach { (description, payload) ->
            assertWithMessage("decoding a payload with %s", description)
                .that(decode(payload))
                .isNull()
        }
    }

    @Test
    fun aPayloadWhoseTotalExceedsTheWeeklyWorkSetBoundIsRejected() {
        val oversized = buildString {
            append(WeeklyDoseLedgerPayload.PAYLOAD_HEADER)
            append("\nprimary\tChest\t40000")
            append("\nprimary\tBack\t40000")
        }

        assertThat(decode(oversized)).isNull()
    }

    private fun decode(payload: String): WeeklyDoseLedger? = WeeklyDoseLedgerPayload.decode(
        payload = payload,
        policyVersion = ledger.policyVersion,
        weekStartEpochDay = ledger.weekStartEpochDay,
        timeZoneId = ledger.timeZoneId,
        catalogVersion = ledger.catalogVersion,
        reviewPolicyVersion = ledger.reviewPolicyVersion
    )
}
