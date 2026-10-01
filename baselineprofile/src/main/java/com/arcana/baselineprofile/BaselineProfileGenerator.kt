package com.arcana.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through what a first session touches: a cold start onto the card grid, a scroll through
 * it (78 images, the heaviest list in the app), one card's page, the spread list, a reading from
 * shuffle to the built-in text, then the journal and Settings. Nothing here needs a network or a
 * downloaded model, so it runs the same on any emulator.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect("com.arcana.app") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        val w = device.displayWidth
        val h = device.displayHeight
        fun up() = device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 12).also { device.waitForIdle() }
        fun down() = device.swipe(w / 2, h / 3, w / 2, h * 4 / 5, 12).also { device.waitForIdle() }

        repeat(4) { up() }
        repeat(5) { down() }
        device.tap(By.desc("The Fool"))
        up()
        device.pressBack()
        device.waitForIdle()

        device.tap(By.text("Spreads"))
        up()
        down()
        device.tap(By.text("Daily Draw"))
        device.tap(By.textContains("Pull digitally"))
        device.tap(By.text("Shuffle the deck"))
        device.tap(By.text("Interpret"), timeout = 8_000)
        // A fresh install offers a model the first time. The built-in text is what is profiled.
        device.tap(By.text("Not now"))
        Thread.sleep(2_000)
        up()
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.tap(By.text("Journal"))
        device.tap(By.text("Settings"))
        repeat(3) { up() }
    }

    /** Taps the first thing matching [selector] if it shows up in time; a missing one is skipped. */
    private fun UiDevice.tap(selector: androidx.test.uiautomator.BySelector, timeout: Long = 4_000) {
        wait(Until.findObject(selector), timeout)?.click()
        waitForIdle()
    }
}
