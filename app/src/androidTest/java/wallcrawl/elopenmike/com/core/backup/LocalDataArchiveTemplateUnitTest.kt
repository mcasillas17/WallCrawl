package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class LocalDataArchiveTemplateUnitTest {

    private fun archive(version: Int = LocalDataArchiveFormat.ARCHIVE_VERSION, unit: WeightUnit? = null) =
        LocalDataArchiveFixtures.archive().let { base ->
            base.copy(
                metadata = base.metadata.copy(archiveVersion = version),
                snapshot = base.snapshot.copy(
                    templates = base.snapshot.templates.mapIndexed { index, template ->
                        // One recorded unit beside one legacy template, in the same document.
                        if (index == 0) template.copy(weightUnit = unit) else template
                    },
                    recommendationRecords =
                        if (version == 1) emptyList() else base.snapshot.recommendationRecords
                )
            )
        }

    @Test fun currentFormatRoundTripsRecordedUnitsAndLeavesLegacyTemplatesUnknown() {
        WeightUnit.entries.forEach { unit ->
            val original = archive(unit = unit)
            val encoded = bytes(original)
            val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(encoded))
            assertEquals(original, restored)
            assertEquals(unit, restored.snapshot.templates[0].weightUnit)
            assertNull(restored.snapshot.templates[1].weightUnit)
            assertTrue(encoded.decodeToString().contains("\"weightUnit\":\"${unit.name}\""))
        }
        // A legacy template writes no field at all, rather than an invented or null unit,
        // wherever in the template object it might be written. Sessions have their own unit.
        val legacyOnly = org.json.JSONObject(bytes(archive(unit = null)).decodeToString())
        val templates = templatesIn(legacyOnly)
        assertTrue(templates.isNotEmpty())
        templates.forEach { assertFalse(it.has("weightUnit")) }
    }

    @Test fun earlierFormatsNeverCarryATemplateUnitAndKeepTheirChecksums() {
        for (version in 1 until LocalDataArchiveFormat.ARCHIVE_VERSION) {
            val original = archive(version)
            val encoded = bytes(original)
            val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(encoded))
            assertTrue(restored.snapshot.templates.all { it.weightUnit == null })
            assertArrayEquals(encoded, bytes(restored))
            assertThrows(IllegalArgumentException::class.java) {
                bytes(archive(version, WeightUnit.KG))
            }
            // The field is gated by the document's own declared version.
            val smuggled = encoded.decodeToString().replaceFirst(
                "\"name\":\"Upper Focus\"",
                "\"name\":\"Upper Focus\",\"weightUnit\":\"KG\""
            )
            assertNotEquals(encoded.decodeToString(), smuggled)
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(smuggled.encodeToByteArray()))
            }
            assertNotEquals(ArchiveRejection.CHECKSUM_MISMATCH, error.rejection)
        }
    }

    @Test fun unknownUnitNamesAndDuplicatesAreRejectedBeforeTheChecksum() {
        val valid = bytes(archive(unit = WeightUnit.KG)).decodeToString()
        listOf(
            "\"weightUnit\":\"KG\"" to "\"weightUnit\":\"STONE\"",
            "\"weightUnit\":\"KG\"" to "\"weightUnit\":\"kg\"",
            "\"weightUnit\":\"KG\"" to "\"weightUnit\":null",
            "\"weightUnit\":\"KG\"" to "\"weightUnit\":\"KG\",\"weightUnit\":\"KG\""
        ).forEach { (from, to) ->
            assertTrue(valid.contains(from))
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(valid.replaceFirst(from, to).encodeToByteArray()))
            }
            assertNotEquals(ArchiveRejection.CHECKSUM_MISMATCH, error.rejection)
        }
    }

    @Test fun aKilogramTemplateAboveTheKilogramBoundIsRefusedWhileTheSameLegacyNumberRestores() {
        // 4 535.92 kg is the kilogram bound; one more hundredth is not a kilogram template.
        val valid = bytes(archive(unit = WeightUnit.KG)).decodeToString()
        val template = valid.indexOf("\"name\":\"Upper Focus\"")
        val load = valid.indexOf("\"targetWeight\":70.0", template)
        assertTrue(template >= 0 && load > template)
        val overBound = valid.replaceRange(load, load + "\"targetWeight\":70.0".length, "\"targetWeight\":4535.93")
        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(overBound.encodeToByteArray()))
        }
        assertEquals(ArchiveRejection.INVALID_VALUE, error.rejection)

        // A template with no recorded unit is not held to the kilogram bound.
        val legacy = archive(unit = null).let { base ->
            base.copy(
                snapshot = base.snapshot.copy(
                    templates = base.snapshot.templates.mapIndexed { index, it ->
                        if (index != 0) it else it.copy(
                            exercises = it.exercises.mapIndexed { position, exercise ->
                                if (position != 0) exercise else exercise.copy(
                                    prescription = exercise.prescription.copy(targetWeight = 5000.0)
                                )
                            }
                        )
                    }
                )
            )
        }
        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(legacy)))
        assertEquals(5000.0, restored.snapshot.templates[0].exercises[0].prescription.targetWeight)
    }

    /** Every template object in an encoded archive, wherever its "templates" array sits. */
    private fun templatesIn(node: Any?): List<org.json.JSONObject> = when (node) {
        is org.json.JSONObject -> node.keys().asSequence().toList().flatMap { key ->
            val value = node.get(key)
            if (key == "templates" && value is org.json.JSONArray) {
                (0 until value.length()).map { value.getJSONObject(it) }
            } else {
                templatesIn(value)
            }
        }
        is org.json.JSONArray -> (0 until node.length()).flatMap { templatesIn(node.get(it)) }
        else -> emptyList()
    }

    private fun bytes(archive: LocalDataArchive): ByteArray =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(archive) { sink } }.toByteArray()
}
