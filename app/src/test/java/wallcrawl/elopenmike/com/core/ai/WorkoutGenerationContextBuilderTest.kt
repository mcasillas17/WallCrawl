package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary

class WorkoutGenerationContextBuilderTest {

    @Test
    fun build_withReviewedEligibilityDisabledPreservesLegacyCandidates() = runTest {
        val first = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()
        val second = first.copy(id = "second-legacy-candidate")
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(
                UserProfile(
                    availableEquipment = listOf(
                        StandardEquipment.DUMBBELL,
                        StandardEquipment.BENCH
                    )
                )
            ),
            workoutRepository = StubWorkoutRepository(emptyList()),
            exerciseCatalog = InMemoryExerciseCatalog(listOf(first, second)),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags()
        )

        val context = builder.build()

        assertThat(context.allowedExercises.map { it.id })
            .containsExactly(first.id, second.id)
            .inOrder()
        assertThat(context.automaticEligibilityResult).isNull()
    }

    @Test
    fun build_withReviewedEligibilityEnabledUsesOnlySyntheticApprovedCandidates() = runTest {
        val base = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()
        val approved = base.copy(
            id = "synthetic-approved",
            reviewedMetadata = syntheticReviewedMetadata(ReviewState.APPROVED)
        )
        val draft = base.copy(
            id = "draft",
            reviewedMetadata = syntheticReviewedMetadata(ReviewState.DRAFT)
        )
        val missing = base.copy(id = "missing", reviewedMetadata = null)
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(
                UserProfile(
                    availableEquipment = listOf(
                        StandardEquipment.DUMBBELL,
                        StandardEquipment.BENCH
                    )
                )
            ),
            workoutRepository = StubWorkoutRepository(emptyList()),
            exerciseCatalog = InMemoryExerciseCatalog(listOf(approved, draft, missing)),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags(reviewedCapabilityEligibility = true),
            reviewedEligibilityPolicy = ExerciseEligibilityPolicy()
        )

        val context = builder.build()

        assertThat(context.allowedExercises).containsExactly(approved)
        assertThat(context.automaticEligibilityResult).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(approved),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = approved.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    ),
                    EligibilityDecision(
                        exerciseId = draft.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.MISSING_APPROVED_METADATA)
                    ),
                    EligibilityDecision(
                        exerciseId = missing.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.MISSING_APPROVED_METADATA)
                    )
                )
            )
        )
    }

    @Test
    fun build_enabledUncalibratedGateAllowsAdvancedWithAvailableStandardRegression() = runTest {
        val base = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()
        val regression = base.copy(
            id = "synthetic-standard-regression",
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.STANDARD,
                progressionFamily = "synthetic-standard-family"
            )
        )
        val advanced = base.copy(
            id = "synthetic-advanced-target",
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.ADVANCED,
                progressionFamily = "synthetic-advanced-family",
                approvedRegressions = listOf(ReviewedExerciseLink(regression.id))
            )
        )
        val builder = reviewedEligibilityBuilder(
            exercises = listOf(advanced, regression),
            completedSessions = emptyList()
        )

        val context = builder.build()

        assertThat(context.allowedExercises).containsExactly(advanced, regression).inOrder()
        assertThat(context.automaticEligibilityResult)
            .isInstanceOf(AutomaticEligibilityResult.Candidates::class.java)
    }

    @Test
    fun build_enabledReturningGateUsesCompletedHistoryForAdvancedRegressionFamily() = runTest {
        val base = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()
        val regression = base.copy(
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.ADVANCED,
                progressionFamily = "synthetic-demonstrated-regression-family"
            )
        )
        val advanced = base.copy(
            id = "synthetic-advanced-target",
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.ADVANCED,
                progressionFamily = "synthetic-undemonstrated-target-family",
                approvedRegressions = listOf(ReviewedExerciseLink(regression.id))
            )
        )
        val completedSession = completedInclinePressSession(completedAtTimestamp = 10_000L)
        val builder = reviewedEligibilityBuilder(
            exercises = listOf(advanced, regression),
            completedSessions = listOf(completedSession),
            returningAfterBreakWeeks = 8
        )

        val context = builder.build()

        assertThat(context.exerciseHistory.keys).containsExactly(regression.id)
        assertThat(context.allowedExercises).containsExactly(advanced, regression).inOrder()
        assertThat(context.automaticEligibilityResult)
            .isInstanceOf(AutomaticEligibilityResult.Candidates::class.java)
    }

    @Test
    fun build_includesCatalogEntriesWithoutReviewedProgrammingWhenEquipmentMatches() = runTest {
        val reviewed = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()
        val unreviewed = reviewed.copy(id = "catalog-only-exercise", programming = null)
        // Onboarding no longer assumes a full gym, so the profile must confirm the
        // equipment this exercise actually needs for the "equipment matches" premise to hold.
        val profile = UserProfile(
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
        )
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(profile),
            workoutRepository = StubWorkoutRepository(emptyList()),
            exerciseCatalog = InMemoryExerciseCatalog(listOf(reviewed, unreviewed)),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer()
        )

        val context = builder.build()

        assertThat(context.allowedExercises.map { it.id })
            .containsExactly(reviewed.id, unreviewed.id)
    }

    @Test
    fun build_filtersCandidatesAndIncludesPersistedPerformance() = runTest {
        val now = 10_000_000L
        val profile = UserProfile(
            goals = setOf(FitnessGoal.STRENGTH),
            availableEquipment = listOf(
                StandardEquipment.DUMBBELL,
                StandardEquipment.BENCH,
                StandardEquipment.BODYWEIGHT
            ),
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH),
            excludedExerciseIds = listOf("dumbbell-lateral-raise")
        )
        val completedSession = completedInclinePressSession(now - 1_000L)
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(profile),
            workoutRepository = StubWorkoutRepository(listOf(completedSession)),
            exerciseCatalog = InMemoryExerciseCatalog(),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            nowTimestamp = { now }
        )

        val context = builder.build()

        assertThat(context.userProfile).isEqualTo(profile)
        assertThat(context.allowedExercises.map { it.id })
            .contains("incline-dumbbell-press")
        assertThat(context.allowedExercises.map { it.id })
            .doesNotContain("barbell-bench-press")
        assertThat(context.allowedExercises.map { it.id })
            .doesNotContain("dumbbell-lateral-raise")
        assertThat(context.recentWorkoutHistory).containsExactly(completedSession)
        assertThat(context.exerciseHistory.getValue("incline-dumbbell-press").lastWeight)
            .isEqualTo(45.0)
        assertThat(context.recentlyTrainedMuscles).containsExactly(StandardMuscles.CHEST)
        // Seeds split rotation, so it has to survive the trip from the repository.
        assertThat(context.completedWorkoutCount).isEqualTo(1)
    }

    @Test
    fun build_limitsHistoryPassedToPlanner() = runTest {
        val sessions = (1..12).map { index ->
            completedInclinePressSession(completedAtTimestamp = index.toLong())
                .copy(id = "session-$index")
        }
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(UserProfile()),
            workoutRepository = StubWorkoutRepository(sessions),
            exerciseCatalog = InMemoryExerciseCatalog(),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            nowTimestamp = { 12L }
        )

        val context = builder.build()

        assertThat(context.recentWorkoutHistory).hasSize(8)
        assertThat(context.recentWorkoutHistory.map { it.id })
            .containsExactly(
                "session-12",
                "session-11",
                "session-10",
                "session-9",
                "session-8",
                "session-7",
                "session-6",
                "session-5"
            )
            .inOrder()
    }

    private fun completedInclinePressSession(completedAtTimestamp: Long): WorkoutSession {
        val set = WorkoutSet(
            id = "set-$completedAtTimestamp",
            workoutExerciseId = "workout-exercise-$completedAtTimestamp",
            setNumber = 1,
            targetReps = 10,
            completedReps = 10,
            targetWeight = 45.0,
            completedWeight = 45.0,
            isCompleted = true
        )
        return WorkoutSession(
            id = "session-$completedAtTimestamp",
            name = "Push",
            completedAtTimestamp = completedAtTimestamp,
            status = SessionStatus.COMPLETED,
            focusMuscles = listOf(StandardMuscles.CHEST),
            exercises = listOf(
                WorkoutExercise(
                    id = set.workoutExerciseId,
                    sessionId = "session-$completedAtTimestamp",
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    targetSets = 1,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    targetWeight = 45.0,
                    sets = listOf(set)
                )
            )
        )
    }

    private fun reviewedEligibilityBuilder(
        exercises: List<wallcrawl.elopenmike.com.core.model.Exercise>,
        completedSessions: List<WorkoutSession>,
        returningAfterBreakWeeks: Int = 0
    ): WorkoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
        userProfileRepository = StubUserProfileRepository(
            UserProfile(
                availableEquipment = listOf(
                    StandardEquipment.DUMBBELL,
                    StandardEquipment.BENCH
                ),
                returningAfterBreakWeeks = returningAfterBreakWeeks
            )
        ),
        workoutRepository = StubWorkoutRepository(completedSessions),
        exerciseCatalog = InMemoryExerciseCatalog(exercises),
        exerciseFilter = ExerciseFilter(),
        historyAnalyzer = WorkoutHistoryAnalyzer(),
        plannerFeatureFlags = PlannerFeatureFlags(reviewedCapabilityEligibility = true),
        reviewedEligibilityPolicy = ExerciseEligibilityPolicy()
    )

    private fun syntheticReviewedMetadata(
        reviewState: ReviewState,
        complexity: ComplexityTier = ComplexityTier.FOUNDATIONAL,
        progressionFamily: String = "synthetic-builder-test-family",
        approvedRegressions: List<ReviewedExerciseLink> = emptyList()
    ): ReviewedExerciseMetadata =
        ReviewedExerciseMetadata(
            reviewState = reviewState,
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = emptySet(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = complexity,
            progressionFamily = progressionFamily,
            prescriptionShape = PrescriptionShape.WEIGHT_REPS,
            approvedRegressions = approvedRegressions,
            approvedSubstitutions = emptyList(),
            capabilityRequirements = emptySet(),
            supportRequirement = SupportRequirement.SUPPORTED,
            impactLevel = ImpactLevel.NONE,
            equipmentAlternatives = listOf(
                listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
            ),
            provenance = ReviewProvenance(
                reviewerRole = if (reviewState == ReviewState.APPROVED) {
                    "Synthetic test-only reviewer"
                } else {
                    null
                },
                rationaleOrSource = "SYNTHETIC TEST DATA — never bundled in production assets.",
                reviewedAtEpochMillis = if (reviewState == ReviewState.APPROVED) 1L else null,
                schemaVersion = 1,
                policyVersion = 1
            )
        )
}

private class StubUserProfileRepository(
    private val profile: UserProfile
) : UserProfileRepository {
    override fun getUserProfile(): Flow<UserProfile> = flowOf(profile)
    override suspend fun getProfileOnce(): UserProfile = profile
    override suspend fun saveUserProfile(profile: UserProfile) = Unit
    override suspend fun saveProfile(profile: UserProfile) = Unit
    override suspend fun updateGoals(goals: Set<FitnessGoal>) = Unit
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = Unit
    override suspend fun updateExperienceLevel(level: wallcrawl.elopenmike.com.core.model.ExperienceLevel) = Unit
    override suspend fun updatePreferredDuration(minutes: Int) = Unit
    override suspend fun updateDaysPerWeek(days: Int) = Unit
    override suspend fun updateEquipment(equipment: List<String>) = Unit
    override suspend fun updateUnit(unit: WeightUnit) = Unit
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) = Unit
    override suspend fun updateExcludedExercises(excludedIds: List<String>) = Unit
    override suspend fun updateTrainingConstraints(
        constraints: Set<wallcrawl.elopenmike.com.core.model.TrainingConstraint>
    ) = Unit
    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) = Unit
    override suspend fun updateThemePreference(themePreference: wallcrawl.elopenmike.com.core.model.ThemePreference) = Unit
}

private class StubWorkoutRepository(
    private val completedSessions: List<WorkoutSession>
) : WorkoutRepository {
    override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun getActiveSessionOnce(): WorkoutSession? = null
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = null
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        flowOf(completedSessions.take(limit))

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(completedSessions.size)

    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> = flowOf(
        completedSessions.count { (it.completedAtTimestamp ?: Long.MIN_VALUE) >= startTimestamp }
    )
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> =
        completedSessions.sortedByDescending { it.completedAtTimestamp }.take(limit)

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession =
        error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(
        setId: String,
        performance: SetPerformanceInput
    ) = Unit

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary = error("Not used")

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = Unit
}
