package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.model.DumbbellRack
import wallcrawl.elopenmike.com.core.model.WeightUnit

/** Strict bounded row encoding; corrupt or newer racks cannot silently become unknown. */
internal object DumbbellRackCodec {
    private val rowPattern = Regex("""\{"unit":"(LBS|KG)","weights":\[([0-9.,]+)\]\}""")

    fun encode(rack: DumbbellRack): String {
        rack.validate()
        return "{\"unit\":\"${rack.unit.name}\",\"weights\":[${rack.weights.joinToString(",")}]}"
    }

    fun decode(raw: String): DumbbellRack {
        require(raw.length <= 4096)
        val match = rowPattern.matchEntire(raw)
            ?: throw IllegalArgumentException("Unsupported dumbbell rack encoding.")
        val values = match.groupValues[2].split(',')
        require(values.size in 1..DumbbellRack.MAX_WEIGHTS)
        val rack = DumbbellRack(WeightUnit.valueOf(match.groupValues[1]), values.map(String::toDouble))
        require(encode(rack) == raw) { "Noncanonical dumbbell rack encoding." }
        return rack
    }
}
