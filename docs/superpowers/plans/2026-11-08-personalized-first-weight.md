# Personalized first-weight implementation plan

Implement the approved first-use behavior in the guided set logger on PR #109. Use the existing Kotlin planner, Room profile, archive and Compose controls. The [guided training design](../specs/2026-11-05-guided-training-ux-design.md) defines the interaction.

1. Persist an optional per-dumbbell rack with its original unit in the profile. Accept bounded, distinct positive loads. Keep unit changes and backup/restore lossless and cover legacy profiles.
2. Expose the rack under Profile → Equipment. Support entry, correction, removal and both shipped languages. Provide an accessible control for arbitrary rack sizes and weights.
3. Implement deterministic first-use guidance. Check recent working sets from an explicitly comparable lift, eligibility, return status, the prescription and equipment. Keep a reference separate from a target. Rank only weights actually on the rack, never invent a load conversion or write a suggestion as performance.
4. Connect active workout state to the guidance. Keep manual inline adjustment, exact-exercise prescriptions and carry-forward. Test unknown equipment, mixed units, absent history, pain restrictions and old backups. Run Gradle and native tests, then update the existing PR and verify its checks.
