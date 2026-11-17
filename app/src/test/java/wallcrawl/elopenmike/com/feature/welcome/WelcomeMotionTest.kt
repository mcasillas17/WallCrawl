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

    @Test fun aboutEmblemReachesHeaderAt560msAndArcLapsBackToRest() {
        assertThat(M.flight(560f)).isEqualTo(1f)
        assertThat(M.aboutArcHead(0f) - M.aboutArcTail(0f)).isEqualTo(90f)
        val end = EmblemFrame.about(M.ABOUT_END)
        assertThat(end.arcHead).isWithin(eps).of(EmblemFrame.REST.arcHead)
        assertThat(end.arcTail).isWithin(eps).of(EmblemFrame.REST.arcTail)
    }

    @Test fun aboutEntranceIsCompleteAtItsEnd() {
        val t = M.ABOUT_END
        assertThat(M.footer(t)).isEqualTo(1f)
        assertThat(M.card(t)).isEqualTo(1f)
        assertThat((0..2).all { M.feature(it, t) == 1f && M.tileScale(it, t) == 1f && M.chipScale(it, t) == 1f }).isTrue()
    }
}
