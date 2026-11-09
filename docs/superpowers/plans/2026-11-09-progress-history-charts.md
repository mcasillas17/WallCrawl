# Exercise history on Progress

Implement the approved chart-first Progress design on the existing Android tab. The user's own completed workout sets supply the exercise picker, chart points and session counts.

1. Use the previous-set eligibility rules in Room. Read choices across the full history, then load and aggregate only the selected exercise. Preserve canonical IDs, session units, recorded measurement shape and deterministic set selection.
2. Filter the observations using the device's calendar. Default to Month and offer Monday-start Week, Year, All time and an inclusive Custom range. Keep the selection across rotation and recreation. Test DST, midnight rollover, old sessions, and empty ranges.
3. Put the Exercise label, picker and range controls above the plots. Both weighted charts select the same session. Draw bodyweight reps on their own. Support tap, previous/next, TalkBack, large type, and English and Spanish. Keep the history and accounting destinations in the overflow menu.
4. Exercise the production route using seeded Room history on Android. Run unit tests, lint, Room checks and instrumentation. Update the walkthrough, architecture and native screenshots, then deliver the reviewed branch for merge.
