package wallcrawl.elopenmike.com.feature.welcome

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.feature.welcome.WelcomeMotion as M

/** Checks the timelines against the tables in docs/first-launch-intro.md §7–§8. */
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

    @Test fun reducedIntroFadesInHoldsAndFadesOut() {
        assertThat(M.reducedIntroAlpha(0f)).isEqualTo(0f)
        assertThat(M.reducedIntroAlpha(300f)).isEqualTo(1f)
        assertThat(M.reducedIntroAlpha(1700f)).isEqualTo(1f)
        assertThat(M.reducedIntroAlpha(M.REDUCED_INTRO_END)).isEqualTo(0f)
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

    @Test fun headlineThenBuilderCardEnter() {
        assertThat(M.headline(450f)).isEqualTo(0f)
        assertThat(M.headline(451f)).isGreaterThan(0f)
        assertThat(M.builderCard(650f)).isEqualTo(0f)
        assertThat(M.builderCard(651f)).isGreaterThan(0f)
        assertThat(M.builderCard(1250f)).isEqualTo(1f)
    }

    @Test fun inputsPopLeftToRightThenConnectorsMeet() {
        assertThat((0..2).map { M.inputAlpha(it, 900f) }).isInOrder(Comparator.reverseOrder<Float>())
        assertThat(M.inputScale(0, 850f)).isWithin(eps).of(0.6f)
        assertThat((0..2).any { M.inputScale(it, 1300f) > 1f }).isTrue() // overshoot
        assertThat((0..2).all { M.connector(it, 1250f) == 0f }).isTrue()
        assertThat((0..2).all { M.connector(it, 1870f) == 1f }).isTrue()
    }

    @Test fun outputPopsWithABriefGlow() {
        assertThat(M.outputGlow(0f)).isEqualTo(0f)
        assertThat(M.outputAlpha(1650f)).isEqualTo(0f)
        assertThat(M.outputScale(2100f)).isGreaterThan(1f) // overshoot
        val peak = (1650..2550 step 10).maxBy { M.outputGlow(it.toFloat()) }
        assertThat(peak).isIn(1890..1960)
        assertThat(M.outputGlow(1950f)).isWithin(eps).of(0.45f)
        assertThat(M.outputGlow(2550f)).isEqualTo(0f)
        assertThat(M.outputGlow(2800f)).isEqualTo(0f)
    }

    @Test fun aboutEntranceIsCompleteAtItsEnd() {
        val t = M.ABOUT_END
        assertThat(M.headline(t)).isEqualTo(1f)
        assertThat(M.builderCard(t)).isEqualTo(1f)
        assertThat(M.outputScale(t)).isEqualTo(1f)
        assertThat(M.outputAlpha(t)).isEqualTo(1f)
        assertThat(M.outputGlow(t)).isEqualTo(0f)
        assertThat(M.privacyCard(t)).isEqualTo(1f)
        assertThat(M.footer(t)).isEqualTo(1f)
        assertThat((0..2).all { M.inputScale(it, t) == 1f && M.connector(it, t) == 1f && M.chipScale(it, t) == 1f }).isTrue()
        assertThat((0..1).all { M.feature(it, t) == 1f && M.tileScale(it, t) == 1f }).isTrue()
    }
}
