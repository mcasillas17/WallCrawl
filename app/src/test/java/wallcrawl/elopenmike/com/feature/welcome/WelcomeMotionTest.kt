package wallcrawl.elopenmike.com.feature.welcome

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.feature.welcome.WelcomeMotion as M

/** Checks the timelines against the tables in docs/welcome-screens-v2.md §6–§7. */
class WelcomeMotionTest {
    private val eps = 1e-3f

    @Test fun segClampsAndEases() {
        assertThat(M.seg(-10f, 0f, 100f)).isEqualTo(0f)
        assertThat(M.seg(50f, 0f, 100f)).isWithin(eps).of(0.5f)
        assertThat(M.seg(500f, 0f, 100f)).isEqualTo(1f)
        assertThat(M.seg(50f, 0f, 100f, M.IN_OUT)).isWithin(0.01f).of(0.5f)
    }

    @Test fun ringDrawsOverItsFirst450ms() {
        assertThat(M.ring(0f)).isEqualTo(0f)
        assertThat(M.ring(450f)).isEqualTo(1f)
    }

    @Test fun spokesAndStrandsAreStaggered() {
        assertThat(M.spoke(0, 150f)).isEqualTo(0f)
        assertThat(M.spoke(7, 324f)).isEqualTo(0f)
        assertThat(M.spoke(7, 525f)).isEqualTo(1f)
        assertThat(M.strand(7, 655f)).isEqualTo(1f)
    }

    @Test fun energyArcEndsResting12To3() {
        assertThat(M.arcHead(0f) - M.arcTail(0f)).isEqualTo(0f)
        assertThat(M.arcHead(1140f)).isWithin(eps).of(450f)
        assertThat(M.arcTail(1140f)).isWithin(eps).of(360f)
        val rest = EmblemFrame.REST
        assertThat(rest.arcHead - rest.arcTail).isEqualTo(90f)
    }

    @Test fun emblemKicksThenSettlesInto120dp() {
        assertThat(M.introEmblemScale(0f)).isEqualTo(1f)
        assertThat(M.introEmblemScale(1260f)).isWithin(eps).of(1.03f)
        assertThat(M.introEmblemScale(1720f)).isWithin(eps).of(0.714f)
        assertThat(M.introEmblemScale(M.INTRO_END)).isWithin(eps).of(120f / 168f)
    }

    @Test fun crawlPillOvershoots() {
        assertThat(M.pillScale(1540f)).isEqualTo(0.5f)
        assertThat(M.pillScale(1700f)).isGreaterThan(1f)
        assertThat(M.pillScale(1920f)).isWithin(eps).of(1f)
    }

    @Test fun everythingIsSettledBeforeTheFadeOut() {
        val t = M.INTRO_SETTLED
        assertThat(M.wall(t)).isEqualTo(1f)
        assertThat(M.tagline2(t)).isEqualTo(1f)
        assertThat(M.introChrome(t)).isEqualTo(1f)
        assertThat(M.introChrome(M.INTRO_END)).isEqualTo(0f)
        val frame = EmblemFrame.intro(t)
        assertThat(frame.streaks.all { it == 1f }).isTrue()
        assertThat(frame.arcHead - frame.arcTail).isWithin(eps).of(90f)
    }

    @Test fun introLasts3500msWithAReadingHold() {
        assertThat(M.INTRO_END).isEqualTo(3500f)
        assertThat(M.tagline2(2120f)).isEqualTo(0f)
        assertThat(M.tagline2(2620f)).isEqualTo(1f)
        assertThat((2620..3200 step 20).all { M.introChrome(it.toFloat()) == 1f }).isTrue()
        assertThat(M.introChrome(3500f)).isEqualTo(0f)
    }

    @Test fun reducedIntroFadesInHoldsUntil3200AndFadesOut() {
        assertThat(M.reducedIntroAlpha(0f)).isEqualTo(0f)
        assertThat(M.reducedIntroAlpha(1000f)).isEqualTo(1f)
        assertThat(M.reducedIntroAlpha(3200f)).isEqualTo(1f)
        assertThat(M.REDUCED_INTRO_END).isEqualTo(3500f)
        assertThat(M.reducedIntroAlpha(3500f)).isEqualTo(0f)
    }

    @Test fun aboutPlaysOver3000RealMs() {
        assertThat(M.ABOUT_END).isEqualTo(3000f)
        assertThat(M.footer(2999f)).isLessThan(1f)
        assertThat(M.footer(3000f)).isEqualTo(1f)
    }

    @Test fun aboutEmblemReachesHeaderAt900msAndArcLapsBackToRest() {
        assertThat(M.flight(899f)).isLessThan(1f)
        assertThat(M.flight(900f)).isEqualTo(1f)
        assertThat(M.aboutArcHead(0f) - M.aboutArcTail(0f)).isEqualTo(90f)
        assertThat(M.aboutArcTail(1050f)).isWithin(eps).of(360f)
        val end = EmblemFrame.about(M.ABOUT_END)
        assertThat(end.arcHead).isWithin(eps).of(EmblemFrame.REST.arcHead)
        assertThat(end.arcTail).isWithin(eps).of(EmblemFrame.REST.arcTail)
    }

    @Test fun arrivingViaSkipFadesTheEmblemInOver250ms() {
        assertThat(M.arrivedViaSkipAlpha(0f)).isEqualTo(0f)
        assertThat(M.arrivedViaSkipAlpha(125f)).isWithin(eps).of(0.5f)
        assertThat(M.arrivedViaSkipAlpha(250f)).isEqualTo(1f)
    }

    @Test fun inputsPopLeftToRightThenConnectorsMeet() {
        assertThat((0..2).map { M.inputAlpha(it, 750f) }).isInOrder(Comparator.reverseOrder<Float>())
        assertThat(M.inputScale(0, 700f)).isWithin(eps).of(0.6f)
        assertThat((0..2).any { M.inputScale(it, 1150f) > 1f }).isTrue() // overshoot
        assertThat((0..2).all { M.connector(it, 1100f) == 0f }).isTrue()
        for (k in 0..2) {
            assertThat(M.connector(k, 1549f + 60f * k)).isLessThan(1f)
            assertThat(M.connector(k, 1550f + 60f * k)).isEqualTo(1f)
        }
    }

    @Test fun workoutRingsAndGlowsBriefly() {
        assertThat(M.workoutGlow(0f)).isEqualTo(0f)
        assertThat(M.workoutAlpha(1450f)).isEqualTo(0f)
        assertThat(M.workoutScale(1800f)).isGreaterThan(1f) // overshoot
        assertThat(M.workoutRing(2000f)).isEqualTo(1f)
        val peak = (1450..2350 step 10).maxBy { M.workoutGlow(it.toFloat()) }
        assertThat(peak).isIn(1690..1760)
        assertThat(M.workoutGlow(2350f)).isEqualTo(0f)
        assertThat(M.workoutGlow(2800f)).isEqualTo(0f)
    }

    @Test fun arrowThenLogThenLoopBackToProgressWithAPing() {
        assertThat(M.down(1850f)).isEqualTo(0f)
        assertThat(M.down(2150f)).isEqualTo(1f)
        assertThat(M.logAlpha(2000f)).isEqualTo(0f)
        assertThat(M.loop(2150f)).isEqualTo(0f)
        assertThat(M.loop(2650f)).isEqualTo(1f)
        assertThat(M.ping(2649f)).isEqualTo(0f)
        assertThat(M.ping(3000f)).isEqualTo(1f)
    }

    @Test fun badgesPopInOrder() {
        assertThat((0..3).map { M.badgeAlpha(it, 2350f) }).isInOrder(Comparator.reverseOrder<Float>())
        assertThat(M.badgeScale(0, 2300f)).isWithin(eps).of(0.8f)
    }

    @Test fun aboutEntranceIsCompleteAtItsEnd() {
        val t = M.ABOUT_END
        assertThat(M.headline(t)).isEqualTo(1f)
        assertThat(M.workoutScale(t)).isEqualTo(1f)
        assertThat(M.workoutAlpha(t)).isEqualTo(1f)
        assertThat(M.workoutRing(t)).isEqualTo(1f)
        assertThat(M.workoutGlow(t)).isEqualTo(0f)
        assertThat(M.down(t)).isEqualTo(1f)
        assertThat(M.logScale(t)).isEqualTo(1f)
        assertThat(M.loop(t)).isEqualTo(1f)
        assertThat(M.footer(t)).isEqualTo(1f)
        assertThat((0..2).all { M.inputScale(it, t) == 1f && M.inputAlpha(it, t) == 1f && M.connector(it, t) == 1f }).isTrue()
        assertThat((0..3).all { M.badgeScale(it, t) == 1f && M.badgeAlpha(it, t) == 1f }).isTrue()
    }
}
