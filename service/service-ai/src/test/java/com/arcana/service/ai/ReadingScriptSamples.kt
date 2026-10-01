package com.arcana.service.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes sample readings as scripts for `tools/reading-cli`, which runs them through a model on
 * a desktop. It does nothing unless ARCANA_SCRIPT_DIR names a folder to write into:
 *
 *     ARCANA_SCRIPT_DIR=/tmp/scripts ./gradlew :service:service-ai:testDebugUnitTest --tests '*ReadingScriptSamples'
 */
class ReadingScriptSamples {
    private val samples = mapOf(
        "daily" to Fixtures.reading("one_card", "cups_08!"),
        "daily-question" to Fixtures.reading("one_card", "major_16_tower", question = "Should I take the job in another city?"),
        "past-present-future" to Fixtures.reading(
            "three_card_ppf", "major_09_hermit!", "pentacles_06", "swords_01_ace!",
            question = "Why do I keep losing touch with my friends?",
        ),
        "situation-action-outcome" to Fixtures.reading(
            "three_card_sao", "wands_10", "major_12_hanged_man", "cups_03",
            question = "I am burned out at work. What should I do?",
            tone = InterpretationTone.PRACTICAL,
        ),
        "mind-body-spirit-poetic" to Fixtures.reading(
            "three_card_mbs", "swords_09", "pentacles_04!", "major_17_star",
            tone = InterpretationTone.POETIC,
        ),
        "relationship-gentle" to Fixtures.reading(
            "relationship_5", "cups_queen", "swords_knight!", "cups_02", "pentacles_10", "major_18_moon",
            question = "Is this relationship worth staying in?",
            tone = InterpretationTone.THERAPEUTIC,
        ),
        "celtic-cross" to Fixtures.reading(
            "celtic_cross",
            "major_13_death", "wands_05", "pentacles_08", "cups_06!", "major_19_sun",
            "swords_02", "major_08_strength!", "pentacles_03", "major_15_devil", "wands_01_ace",
            question = "Is it time to start my own business?",
        ),
    )

    @Test
    fun write() {
        val dir = System.getenv("ARCANA_SCRIPT_DIR")
        assumeTrue("ARCANA_SCRIPT_DIR is not set", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        samples.forEach { (name, request) ->
            val script = ReadingScript.of(request)
            File(out, "$name.json").writeText(json(name, script, restartAt = -1).toString())
        }
        // The same long reading, started over halfway, as happens when the context fills up.
        val long = ReadingScript.of(samples.getValue("celtic-cross"))
        File(out, "celtic-cross-restarted.json").writeText(json("celtic-cross-restarted", long, restartAt = 6).toString())
    }

    private fun json(name: String, script: ReadingScript, restartAt: Int) = JsonObject(
        mapOf(
            "name" to JsonPrimitive(name),
            "system" to JsonPrimitive(script.system),
            "turns" to JsonArray(
                script.turns.mapIndexed { i, turn ->
                    val fresh = i == 0 || i == restartAt
                    JsonObject(
                        mapOf(
                            "heading" to JsonPrimitive(turn.heading),
                            "prompt" to JsonPrimitive(if (fresh) script.promptFrom(i) else turn.prompt),
                            "grammar" to JsonPrimitive(turn.grammar),
                            "maxTokens" to JsonPrimitive(turn.maxTokens),
                            "restart" to JsonPrimitive(i == restartAt),
                        ),
                    )
                },
            ),
        ),
    )
}
