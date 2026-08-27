package com.skysplatwoomy.petblud

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.color.MaterialColors
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
                assertEquals(View.GONE, hootsDashboard.findViewById<View>(R.id.collisionSwitch).visibility)
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
}
