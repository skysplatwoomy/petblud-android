package com.skysplatwoomy.petblud

import android.view.WindowManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun chatGptParserFiltersSanitizesDeduplicatesAndCapsText() {
        val ignored = payload(packageName = "com.example.other")
        assertNull(ChatGptNotificationParser.parse(ignored))

        val parsed = ChatGptNotificationParser.parse(
            payload(
                title = "  Codex\nRemote  ",
                bigText = "Working\u0000   on   tests",
                text = "unused fallback",
            ),
        ) ?: error("Expected ChatGPT payload to parse")
        assertEquals("Codex Remote", parsed.title)
        assertEquals("Working on tests", parsed.body)

        val duplicate = ChatGptNotificationParser.parse(
            payload(title = "Done", bigText = "Done"),
        ) ?: error("Expected duplicate payload to parse")
        assertEquals("", duplicate.body)

        val capped = ChatGptNotificationParser.parse(
            payload(title = "Codex", bigText = "x".repeat(500)),
        ) ?: error("Expected long payload to parse")
        assertTrue(capped.title.length + capped.body.length <= ChatGptNotificationParser.MAX_TOTAL_CHARACTERS)
    }

    @Test
    fun activeChatGptNotificationsTracksNewestUpdatesRemovalAndDisconnect() {
        val tracker = ActiveChatGptNotifications()
        tracker.setEnabled(true)
        tracker.reconcile(
            listOf(
                payload(key = "old", postTime = 10L, title = "Old"),
                payload(key = "new", postTime = 20L, title = "New"),
            ),
        )
        assertEquals("new", tracker.current.value?.key)

        tracker.posted(payload(key = "old", postTime = 30L, title = "Updated"))
        assertEquals("Updated", tracker.current.value?.title)
        tracker.removed("old")
        assertEquals("new", tracker.current.value?.key)
        tracker.disconnected()
        assertNull(tracker.current.value)

        tracker.setEnabled(false)
        tracker.posted(payload(key = "ignored", postTime = 40L, title = "Ignored"))
        assertNull(tracker.current.value)
    }

    @Test
    fun hootsBubblePrefersAboveFlipsBelowAndClampsToUsableBounds() {
        val bounds = PixelBounds(left = 10, top = 20, right = 1090, bottom = 1000)
        val centered = HootsBubblePlacement.place(
            pet = PixelRect(500, 700, 620, 830),
            bubbleWidth = 280,
            bubbleHeight = 140,
            bounds = bounds,
            gap = 5,
            tailInset = 24,
        )
        assertFalse(centered.tailAtTop)
        assertTrue(centered.y + 140 <= 700)

        val topEdge = HootsBubblePlacement.place(
            pet = PixelRect(0, 22, 120, 152),
            bubbleWidth = 280,
            bubbleHeight = 140,
            bounds = bounds,
            gap = 5,
            tailInset = 24,
        )
        assertTrue(topEdge.tailAtTop)
        assertTrue(topEdge.x >= bounds.left)
        assertTrue(topEdge.y >= bounds.top)
        assertTrue(topEdge.x + 280 <= bounds.right)
        assertTrue(topEdge.y + 140 <= bounds.bottom)
    }

    @Test
    fun bubbleAppearanceNormalizesOpacityColorsAndHootsIdleScale() {
        val normalized = PetSettings(
            bubbleBorderColor = 0x00112233,
            bubbleFillColor = 0x00445566,
            bubbleBorderOpacityPercent = -50.0,
            bubbleFillOpacityPercent = 150.0,
            bubbleTextOpacityPercent = 45.0,
        ).normalized()

        assertEquals(0xFF112233.toInt(), normalized.bubbleBorderColor)
        assertEquals(0xFF445566.toInt(), normalized.bubbleFillColor)
        assertEquals(
            PetSettings.MIN_BUBBLE_OPACITY_PERCENT,
            normalized.bubbleBorderOpacityPercent,
            0.0,
        )
        assertEquals(
            PetSettings.MAX_BUBBLE_OPACITY_PERCENT,
            normalized.bubbleFillOpacityPercent,
            0.0,
        )
        assertEquals(45.0, normalized.bubbleTextOpacityPercent, 0.0)
        assertEquals(
            SpriteVisualScale.HOOTS_IDLE_SCALE,
            SpriteVisualScale.forAnimation(PetSpecies.HOOTS, PetAnimation.IDLE),
            0f,
        )
        assertEquals(1f, SpriteVisualScale.forAnimation(PetSpecies.HOOTS, PetAnimation.CODE), 0f)
        assertEquals(1f, SpriteVisualScale.forAnimation(PetSpecies.SEEDY, PetAnimation.IDLE), 0f)

        val passiveFlags = HootsBubbleInteraction.windowFlags(interactive = false)
        val interactiveFlags = HootsBubbleInteraction.windowFlags(interactive = true)
        assertTrue(passiveFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertEquals(0, interactiveFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        assertTrue(interactiveFlags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }

    @Test
    fun typedNumericEntryAcceptsDecimalCommaRejectsInvalidAndClampsRange() {
        assertEquals(2.5, NumericEntry.parseAndClamp("2,5", 0.1, 5.0) ?: 0.0, 0.0)
        assertEquals(5.0, NumericEntry.parseAndClamp("99", 0.1, 5.0) ?: 0.0, 0.0)
        assertEquals(10.0, NumericEntry.parseAndClamp("-4", 10.0, 500.0) ?: 0.0, 0.0)
        assertNull(NumericEntry.parseAndClamp("hello", 0.0, 100.0))
        assertNull(NumericEntry.parseAndClamp("NaN", 0.0, 100.0))
        assertEquals(1.3, NumericEntry.snap(1.26, 0.1, 5.0, 0.1), 0.0001)
        assertEquals("1.3", NumericEntry.format(1.25, 1))
    }

    private fun payload(
        packageName: String = ChatGptNotificationParser.CHATGPT_PACKAGE,
        key: String = "key",
        postTime: Long = 1L,
        title: CharSequence? = "Title",
        bigText: CharSequence? = null,
        text: CharSequence? = null,
        subText: CharSequence? = null,
    ) = NotificationPayload(
        packageName = packageName,
        key = key,
        postTime = postTime,
        title = title,
        bigText = bigText,
        text = text,
        subText = subText,
    )
}
