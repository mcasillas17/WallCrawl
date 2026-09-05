package wallcrawl.elopenmike.com.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupPolicyResourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun packagedApplication_disablesBackupWithoutACustomAgent() {
        assertThat(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
        withPackagedApplication { manifest ->
            assertThat(manifest.getAttributeValue(ANDROID_NAMESPACE, "allowBackup")).isNotNull()
            assertThat(manifest.getAttributeBooleanValue(ANDROID_NAMESPACE, "allowBackup", true))
                .isFalse()
            assertThat(manifest.getAttributeValue(ANDROID_NAMESPACE, "backupAgent")).isNull()
        }
    }

    @Test
    fun referencedLegacyRules_excludeEveryAppDataDomain() {
        withReferencedRules("fullBackupContent") { rules ->
            assertThat(rules.name).isEqualTo("full-backup-content")
            assertAllDomainsExcluded(rules)
            assertThat(rules.next()).isEqualTo(XmlPullParser.END_DOCUMENT)
        }
    }

    @Test
    fun referencedModernRules_excludeEveryDomainFromCloudAndDeviceTransfer() {
        withReferencedRules("dataExtractionRules") { rules ->
            assertThat(rules.name).isEqualTo("data-extraction-rules")
            assertThat(rules.attributeCount).isEqualTo(0)
            val sections = mutableListOf<String>()
            while (rules.nextTag() == XmlPullParser.START_TAG) {
                sections += rules.name
                assertAllDomainsExcluded(rules)
            }
            assertThat(sections).containsExactly("cloud-backup", "device-transfer")
            assertThat(rules.name).isEqualTo("data-extraction-rules")
            assertThat(rules.next()).isEqualTo(XmlPullParser.END_DOCUMENT)
        }
    }

    private fun assertAllDomainsExcluded(rules: XmlResourceParser) {
        val section = rules.name
        assertWithMessage("$section must not have conditional attributes")
            .that(rules.attributeCount).isEqualTo(0)
        val excludedDomains = mutableListOf<String>()
        while (rules.nextTag() == XmlPullParser.START_TAG) {
            assertWithMessage("$section must contain only unconditional exclusions")
                .that(rules.name).isEqualTo("exclude")
            assertThat(rules.attributeCount).isEqualTo(2)
            assertWithMessage("$section must exclude each entire domain, not a filename")
                .that(rules.getAttributeValue(null, "path")).isEqualTo(".")
            val domain = rules.getAttributeValue(null, "domain")
            assertThat(domain).isNotNull()
            excludedDomains += requireNotNull(domain)
            assertThat(rules.nextTag()).isEqualTo(XmlPullParser.END_TAG)
            assertThat(rules.name).isEqualTo("exclude")
        }
        assertThat(rules.name).isEqualTo(section)
        assertWithMessage("$section must exclude all credential/device-protected and external data")
            .that(excludedDomains).containsExactlyElementsIn(BACKUP_DOMAINS)
    }

    private fun withReferencedRules(attribute: String, check: (XmlResourceParser) -> Unit) {
        withPackagedApplication { manifest ->
            val resourceId = manifest.getAttributeResourceValue(ANDROID_NAMESPACE, attribute, 0)
            assertWithMessage("Packaged application must reference XML via android:$attribute")
                .that(resourceId).isNotEqualTo(0)
            context.resources.getXml(resourceId).use { rules ->
                while (rules.eventType != XmlPullParser.START_TAG &&
                    rules.eventType != XmlPullParser.END_DOCUMENT
                ) {
                    rules.next()
                }
                assertThat(rules.eventType).isEqualTo(XmlPullParser.START_TAG)
                check(rules)
            }
        }
    }

    private fun withPackagedApplication(check: (XmlResourceParser) -> Unit) {
        // Read the target APK's merged binary manifest, not the test APK or source XML.
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { manifest ->
            while (manifest.next() != XmlPullParser.END_DOCUMENT) {
                if (manifest.eventType == XmlPullParser.START_TAG && manifest.depth == 1) {
                    assertThat(manifest.name).isEqualTo("manifest")
                    assertWithMessage("Read the target application's manifest, not another APK")
                        .that(manifest.getAttributeValue(null, "package"))
                        .isEqualTo(context.packageName)
                }
                if (manifest.eventType == XmlPullParser.START_TAG &&
                    manifest.name == "application" && manifest.depth == 2
                ) {
                    check(manifest)
                    return
                }
            }
            throw AssertionError("Packaged manifest has no application element")
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        // Recheck this documented domain list when raising compileSdk (currently 37):
        // https://developer.android.com/identity/data/autobackup#xml-include-exclude
        val BACKUP_DOMAINS = listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref"
        )
    }
}
