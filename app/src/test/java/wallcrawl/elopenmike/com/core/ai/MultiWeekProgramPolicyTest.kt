package wallcrawl.elopenmike.com.core.ai

import org.junit.Assert.*
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class MultiWeekProgramPolicyTest {
    private val profile = UserProfile(onboardingCompleted = true, daysPerWeek = 2)

    @Test fun capturesSupportedFrequencyAndCanonicalGoalsWithoutMutatingProfile() {
        for (frequency in listOf(2, 3, 6)) {
            val goals = linkedSetOf(FitnessGoal.STRENGTH, FitnessGoal.GENERAL_FITNESS)
            val input = profile.copy(daysPerWeek = frequency, goals = goals)
            val configuration = MultiWeekProgramPolicy.configurationFromProfile(input)
            assertEquals(frequency, configuration.sessionsPerWeek)
            assertEquals(FitnessGoal.STRENGTH, configuration.primaryGoal)
            assertEquals(listOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.STRENGTH), configuration.goals)
            assertEquals(listOf(FitnessGoal.STRENGTH, FitnessGoal.GENERAL_FITNESS), goals.toList())
            goals.clear()
            assertEquals(2, configuration.goals.size)
        }
    }

    private val run = "11111111-1111-4111-8111-111111111111"
    private val nextRun = "22222222-2222-4222-8222-222222222222"
    private fun enrollment(frequency: Int = 2) = ProgramEnrollment(
        profileId = profile.id, programId = run, revision = 100,
        configuration = ProgramConfiguration(FitnessGoal.BUILD_MUSCLE, listOf(FitnessGoal.BUILD_MUSCLE), frequency),
        status = ProgramStatus.ACTIVE, createdAtEpochMillis = 100, updatedAtEpochMillis = 100
    )
    private fun expectation(current: ProgramEnrollment? = enrollment()) = ProgramExpectation(
        profile.id, profile.revision, current?.programId, current?.revision ?: 0
    )
    private fun slot(ordinal: Int, status: SessionStatus = SessionStatus.COMPLETED,
                     current: ProgramEnrollment = enrollment()) = ProgramSlotSession(
        ProgramAssociation("session-$ordinal", profile.id,
            MultiWeekProgramPolicy.snapshot(current.copy(revision = if (status == SessionStatus.IN_PROGRESS) current.revision - 1 else 2L * ordinal + 1),
                MultiWeekProgramPolicy.position(current.configuration, ordinal))),
        status, if (status == SessionStatus.COMPLETED) 1 else null
    )
    private fun failure(expected: ProgramFailure, block: () -> Unit) {
        assertEquals(expected, assertThrows(ProgramException::class.java, block).failure)
    }
    private fun act(action: ProgramAction, current: ProgramEnrollment? = enrollment(),
                    slots: List<ProgramSlotSession> = emptyList(),
                    expected: ProgramExpectation = expectation(current), active: Boolean = false,
                    newRun: String? = nextRun, confirmed: Boolean = true,
                    sessionId: String? = null, inputProfile: UserProfile? = profile) =
        MultiWeekProgramPolicy.transition(inputProfile, current, slots, action, expected, 1,
            hasActiveWorkout = active, newProgramId = newRun, confirmed = confirmed, sessionId = sessionId)

    @Test fun positionsCoverWeeksBlocksAndNeverCreateAFifthWeek() {
        for (frequency in listOf(2, 3, 6)) {
            val config = enrollment(frequency).configuration
            assertEquals(ProgramPosition(0, 1, 1, 1), MultiWeekProgramPolicy.position(config, 0))
            assertEquals(ProgramPosition(frequency, 1, 2, 1), MultiWeekProgramPolicy.position(config, frequency))
            assertEquals(ProgramPosition(2 * frequency, 2, 3, 1), MultiWeekProgramPolicy.position(config, 2 * frequency))
            assertEquals(ProgramPosition(4 * frequency - 1, 2, 4, frequency),
                MultiWeekProgramPolicy.position(config, 4 * frequency - 1))
            failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.position(config, 4 * frequency) }
            failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.position(config, -1) }
            val current = enrollment(frequency).copy(status = ProgramStatus.COMPLETED)
            val progress = MultiWeekProgramPolicy.reconstruct(current, (0 until 4 * frequency).map { slot(it, current = current) })
            assertEquals(4 * frequency, progress.completedSlots)
            assertNull(progress.nextPosition)
        }
    }

    @Test fun rejectsUnsupportedProfilesVersionsAndMalformedKnownConfigurations() {
        for (frequency in listOf(Int.MIN_VALUE, 0, 1, 7, Int.MAX_VALUE)) {
            failure(ProgramFailure.UNSUPPORTED_CONFIGURATION) {
                MultiWeekProgramPolicy.configurationFromProfile(profile.copy(daysPerWeek = frequency))
            }
        }
        failure(ProgramFailure.PROFILE_UNAVAILABLE) {
            MultiWeekProgramPolicy.configurationFromProfile(profile.copy(onboardingCompleted = false))
        }
        val current = enrollment()
        failure(ProgramFailure.UNSUPPORTED_POLICY) {
            MultiWeekProgramPolicy.validateSupported(current.copy(lifecyclePolicyVersion = "FUTURE"))
        }
        failure(ProgramFailure.UNSUPPORTED_POLICY) {
            MultiWeekProgramPolicy.validateSupported(current.copy(configuration = current.configuration.copy(policyVersion = "FUTURE")))
        }
        for (goals in listOf(emptyList(), listOf(FitnessGoal.STRENGTH), listOf(FitnessGoal.BUILD_MUSCLE, FitnessGoal.BUILD_MUSCLE))) {
            failure(ProgramFailure.INVALID_CONFIGURATION) {
                MultiWeekProgramPolicy.validateSupported(current.copy(configuration = current.configuration.copy(goals = goals)))
            }
        }
        failure(ProgramFailure.PROFILE_MISMATCH) {
            MultiWeekProgramPolicy.validateProfileMatch(current.configuration, profile.copy(daysPerWeek = 3))
        }
        val canonical = current.configuration.copy(goals = listOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE))
        MultiWeekProgramPolicy.validateProfileMatch(canonical,
            profile.copy(goals = linkedSetOf(FitnessGoal.BUILD_MUSCLE, FitnessGoal.STRENGTH)))
    }

    @Test fun everyManagementActionHasAnExplicitAllowedSourceAndPreservesHistoryInputs() {
        for (status in ProgramStatus.entries) {
            val current = enrollment().copy(status = status)
            val slots = if (status == ProgramStatus.COMPLETED) (0..7).map { slot(it, current = current) } else emptyList()
            for (action in ProgramAction.entries.filter { it in listOf(ProgramAction.START, ProgramAction.PAUSE,
                    ProgramAction.RESUME, ProgramAction.RESTART, ProgramAction.REPLACE) }) {
                val allowed = when (action) {
                    ProgramAction.START -> false
                    ProgramAction.PAUSE -> status == ProgramStatus.ACTIVE
                    ProgramAction.RESUME -> status == ProgramStatus.PAUSED
                    else -> true
                }
                if (!allowed) failure(ProgramFailure.INVALID_ACTION) { act(action, current, slots) }
                else {
                    val changed = act(action, current, slots)
                    assertEquals(101, changed.revision)
                    assertEquals(if (action == ProgramAction.PAUSE) ProgramStatus.PAUSED else ProgramStatus.ACTIVE, changed.status)
                    assertEquals(if (action in listOf(ProgramAction.RESTART, ProgramAction.REPLACE)) nextRun else run, changed.programId)
                    assertEquals(1, changed.updatedAtEpochMillis)
                    assertEquals(status, current.status)
                    assertEquals(100, current.revision)
                }
            }
        }
        val started = act(ProgramAction.START, current = null)
        assertEquals(1, started.revision)
        assertEquals(nextRun, started.programId)
        for (action in ProgramAction.entries - ProgramAction.START) {
            failure(ProgramFailure.INVALID_ACTION) { act(action, current = null) }
        }
    }

    @Test fun guardsRejectStaleRunsRevisionsMissingProfilesActiveWorkoutsAndExhaustion() {
        val expected = expectation()
        for (stale in listOf(expected.copy(profileId = "other"), expected.copy(profileRevision = 1),
            expected.copy(programId = nextRun), expected.copy(enrollmentRevision = 0))) {
            failure(ProgramFailure.STALE_STATE) { act(ProgramAction.PAUSE, expected = stale) }
        }
        failure(ProgramFailure.PROFILE_UNAVAILABLE) { act(ProgramAction.PAUSE, inputProfile = null) }
        failure(ProgramFailure.PROFILE_UNAVAILABLE) {
            act(ProgramAction.PAUSE, inputProfile = profile.copy(onboardingCompleted = false))
        }
        for (action in listOf(ProgramAction.PAUSE, ProgramAction.RESUME, ProgramAction.RESTART, ProgramAction.REPLACE)) {
            failure(ProgramFailure.ACTIVE_WORKOUT) { act(action, active = true) }
        }
        failure(ProgramFailure.ACTIVE_WORKOUT) { act(ProgramAction.START, current = null, active = true) }
        failure(ProgramFailure.CONFIRMATION_REQUIRED) { act(ProgramAction.RESTART, confirmed = false) }
        failure(ProgramFailure.CONFIRMATION_REQUIRED) { act(ProgramAction.REPLACE, confirmed = false) }
        failure(ProgramFailure.INVALID_IDENTITY) { act(ProgramAction.RESTART, newRun = run) }
        val exhausted = enrollment().copy(revision = Long.MAX_VALUE)
        failure(ProgramFailure.REVISION_EXHAUSTED) { act(ProgramAction.PAUSE, exhausted) }
        val paused = act(ProgramAction.PAUSE)
        failure(ProgramFailure.STALE_STATE) { act(ProgramAction.RESUME, paused, expected = expected) }
        assertEquals(102, act(ProgramAction.RESTART, paused).revision)
    }

    @Test fun reconstructionCountsFinishedSessionsWithoutSetPerformanceOrClockChronology() {
        val current = enrollment()
        val empty = MultiWeekProgramPolicy.reconstruct(current, emptyList())
        assertEquals(0, empty.completedSlots)
        assertEquals(0, empty.nextPosition!!.slotOrdinal)
        val rows = mutableListOf(slot(1, SessionStatus.IN_PROGRESS), slot(0))
        val progress = MultiWeekProgramPolicy.reconstruct(current, rows)
        assertEquals(1, progress.completedSlots)
        assertEquals(1, progress.nextPosition!!.slotOrdinal)
        assertEquals("session-1", progress.activeSessionId)
        assertEquals(listOf("session-1", "session-0"), rows.map { it.association.sessionId })
        val cancelled = MultiWeekProgramPolicy.reconstruct(current, rows.filter { it.status != SessionStatus.IN_PROGRESS })
        assertEquals(1, cancelled.completedSlots)
        assertEquals(1, cancelled.nextPosition!!.slotOrdinal)
        assertNull(cancelled.activeSessionId)
        val rollback = MultiWeekProgramPolicy.reconstruct(current, listOf(slot(0).copy(completedAtEpochMillis = 0)))
        assertEquals(1, rollback.completedSlots)
    }

    @Test fun reconstructionRejectsDuplicateGapStatusSnapshotAndLifecycleCorruption() {
        val current = enrollment()
        val one = slot(0)
        val wrongPosition = one.copy(association = one.association.copy(snapshot = one.association.snapshot.copy(position = ProgramPosition(0, 2, 1, 1))))
        val wrongGoal = one.copy(association = one.association.copy(snapshot = one.association.snapshot.copy(configuration = current.configuration.copy(sessionsPerWeek = 3))))
        val wrongRun = one.copy(association = one.association.copy(snapshot = one.association.snapshot.copy(programId = nextRun)))
        val wrongProfile = one.copy(association = one.association.copy(profileId = "other"))
        val wrongObjective = one.copy(association = one.association.copy(snapshot = one.association.snapshot.copy(objective = ProgramObjective.CONTINUE_AND_RECORD)))
        val futureRevision = one.copy(association = one.association.copy(snapshot = one.association.snapshot.copy(enrollmentRevision = 101)))
        for (rows in listOf(listOf(one, one), listOf(slot(1)), listOf(slot(1, SessionStatus.IN_PROGRESS)),
            listOf(slot(0, SessionStatus.CANCELLED)), listOf(one.copy(completedAtEpochMillis = null)),
            listOf(slot(0, SessionStatus.IN_PROGRESS).copy(completedAtEpochMillis = 1)),
            listOf(wrongPosition), listOf(wrongGoal), listOf(wrongRun), listOf(wrongProfile), listOf(wrongObjective), listOf(futureRevision),
            listOf(slot(0, SessionStatus.IN_PROGRESS), slot(1, SessionStatus.IN_PROGRESS)))) {
            failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.reconstruct(current, rows) }
        }
        failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.reconstruct(current.copy(status = ProgramStatus.COMPLETED), emptyList()) }
        failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.reconstruct(current, (0..7).map { slot(it) }) }
        failure(ProgramFailure.INVALID_PROGRESS) {
            MultiWeekProgramPolicy.reconstruct(current.copy(status = ProgramStatus.PAUSED), listOf(slot(0, SessionStatus.IN_PROGRESS)))
        }
    }

    @Test fun explicitReservationFinishAndCancelOnlyChangeTheAssociatedActiveSlot() {
        assertEquals(101, act(ProgramAction.START_SESSION).revision)
        for (status in listOf(ProgramStatus.PAUSED, ProgramStatus.COMPLETED)) {
            failure(ProgramFailure.INVALID_ACTION) { act(ProgramAction.START_SESSION, enrollment().copy(status = status)) }
            failure(ProgramFailure.INVALID_ACTION) { act(ProgramAction.FINISH_SESSION, enrollment().copy(status = status)) }
            failure(ProgramFailure.INVALID_ACTION) { act(ProgramAction.CANCEL_SESSION, enrollment().copy(status = status)) }
        }
        val rows = listOf(slot(0, SessionStatus.IN_PROGRESS))
        assertEquals(101, act(ProgramAction.FINISH_SESSION, slots = rows, sessionId = "session-0").revision)
        assertEquals(ProgramStatus.ACTIVE, act(ProgramAction.CANCEL_SESSION, slots = rows, sessionId = "session-0").status)
        failure(ProgramFailure.MISSING_OR_NOT_ACTIVE) { act(ProgramAction.FINISH_SESSION, slots = rows, sessionId = "wrong") }
        failure(ProgramFailure.MISSING_OR_NOT_ACTIVE) { act(ProgramAction.CANCEL_SESSION) }
        failure(ProgramFailure.ACTIVE_WORKOUT) { act(ProgramAction.PAUSE, slots = rows) }
        failure(ProgramFailure.ACTIVE_WORKOUT) { act(ProgramAction.START_SESSION, slots = rows) }
        val finalRows = (0..6).map { slot(it) } + slot(7, SessionStatus.IN_PROGRESS)
        val finished = act(ProgramAction.FINISH_SESSION, slots = finalRows, sessionId = "session-7",
            inputProfile = profile.copy(daysPerWeek = 3))
        assertEquals(ProgramStatus.COMPLETED, finished.status)
        failure(ProgramFailure.MISSING_OR_NOT_ACTIVE) { act(ProgramAction.FINISH_SESSION, slots = listOf(slot(0)), sessionId = "session-0") }
    }

    @Test fun unknownPolicyIdentityCanBeReadReplacedAndCancelledWithoutInterpretingConfiguration() {
        val header = ProgramHeader(profile.id, run, 9, "a".repeat(64))
        val expected = ProgramExpectation(profile.id, profile.revision, run, 9)
        val changed = MultiWeekProgramPolicy.replace(profile, header, expected, nextRun, 1, false, true)
        assertEquals(nextRun, changed.programId)
        assertEquals(10, changed.revision)
        assertEquals(2, changed.configuration.sessionsPerWeek)
        MultiWeekProgramPolicy.checkExpected(profile, header, expected)
        assertEquals(10, MultiWeekProgramPolicy.nextRevision(header.revision))
        assertNotEquals(header, header.copy(rawStorageFingerprint = "b".repeat(64)))
        assertEquals(ProgramHeader.absent(profile.id), ProgramHeader(profile.id, null, 0, null))
        for (bad in listOf<() -> Unit>({ header.copy(programId = "bad") }, { header.copy(revision = 0) },
            { ProgramHeader(profile.id, null, 1, null) }, { header.copy(rawStorageFingerprint = "bad") })) {
            failure(ProgramFailure.INVALID_IDENTITY, bad)
        }
        val unknown = enrollment().copy(lifecyclePolicyVersion = "FUTURE", configuration = enrollment().configuration.copy(sessionsPerWeek = -1))
        assertEquals(ProgramStatus.PAUSED, act(ProgramAction.PAUSE, unknown).status)
        assertEquals(nextRun, act(ProgramAction.REPLACE, unknown).programId)
        for (action in listOf(ProgramAction.RESUME, ProgramAction.RESTART, ProgramAction.START_SESSION, ProgramAction.FINISH_SESSION)) {
            val source = if (action == ProgramAction.RESUME) unknown.copy(status = ProgramStatus.PAUSED) else unknown
            failure(ProgramFailure.UNSUPPORTED_POLICY) { act(action, source) }
        }
    }

    @Test fun associationDigestIsFramedCanonicalLocaleIndependentAndBindsEveryFrozenField() {
        val association = slot(0).association
        val digest = MultiWeekProgramPolicy.associationDigest(association)
        assertTrue(digest.matches(Regex("[0-9a-f]{64}")))
        val oldLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals(digest, MultiWeekProgramPolicy.associationDigest(association))
        } finally { java.util.Locale.setDefault(oldLocale) }
        val twoGoals = association.copy(snapshot = association.snapshot.copy(configuration = association.snapshot.configuration.copy(
            goals = listOf(FitnessGoal.BUILD_MUSCLE, FitnessGoal.STRENGTH))))
        assertEquals(MultiWeekProgramPolicy.associationDigest(twoGoals), MultiWeekProgramPolicy.associationDigest(
            twoGoals.copy(snapshot = twoGoals.snapshot.copy(configuration = twoGoals.snapshot.configuration.copy(goals = twoGoals.snapshot.configuration.goals.reversed())))))
        val changes = listOf(association.copy(sessionId = "another"), association.copy(profileId = "another"),
            association.copy(snapshot = association.snapshot.copy(programId = nextRun)),
            association.copy(snapshot = association.snapshot.copy(enrollmentRevision = 2)),
            association.copy(snapshot = association.snapshot.copy(lifecyclePolicyVersion = "OTHER")),
            association.copy(snapshot = association.snapshot.copy(position = ProgramPosition(1, 1, 1, 2))),
            association.copy(snapshot = association.snapshot.copy(objective = ProgramObjective.CONTINUE_AND_RECORD)),
            association.copy(snapshot = association.snapshot.copy(configuration = association.snapshot.configuration.copy(sessionsPerWeek = 3))),
            association.copy(snapshot = association.snapshot.copy(configuration = association.snapshot.configuration.copy(policyVersion = "OTHER"))),
            association.copy(snapshot = association.snapshot.copy(configuration = association.snapshot.configuration.copy(primaryGoal = FitnessGoal.STRENGTH))), twoGoals)
        changes.forEach { assertNotEquals(digest, MultiWeekProgramPolicy.associationDigest(it)) }
        assertNotEquals(MultiWeekProgramPolicy.associationDigest(association.copy(sessionId = "a:b", profileId = "c")),
            MultiWeekProgramPolicy.associationDigest(association.copy(sessionId = "a", profileId = "b:c")))
    }

    @Test fun persistedReservationsUseAPreStartRevisionAndKnownReplacementCannotHideCorruption() {
        val current = enrollment().copy(revision = 5)
        val completed = slot(0, current = current).let { row -> row.copy(association = row.association.copy(
            snapshot = row.association.snapshot.copy(enrollmentRevision = 1))) }
        val active = slot(1, SessionStatus.IN_PROGRESS, current).let { row -> row.copy(association = row.association.copy(
            snapshot = row.association.snapshot.copy(enrollmentRevision = 4))) }
        assertEquals(1, MultiWeekProgramPolicy.reconstruct(current, listOf(completed, active)).completedSlots)
        failure(ProgramFailure.INVALID_PROGRESS) {
            MultiWeekProgramPolicy.reconstruct(current, listOf(completed, active.copy(association = active.association.copy(
                snapshot = active.association.snapshot.copy(enrollmentRevision = 5)))))
        }
        failure(ProgramFailure.INVALID_PROGRESS) {
            MultiWeekProgramPolicy.reconstruct(current, listOf(completed, active.copy(association = active.association.copy(
                snapshot = active.association.snapshot.copy(enrollmentRevision = 2)))))
        }
        failure(ProgramFailure.INVALID_PROGRESS) {
            MultiWeekProgramPolicy.reconstruct(current, listOf(completed.copy(completedAtEpochMillis = Long.MAX_VALUE)))
        }
        failure(ProgramFailure.INVALID_CONFIGURATION) {
            act(ProgramAction.REPLACE, current.copy(configuration = current.configuration.copy(goals = emptyList())))
        }
    }

    @Test fun unknownAssociationPolicyIsUnsupportedRatherThanCorruptOrOrdinary() {
        val row = slot(0)
        for (snapshot in listOf(row.association.snapshot.copy(lifecyclePolicyVersion = "FUTURE"),
            row.association.snapshot.copy(configuration = row.association.snapshot.configuration.copy(policyVersion = "FUTURE")))) {
            failure(ProgramFailure.UNSUPPORTED_POLICY) {
                MultiWeekProgramPolicy.reconstruct(enrollment(), listOf(row.copy(association = row.association.copy(snapshot = snapshot))))
            }
        }
    }

    @Test fun frozenWorkoutsCancelAcrossUnsupportedPoliciesAndFinishAcrossProfileMismatch() {
        val known = enrollment()
        val row = slot(0, SessionStatus.IN_PROGRESS)
        val unknown = known.copy(lifecyclePolicyVersion = "FUTURE", configuration = known.configuration.copy(sessionsPerWeek = -1))
        val opaqueRow = row.copy(association = row.association.copy(snapshot = row.association.snapshot.copy(
            lifecyclePolicyVersion = "FUTURE", configuration = unknown.configuration, enrollmentRevision = 1)))
        val cancelled = act(ProgramAction.CANCEL_SESSION, unknown, listOf(opaqueRow), sessionId = "session-0")
        assertEquals(101, cancelled.revision)
        assertEquals(unknown.configuration, cancelled.configuration)
        assertEquals(ProgramStatus.ACTIVE, cancelled.status)
        assertEquals(1, opaqueRow.association.snapshot.enrollmentRevision)
        assertEquals(SessionStatus.IN_PROGRESS, opaqueRow.status)
        failure(ProgramFailure.UNSUPPORTED_POLICY) {
            act(ProgramAction.FINISH_SESSION, unknown, listOf(opaqueRow), sessionId = "session-0")
        }
        val mismatch = profile.copy(daysPerWeek = 3)
        for (action in listOf(ProgramAction.RESTART, ProgramAction.START_SESSION)) {
            failure(ProgramFailure.PROFILE_MISMATCH) { act(action, inputProfile = mismatch) }
        }
        failure(ProgramFailure.PROFILE_MISMATCH) {
            act(ProgramAction.RESUME, known.copy(status = ProgramStatus.PAUSED), inputProfile = mismatch)
        }
        assertEquals(ProgramStatus.PAUSED, act(ProgramAction.PAUSE, inputProfile = mismatch).status)
        assertEquals(3, act(ProgramAction.REPLACE, inputProfile = mismatch).configuration.sessionsPerWeek)
        val header = ProgramHeader(profile.id, run, 100, "a".repeat(64))
        failure(ProgramFailure.ACTIVE_WORKOUT) {
            MultiWeekProgramPolicy.replace(profile, header, expectation(), nextRun, 1, true, true)
        }
        failure(ProgramFailure.CONFIRMATION_REQUIRED) {
            MultiWeekProgramPolicy.replace(profile, header, expectation(), nextRun, 1, false, false)
        }
    }

    @Test fun completedSlotRequiresASeparateFinishRevision() {
        val current = enrollment().copy(revision = 2)
        failure(ProgramFailure.INVALID_PROGRESS) {
            MultiWeekProgramPolicy.reconstruct(current, listOf(slot(0, current = current)))
        }
        val exhausted = enrollment().copy(revision = Long.MAX_VALUE)
        val tooRecent = slot(0, current = exhausted).let { row -> row.copy(association = row.association.copy(
            snapshot = row.association.snapshot.copy(enrollmentRevision = Long.MAX_VALUE - 1))) }
        failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.reconstruct(exhausted, listOf(tooRecent)) }
        val valid = tooRecent.copy(association = tooRecent.association.copy(
            snapshot = tooRecent.association.snapshot.copy(enrollmentRevision = Long.MAX_VALUE - 2)))
        assertEquals(1, MultiWeekProgramPolicy.reconstruct(exhausted, listOf(valid)).completedSlots)
    }

    @Test fun consecutiveSlotsCannotReuseThePreviousSlotsFinishRevision() {
        val current = enrollment().copy(revision = 6)
        val first = slot(0, current = current)
        val second = slot(1, current = current).let { row -> row.copy(association = row.association.copy(
            snapshot = row.association.snapshot.copy(enrollmentRevision = 2))) }
        failure(ProgramFailure.INVALID_PROGRESS) { MultiWeekProgramPolicy.reconstruct(current, listOf(first, second)) }
    }

    @Test fun startFinishStartReconstructsAndAdditionalRevisionGapsStayValid() {
        val initial = enrollment().copy(revision = 1)
        val firstSnapshot = MultiWeekProgramPolicy.snapshot(initial, ProgramPosition(0, 1, 1, 1))
        val firstActive = ProgramSlotSession(ProgramAssociation("first", profile.id, firstSnapshot), SessionStatus.IN_PROGRESS, null)
        val firstStarted = act(ProgramAction.START_SESSION, initial)
        assertEquals(2, firstStarted.revision)
        val firstFinished = act(ProgramAction.FINISH_SESSION, firstStarted, listOf(firstActive), sessionId = "first")
        assertEquals(3, firstFinished.revision)
        val firstCompleted = firstActive.copy(status = SessionStatus.COMPLETED, completedAtEpochMillis = 1)
        val nextSnapshot = MultiWeekProgramPolicy.snapshot(firstFinished, ProgramPosition(1, 1, 1, 2))
        val secondStarted = act(ProgramAction.START_SESSION, firstFinished, listOf(firstCompleted))
        assertEquals(4, secondStarted.revision)
        val secondActive = ProgramSlotSession(ProgramAssociation("second", profile.id, nextSnapshot), SessionStatus.IN_PROGRESS, null)
        val progress = MultiWeekProgramPolicy.reconstruct(secondStarted, listOf(firstCompleted, secondActive))
        assertEquals(1, progress.completedSlots)
        assertEquals("second", progress.activeSessionId)
        val later = secondActive.copy(status = SessionStatus.COMPLETED, completedAtEpochMillis = 1,
            association = secondActive.association.copy(snapshot = nextSnapshot.copy(enrollmentRevision = 7)))
        assertEquals(2, MultiWeekProgramPolicy.reconstruct(initial.copy(revision = 10), listOf(firstCompleted, later)).completedSlots)
    }
}
