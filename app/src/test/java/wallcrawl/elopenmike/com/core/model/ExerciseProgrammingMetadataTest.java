package wallcrawl.elopenmike.com.core.model;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Java callers exercise the nullable boundary without compiler-enforced Kotlin null checks. */
public class ExerciseProgrammingMetadataTest {
    @Test public void repTypesRequireRangeAndTimedTypesForbidIt() {
        for (ExerciseType type : ExerciseType.values()) {
            boolean timed = type == ExerciseType.DURATION || type == ExerciseType.DISTANCE_DURATION;
            if (timed) {
                assertNull(exercise(type, null).getProgramming().getRecommendedRepRange());
                assertThrows(IllegalArgumentException.class, () -> exercise(type, new RepRange(6, 12)));
            } else {
                assertThrows(IllegalArgumentException.class, () -> exercise(type, null));
                assertEquals(new RepRange(6, 12), exercise(type, new RepRange(6, 12))
                    .getProgramming().getRecommendedRepRange());
                assertThrows(IllegalArgumentException.class, () -> exercise(type, new RepRange(1, 1001)));
            }
        }
    }

    @Test public void timedTypesRejectFabricatedRepRanges() {
        assertThrows(IllegalArgumentException.class, () -> exercise(ExerciseType.DURATION, new RepRange(6, 12)));
        assertThrows(IllegalArgumentException.class, () -> exercise(ExerciseType.DISTANCE_DURATION, new RepRange(6, 12)));
    }

    @Test public void repRangeStillRequiresPositiveOrderedValues() {
        assertThrows(IllegalArgumentException.class, () -> new RepRange(0, 12));
        assertThrows(IllegalArgumentException.class, () -> new RepRange(12, 6));
    }

    private Exercise exercise(ExerciseType type, RepRange range) {
        ExerciseProgrammingMetadata programming = new ExerciseProgrammingMetadata(
            Collections.singletonList(Collections.singletonList("Bodyweight")),
            MovementPattern.CORE, Difficulty.BEGINNER, MechanicsType.ISOLATION,
            range, 1, ProgressionType.DURATION, Collections.emptyList(), "Fixture coaching.");
        return new Exercise("sample", null, "Sample", Collections.emptyList(),
            Collections.singletonList("Core"), Collections.emptyList(), Collections.singletonList("Bodyweight"),
            type, false, programming, null);
    }
}
