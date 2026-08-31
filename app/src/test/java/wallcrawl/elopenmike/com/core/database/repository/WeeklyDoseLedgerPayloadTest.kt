package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.MONDAY_EPOCH_DAY
import wallcrawl.elopenmike.com.core.ai.WeeklyDoseLedgerCalculator
import wallcrawl.elopenmike.com.core.ai.completedNormalSet
import wallcrawl.elopenmike.com.core.ai.completedSession
import wallcrawl.elopenmike.com.core.ai.exerciseInstance
import wallcrawl.elopenmike.com.core.ai.syntheticApprovedExercise
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingWeek
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
    fun aPayloadWhoseTotalExceedsTheLedgerUnitBoundIsRejected() {
        // Every individual entry is legal here: distinct keys, and each count is within the
        // per-entry ceiling. Only the running total pushes it past the bound.
        val perEntry = WeeklyDoseLedgerCalculator.MAX_WORK_SETS_PER_WEEK
        val entriesNeeded =
            WeeklyDoseLedgerCalculator.MAX_LEDGER_COUNTED_UNITS / perEntry + 1
        val oversized = buildString {
            append(WeeklyDoseLedgerPayload.PAYLOAD_HEADER)
            repeat(entriesNeeded) { index -> append("\nsecondary\tMuscle$index\t$perEntry") }
        }

        assertThat(decode(oversized)).isNull()
    }

    /**
     * The codec has to be able to read back anything the calculator is willing to produce.
     *
     * Secondary involvement is accumulated once per descriptive secondary muscle, so a
     * ledger's payload total grows faster than its work-set total. If the encode side and
     * the decode side disagree about which total is bounded, a legitimately produced ledger
     * gets written to the cache and then refused forever, silently disabling caching for
     * that week instead of failing loudly.
     */
    @Test
    fun anyLedgerTheCalculatorProducesCanBeReadBackFromItsOwnPayload() {
        val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))
        // Enough completed work that primary plus four secondaries per set exceeds the
        // work-set ceiling, spread over exercise instances of a legal size.
        val instances = 501
        val setsPerInstance = 20
        val workSets = instances * setsPerInstance
        val produced = WeeklyDoseLedgerCalculator().calculate(
            sessions = listOf(
                completedSession(
                    id = "very-long-week",
                    completedAtEpochMillis = week.startEpochMillis,
                    exercises = (0 until instances).map { index ->
                        exerciseInstance(
                            exerciseId = "synthetic-bench-press",
                            id = "instance-$index",
                            orderIndex = index,
                            sets = List(setsPerInstance) { completedNormalSet() }
                        )
                    }
                )
            ),
            exercisesById = mapOf(
                "synthetic-bench-press" to syntheticApprovedExercise(
                    id = "synthetic-bench-press",
                    directPrimaryMuscle = "Chest",
                    descriptiveSecondaryMuscles = setOf("Triceps", "Shoulders", "Core", "Lats")
                )
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = "catalog-commit",
            reviewPolicyVersion = 1
        )

        val readBack = WeeklyDoseLedgerPayload.decode(
            payload = WeeklyDoseLedgerPayload.encode(produced),
            policyVersion = produced.policyVersion,
            weekStartEpochDay = produced.weekStartEpochDay,
            timeZoneId = produced.timeZoneId,
            catalogVersion = produced.catalogVersion,
            reviewPolicyVersion = produced.reviewPolicyVersion
        )

        assertThat(produced.directPrimarySets).containsExactly("Chest", workSets)
        assertThat(produced.totalCountedUnits)
            .isGreaterThan(WeeklyDoseLedgerCalculator.MAX_WORK_SETS_PER_WEEK.toLong())
        assertThat(readBack).isEqualTo(produced)
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
