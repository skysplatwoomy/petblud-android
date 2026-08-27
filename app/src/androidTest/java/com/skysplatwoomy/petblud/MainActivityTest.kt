package com.skysplatwoomy.petblud

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun dashboardHasAccessibleActionsAndConditionalMotionRows() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val dashboard = activity.supportFragmentManager.findFragmentByTag("f0")
                    ?.requireView() ?: error("Seedy dashboard was not created")
                val spawn = dashboard.findViewById<View>(R.id.spawnButton)
                val detonate = dashboard.findViewById<View>(R.id.detonateButton)
                val permissions = dashboard.findViewById<View>(R.id.permissionsButton)
                assertNotNull(spawn.contentDescription)
                assertNotNull(detonate.contentDescription)
                assertNotNull(permissions.contentDescription)
                assertEquals(View.GONE, dashboard.findViewById<View>(R.id.motionChildren).visibility)
                assertEquals(
                    View.GONE,
                    dashboard.findViewById<View>(R.id.disablePhysicsInteractionsSwitch).visibility,
                )
                assertEquals(
                    View.GONE,
                    dashboard.findViewById<View>(R.id.chatGptNotificationsCard).visibility,
                )
                assertTrue(dashboard.findViewById<View>(R.id.physicsValue).isClickable)
                assertTrue(dashboard.findViewById<View>(R.id.sizeValue).isClickable)
                assertTrue(dashboard.findViewById<View>(R.id.heroAction).minimumHeight >= 0)
                assertSame(
                    activity.findViewById<View>(R.id.topBar).parent,
                    activity.findViewById<View>(R.id.petPager).parent,
                )
            }
        }
    }

    @Test
    fun pagerShowsSeedyThenOrangeHootsDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.petTabs)
                val pager = activity.findViewById<ViewPager2>(R.id.petPager)
                assertEquals(2, tabs.tabCount)
                assertEquals("Seedy", tabs.getTabAt(0)?.text)
                assertEquals("Hoots", tabs.getTabAt(1)?.text)
                assertEquals(PetSpecies.SEEDY.ordinal, pager.currentItem)

                pager.setCurrentItem(PetSpecies.HOOTS.ordinal, false)
                activity.supportFragmentManager.executePendingTransactions()
                val hootsDashboard = activity.supportFragmentManager.findFragmentByTag("f1")
                    ?.requireView() ?: error("Hoots dashboard was not created")
                assertEquals(PetSpecies.HOOTS.ordinal, pager.currentItem)
                assertEquals(View.VISIBLE, hootsDashboard.findViewById<View>(R.id.crossCollisionSwitch).visibility)
                assertEquals(
                    View.VISIBLE,
                    hootsDashboard.findViewById<View>(R.id.disablePhysicsInteractionsSwitch).visibility,
                )
                assertEquals(View.GONE, hootsDashboard.findViewById<View>(R.id.collisionSwitch).visibility)
                assertEquals(
                    View.VISIBLE,
                    hootsDashboard.findViewById<View>(R.id.chatGptNotificationsCard).visibility,
                )
                val notificationSwitch =
                    hootsDashboard.findViewById<MaterialSwitch>(R.id.chatGptNotificationsSwitch)
                assertNotNull(notificationSwitch.contentDescription)
                assertEquals(
                    if (notificationSwitch.isChecked) View.VISIBLE else View.GONE,
                    hootsDashboard.findViewById<View>(R.id.bubbleAppearanceControls).visibility,
                )
                assertTrue(hootsDashboard.findViewById<View>(R.id.bubbleTextOpacityValue).isClickable)
                assertEquals("Hoots", hootsDashboard.findViewById<View>(R.id.heroPet).contentDescription)
                assertEquals(
                    activity.getColor(R.color.hoots_orange),
                    MaterialColors.getColor(hootsDashboard, androidx.appcompat.R.attr.colorPrimary),
                )
            }
        }
    }

    @Test
    fun appLabelMatchesPetBlud() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("PetBlud", context.getString(R.string.app_name))
    }

    @Test
    fun notificationListenerIsProtectedAndAccessIntentMatchesApiLevel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, ChatGptNotificationListenerService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertEquals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, serviceInfo.permission)
        val action = NotificationListenerAccess.settingsIntent(context).action
        assertEquals(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
            } else {
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            },
            action,
        )
    }

    @Test
    fun hootsHoldMenuShowsChatGptBubbleToggle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var changed: Boolean? = null
                val menu = PetOverlayMenuView(
                    activity,
                    petId = 1L,
                    snapshot = PetSnapshot(
                        id = 1L,
                        species = PetSpecies.HOOTS,
                        gravityEnabled = true,
                        physicsMultiplier = 1.0,
                        sizePercent = 100.0,
                        showChatGptNotifications = true,
                    ),
                    listener = object : PetOverlayMenuView.Listener {
                        override fun onDismiss() = Unit
                        override fun onSpawn() = Unit
                        override fun onExplode() = Unit
                        override fun onDetonate() = Unit
                        override fun onGravityChanged(enabled: Boolean) = Unit
                        override fun onPhysicsChanged(multiplier: Double) = Unit
                        override fun onChatGptBubbleChanged(enabled: Boolean) {
                            changed = enabled
                        }
                        override fun onOpenSettings() = Unit
                        override fun onRemoveThis() = Unit
                        override fun onRemoveOthers() = Unit
                        override fun onRemoveAll() = Unit
                    },
                )
                val toggle = menu.findViewById<MaterialSwitch>(R.id.menuChatGptBubble)
                assertEquals(View.VISIBLE, toggle.visibility)
                assertTrue(toggle.isChecked)
                assertTrue(menu.findViewById<View>(R.id.menuPhysicsValue).isClickable)
                toggle.isChecked = false
                assertEquals(false, changed)
            }
        }
    }

    @Test
    fun hootsBorderColourPickerOpensWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<ViewPager2>(R.id.petPager)
                    .setCurrentItem(PetSpecies.HOOTS.ordinal, false)
                activity.supportFragmentManager.executePendingTransactions()
                val dashboard = activity.supportFragmentManager.findFragmentByTag("f1")
                    ?.requireView() ?: error("Hoots dashboard was not created")
                dashboard.findViewById<View>(R.id.bubbleBorderColorButton).performClick()
            }
            onView(withText(R.string.choose_bubble_border)).check(matches(isDisplayed()))
            pressBack()
        }
    }
}
