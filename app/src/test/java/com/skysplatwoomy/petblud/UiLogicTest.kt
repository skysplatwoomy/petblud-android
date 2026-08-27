package com.skysplatwoomy.petblud

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
    @Test
    fun dashboardSelectsPermissionStoppedAndRunningStates() {
        assertEquals(
            DashboardAction.GRANT_PERMISSION,
            dashboardContent(overlayAllowed = false, serviceRunning = false, petCount = 0).action,
        )
        assertEquals(
            DashboardAction.START,
            dashboardContent(overlayAllowed = true, serviceRunning = false, petCount = 0).action,
        )
        val running = dashboardContent(overlayAllowed = true, serviceRunning = true, petCount = 3)
        assertEquals(DashboardAction.STOP, running.action)
        assertEquals("3 Seedys are roaming", running.title)
    }

    @Test
    fun nestedMotionRowsOnlyAppearWithTiltGravity() {
        assertTrue(visibleMotionRows(false).isEmpty())
        assertEquals(MotionRow.entries.toSet(), visibleMotionRows(true))
    }

    @Test
    fun dashboardContentUsesSpeciesSpecificCopy() {
        val hoots = dashboardContent(
            overlayAllowed = true,
            serviceRunning = true,
            petCount = 1,
            species = PetSpecies.HOOTS,
        )
        assertEquals("Hoots is roaming", hoots.title)
        assertEquals("Send Hoots home", hoots.actionLabel)
    }

    @Test
    fun motionSensorsRouteIndependentlyAcrossSpecies() {
        val settings = AppSettings(
            seedy = PetSettings(tiltGravity = true, shakeDetonation = false),
            hoots = PetSettings(tiltGravity = true, shakeDetonation = true),
        )
        assertEquals(setOf(PetSpecies.SEEDY, PetSpecies.HOOTS), MotionFeatureRouting.tiltSpecies(settings))
        assertEquals(setOf(PetSpecies.HOOTS), MotionFeatureRouting.shakeSpecies(settings))

        val disabledTilt = settings.copy(
            hoots = settings.hoots.copy(tiltGravity = false),
        )
        assertTrue(MotionFeatureRouting.shakeSpecies(disabledTilt).isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun liveSaveDebouncerKeepsOnlyLatestSliderValue() = runTest {
        val saved = mutableListOf<Int>()
        val debouncer = LiveSaveDebouncer<Int>(this, delayMillis = 200L) { saved += it }

        debouncer.submit(10)
        advanceTimeBy(100)
        debouncer.submit(20)
        advanceTimeBy(199)
        runCurrent()
        assertTrue(saved.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(20), saved)
    }

    @Test
    fun popoverPlacementClampsAtEveryScreenEdge() {
        val bounds = PixelBounds(left = 10, top = 20, right = 1090, bottom = 1900)
        val anchors = listOf(
            10 to 20,
            1090 to 20,
            10 to 1900,
            1090 to 1900,
            550 to 960,
        )

        anchors.forEach { (x, y) ->
            val point = PopoverPlacement.place(x, y, 320, 700, bounds, gap = 10)
            assertTrue(point.x >= bounds.left)
            assertTrue(point.y >= bounds.top)
            assertTrue(point.x + 320 <= bounds.right)
            assertTrue(point.y + 700 <= bounds.bottom)
        }
    }

    @Test
    fun removeAllRequiresTwoTapsWithinTimeout() {
        val confirmation = DangerConfirmation(timeoutMillis = 3_000)
        assertFalse(confirmation.tap(1_000))
        assertTrue(confirmation.tap(3_500))
        assertFalse(confirmation.tap(10_000))
        assertFalse(confirmation.tap(14_000))
    }
}
