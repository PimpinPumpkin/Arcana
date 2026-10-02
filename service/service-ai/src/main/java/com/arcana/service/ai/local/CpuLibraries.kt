package com.arcana.service.ai.local

import android.os.Build
import java.io.File

/**
 * Which of llama.cpp's processor-specific math libraries this phone may load, best first.
 *
 * llama.cpp ships one library per generation of ARM processor and checks each against what the
 * kernel says the processor can do. That answer is given for the phone as a whole, and it has
 * been wrong: the Exynos Galaxy S9 has four older cores and four newer ones, and on its first
 * software said yes for all eight. A program that believed it crashed the moment it ran on an
 * older core. So the list of every core is read here too, and a library is only offered when all
 * of them can run it. llama.cpp still has the last word on each one offered.
 */
internal object CpuLibraries {
    private const val BASELINE = "android_armv8.0_1"

    // The names llama.cpp gives its Android builds, with what each needs, as /proc/cpuinfo spells it.
    private val tiers: List<Pair<String, Set<String>>> = listOf(
        "android_armv9.2_2" to setOf("asimddp", "fphp", "asimdhp", "i8mm", "sve", "sve2", "sme"),
        "android_armv9.2_1" to setOf("asimddp", "fphp", "asimdhp", "i8mm", "sve", "sme"),
        "android_armv9.0_1" to setOf("asimddp", "fphp", "asimdhp", "i8mm", "sve2"),
        "android_armv8.6_1" to setOf("asimddp", "fphp", "asimdhp", "i8mm"),
        "android_armv8.2_2" to setOf("asimddp", "fphp", "asimdhp"),
        "android_armv8.2_1" to setOf("asimddp"),
        BASELINE to emptySet(),
    )

    // Chips whose cores differ and whose kernels have claimed otherwise.
    private val mismatched = listOf("exynos9810")

    fun pick(): List<String> = pick(
        cpuinfo = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault(""),
        hardware = "${Build.HARDWARE} ${Build.BOARD}",
    )

    /**
     * @param cpuinfo the text of /proc/cpuinfo, which has a "Features" line for each core
     * @param hardware anything that names the chip, such as Build.HARDWARE
     */
    fun pick(cpuinfo: String, hardware: String): List<String> {
        if (mismatched.any { hardware.contains(it, ignoreCase = true) }) return listOf(BASELINE)
        val cores = cpuinfo.lineSequence()
            .filter { it.startsWith("Features") }
            .map { it.substringAfter(':').trim().split(Regex("\\s+")).toSet() }
            .toList()
        // Nothing to go on: the library every 64-bit ARM chip can run.
        if (cores.isEmpty()) return listOf(BASELINE)
        val shared = cores.reduce { all, core -> all intersect core }
        return tiers.filter { shared.containsAll(it.second) }.map { it.first }
    }
}
