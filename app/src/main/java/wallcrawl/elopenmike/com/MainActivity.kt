package wallcrawl.elopenmike.com

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import wallcrawl.elopenmike.com.app.WallCrawlApp
import wallcrawl.elopenmike.com.app.shouldShowIntro
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.feature.welcome.SplashHandoff
import wallcrawl.elopenmike.com.feature.welcome.isReducedMotion
import wallcrawl.elopenmike.com.feature.welcome.isTouchExploration

/**
 * AppCompatActivity rather than ComponentActivity: below Android 13 the per-app language
 * APIs are applied by the AppCompat delegate, so a plain ComponentActivity would keep
 * rendering in the device language whatever the selector said.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps the starting theme for Theme.WallCrawl.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as WallCrawlApplication).container
        // Hoisted so the splash can stay up until the profile is known: no spinner flash, and
        // the start destination is already decided when the app appears.
        val profileFlow = appContainer.userProfileRepository.getUserProfile()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splashScreen.setKeepOnScreenCondition { profileFlow.value == null }

        // A recreated activity shows no splash, so there is nothing to wait for.
        val handoff = SplashHandoff(if (savedInstanceState == null) SplashHandoff.State.Pending else SplashHandoff.State.None)
        splashScreen.setOnExitAnimationListener { provider ->
            // Let the icon animation finish before leaving (both 0 below Android 12). The start
            // is wall-clock time, not uptime; clamped so a different clock can never stall launch.
            val duration = provider.iconAnimationDurationMillis
            val remaining = (provider.iconAnimationStartMillis + duration - System.currentTimeMillis()).coerceIn(0L, duration)
            val exit = Runnable { exitSplash(provider, profileFlow.value, handoff) }
            if (remaining > 0) provider.view.postDelayed(exit, remaining) else exit.run()
        }

        setContent {
            val profile by profileFlow.collectAsState()
            val themePreference = profile?.themePreference ?: ThemePreference.SYSTEM
            LaunchedEffect(profile?.themePreference) {
                profile?.themePreference?.let(::matchSplashToTheme)
            }

            WallCrawlTheme(themePreference = themePreference) {
                WallCrawlApp(container = appContainer, profile = profile, splashHandoff = handoff)
            }
        }
    }

    private fun exitSplash(provider: SplashScreenViewProvider, profile: UserProfile?, handoff: SplashHandoff) {
        val animatedIntro = profile != null && shouldShowIntro(profile) &&
            !isReducedMotion(this) && !isTouchExploration(this)
        if (animatedIntro) {
            // First launch: no zoom. The intro draws the same emblem in the same place and
            // removes the splash once it has (docs/launch-animation.md, Wiring 4).
            val icon = provider.iconView
            val location = IntArray(2).also(icon::getLocationInWindow)
            val center = Offset(location[0] + icon.width / 2f, location[1] + icon.height / 2f)
            handoff.handTo(center, icon.width * EMBLEM_IN_ICON) { provider.remove() }
        } else {
            handoff.none()
            zoomThrough(provider)
        }
    }

    /** The emblem dips, then zooms through its hub while the app fades and settles in. */
    private fun zoomThrough(provider: SplashScreenViewProvider) {
        val icon = provider.iconView
        val splashBackground = provider.view.background?.mutate()
        val content = findViewById<View>(android.R.id.content)
        icon.pivotX = icon.width * HUB_PIVOT_X
        icon.pivotY = icon.height * HUB_PIVOT_Y
        content.alpha = 0f
        content.scaleX = 1.06f
        content.scaleY = 1.06f

        fun scale(view: View, from: Float, to: Float) = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, from, to), ObjectAnimator.ofFloat(view, View.SCALE_Y, from, to))
        }
        val dip = scale(icon, 1f, 0.9f).apply { duration = 120; interpolator = PathInterpolator(0.4f, 0f, 0.6f, 1f) }
        val zoom = scale(icon, 0.9f, 10f).apply { startDelay = 120; duration = 400; interpolator = PathInterpolator(0.7f, 0f, 0.84f, 0f) }
        val iconFade = ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f).apply { startDelay = 300; duration = 220; interpolator = LinearInterpolator() }
        // Only the splash background fades here; fading the splash view would take the icon with it.
        val backgroundFade = ValueAnimator.ofInt(255, 0).apply {
            startDelay = 200; duration = 220; interpolator = PathInterpolator(0.3f, 0f, 1f, 1f)
            addUpdateListener { splashBackground?.alpha = it.animatedValue as Int }
        }
        val contentFade = ObjectAnimator.ofFloat(content, View.ALPHA, 0f, 1f).apply { startDelay = 200; duration = 180; interpolator = LinearInterpolator() }
        val contentSettle = scale(content, 1.06f, 1f).apply { startDelay = 200; duration = 400; interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f) }

        AnimatorSet().apply {
            playTogether(dip, zoom, iconFade, backgroundFade, contentFade, contentSettle)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    content.alpha = 1f
                    content.scaleX = 1f
                    content.scaleY = 1f
                    provider.remove()
                }
            })
            start()
        }
    }

    /**
     * The splash follows the system night mode, the app its own theme setting. On Android 12+
     * tell the system the app's choice so the next splash matches; 8–11 have no equivalent.
     */
    private fun matchSplashToTheme(preference: ThemePreference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (preference) {
            ThemePreference.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemePreference.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemePreference.LIGHT -> UiModeManager.MODE_NIGHT_NO
        }
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode)
    }

    private companion object {
        /** The emblem is drawn 168 dp wide in the 288 dp splash icon canvas. */
        const val EMBLEM_IN_ICON = 168f / 288f
        /** Hub centre (46, 54) of the 100-unit emblem, as a fraction of the icon canvas. */
        const val HUB_PIVOT_X = 0.4767f
        const val HUB_PIVOT_Y = 0.5233f
    }
}
