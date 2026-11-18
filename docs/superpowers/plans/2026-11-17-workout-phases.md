# Workout phases implementation plan

**Goal** Add deterministic warm-ups and cooldowns to generated WallCrawl workouts.

**Design** [Generated workout warm-up and cooldown](../specs/2026-11-17-workout-phases-design.md)

- [x] Add planner tests for workout order, time, goal-based selection, exclusions, equipment, joint and capability restrictions, and condition plans. Run the focused JVM test and confirm it fails before adding phases.
- [x] Add the phase policy and feed the allowlisted catalog candidates through generation and recommendation validation. Reuse the duration estimator to account for all scheduled work.
- [x] Persist both purposes through the existing workout and archive fields. Add localized phase labels to the preview, guided workout and history.
- [x] Run the focused tests, full debug JVM suite, lint and debug assemble. Verify the guided flow on an available Android device.
