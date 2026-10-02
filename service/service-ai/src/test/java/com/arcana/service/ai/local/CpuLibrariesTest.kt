package com.arcana.service.ai.local

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuLibrariesTest {
    // Feature lists as /proc/cpuinfo prints them. The second is a Pixel 4a 5G's, the same on all
    // eight of its cores.
    private val armv80 = "fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid"
    private val armv82 = "fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp"
    private val armv9 = "$armv82 sha3 sm3 sm4 sha512 sve asimdfhm dit uscat ilrcpc flagm ssbs sb paca pacg dcpodp sve2 sveaes svepmull svebitperm svesha3 svesm4 flagm2 frint svei8mm svebf16 i8mm bf16 dgh bti mte"

    private fun cpuinfo(vararg cores: String): String = cores.mapIndexed { i, features ->
        "processor\t: $i\nBogoMIPS\t: 38.40\nFeatures\t: $features\nCPU implementer\t: 0x41\nCPU part\t: 0xd05\n"
    }.joinToString("\n") + "\nHardware\t: Some Phone\n"

    private fun eight(features: String) = cpuinfo(*Array(8) { features })

    @Test
    fun `a 2020 phone gets the dot product libraries, then the plain one`() {
        assertEquals(
            listOf("android_armv8.2_2", "android_armv8.2_1", "android_armv8.0_1"),
            CpuLibraries.pick(eight(armv82), "bramble bramble"),
        )
    }

    @Test
    fun `an old phone gets only the library every chip can run`() {
        assertEquals(listOf("android_armv8.0_1"), CpuLibraries.pick(eight(armv80), "hi6250 hi6250"))
    }

    @Test
    fun `a recent phone is offered everything up to what it has`() {
        assertEquals(
            listOf("android_armv9.0_1", "android_armv8.6_1", "android_armv8.2_2", "android_armv8.2_1", "android_armv8.0_1"),
            CpuLibraries.pick(eight(armv9), "tokay tokay"),
        )
        assertEquals("android_armv9.2_2", CpuLibraries.pick(eight("$armv9 sme"), "x x").first())
    }

    @Test
    fun `cores that differ only get what all of them have`() {
        val mixed = cpuinfo(armv82, armv82, armv82, armv82, armv80, armv80, armv80, armv80)
        assertEquals(listOf("android_armv8.0_1"), CpuLibraries.pick(mixed, "x x"))
        val oneOdd = cpuinfo(armv9, armv9, armv9, armv82)
        assertEquals("android_armv8.2_2", CpuLibraries.pick(oneOdd, "x x").first())
    }

    @Test
    fun `a chip known to misreport is given the plain library whatever it claims`() {
        assertEquals(listOf("android_armv8.0_1"), CpuLibraries.pick(eight(armv82), "samsungexynos9810 universal9810"))
        assertEquals(listOf("android_armv8.0_1"), CpuLibraries.pick(eight(armv82), "x EXYNOS9810"))
    }

    @Test
    fun `with nothing to go on the plain library is the only one offered`() {
        assertEquals(listOf("android_armv8.0_1"), CpuLibraries.pick("", "x x"))
        assertEquals(listOf("android_armv8.0_1"), CpuLibraries.pick("processor : 0\nmodel name : something\n", "x x"))
    }
}
