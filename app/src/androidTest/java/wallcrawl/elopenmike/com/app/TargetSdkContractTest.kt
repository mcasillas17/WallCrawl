package wallcrawl.elopenmike.com.app

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import wallcrawl.elopenmike.com.R

/**
 * The installed app keeps the platform contract that docs/android-target-sdk-37.md validated.
 *
 * Targeting a newer API opts into its runtime behaviour, so the SDK levels are pinned to the
 * ones with device evidence; changing either needs a new compatibility review. The opt-outs
 * below are the shortcuts that would quietly undo the validated behaviour.
 */
@RunWith(AndroidJUnit4::class)
class TargetSdkContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun installedApplication_keepsTheValidatedSdkLevels() {
        assertThat(context.packageName).isEqualTo("wallcrawl.elopenmike.com")
        assertWithMessage("targetSdk is the device-validated API 37; a change needs a new review")
            .that(context.applicationInfo.targetSdkVersion).isEqualTo(37)
        assertWithMessage("minSdk 26 keeps Android 8.0 devices supported")
            .that(context.applicationInfo.minSdkVersion).isEqualTo(26)
    }

    @Test
    fun packagedManifest_doesNotOptOutOfPredictiveBackOrLargeScreenResizing() {
        // The target APK's merged binary manifest, so library-contributed activities count too.
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { manifest ->
            var activities = 0
            while (manifest.next() != XmlPullParser.END_DOCUMENT) {
                if (manifest.eventType != XmlPullParser.START_TAG) continue
                val element = manifest.name
                val name = manifest.getAttributeValue(ANDROID_NAMESPACE, "name")
                if (manifest.depth == 1) {
                    assertThat(element).isEqualTo("manifest")
                    assertWithMessage("Read the target application's manifest, not another APK")
                        .that(manifest.getAttributeValue(null, "package"))
                        .isEqualTo(context.packageName)
                }
                if (element == "application" || element == "activity" || element == "activity-alias") {
                    assertWithMessage("$element $name must not opt out of predictive back")
                        .that(manifest.getAttributeBooleanValue(ANDROID_NAMESPACE, "enableOnBackInvokedCallback", true))
                        .isTrue()
                }
                if (element == "activity" || element == "activity-alias") {
                    activities++
                    for (restriction in RESIZING_RESTRICTIONS) {
                        assertWithMessage("activity $name must not declare android:$restriction")
                            .that(manifest.getAttributeValue(ANDROID_NAMESPACE, restriction)).isNull()
                    }
                }
                if (element == "property") {
                    assertWithMessage("The large-screen resizability opt-out must stay absent")
                        .that(name).isNotEqualTo(ALLOW_RESTRICTED_RESIZABILITY)
                }
            }
            assertWithMessage("Read the packaged activities, not an empty manifest")
                .that(activities).isGreaterThan(0)
        }
    }

    // The attribute exists from API 35. Targeting 36+ disables it on Android 16 and later, but
    // Android 15 still honours it, so setting it would undo edge-to-edge on those devices.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Test
    fun appTheme_doesNotOptOutOfEdgeToEdge() {
        val attributes = context.obtainStyledAttributes(
            R.style.Theme_WallCrawl,
            intArrayOf(android.R.attr.windowOptOutEdgeToEdgeEnforcement)
        )
        try {
            assertThat(attributes.getBoolean(0, false)).isFalse()
        } finally {
            attributes.recycle()
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val ALLOW_RESTRICTED_RESIZABILITY =
            "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
        val RESIZING_RESTRICTIONS =
            listOf("screenOrientation", "resizeableActivity", "minAspectRatio", "maxAspectRatio")
    }
}
