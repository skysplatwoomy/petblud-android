package com.skysplatwoomy.petblud

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity(), PetDashboardFragment.Host {
    private data class PendingStart(val species: PetSpecies, val count: Int)

    private lateinit var topBar: View
    private lateinit var saveStatus: TextView
    private lateinit var tabs: TabLayout
    private lateinit var pager: ViewPager2
    private var pendingStart: PendingStart? = null
    private var notificationRequestActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        topBar = findViewById(R.id.topBar)
        saveStatus = findViewById(R.id.saveStatus)
        tabs = findViewById(R.id.petTabs)
        pager = findViewById(R.id.petPager)
        applySystemInsets()
        pager.adapter = PetPagerAdapter(this)
        pager.offscreenPageLimit = PetSpecies.entries.size
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = PetSpecies.entries[position].displayName
            tab.contentDescription = getString(
                if (position == PetSpecies.SEEDY.ordinal) R.string.open_seedy_page else R.string.open_hoots_page,
            )
        }.attach()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabs.setSelectedTabIndicatorColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (position == PetSpecies.HOOTS.ordinal) R.color.hoots_orange else R.color.pet_lime,
                    ),
                )
            }
        })
        selectRequestedSpecies(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectRequestedSpecies(intent)
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart != null && Settings.canDrawOverlays(this)) continuePendingStart()
    }

    @Deprecated("Deprecated in the Android framework, retained for API 26 compatibility")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            notificationRequestActive = false
            dispatchPendingStart()
        }
    }

    override fun requestPetStart(species: PetSpecies, count: Int) {
        pendingStart = PendingStart(species, count)
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
            return
        }
        continuePendingStart()
    }

    override fun openOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri(),
            ),
        )
    }

    override fun openNotificationListenerAccess() {
        val preferred = NotificationListenerAccess.settingsIntent(this)
        runCatching { startActivity(preferred) }
            .recoverCatching {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
    }

    override fun showSaving() {
        saveStatus.setText(R.string.saving)
        saveStatus.setTextColor(ContextCompat.getColor(this, R.color.pet_warning))
    }

    override fun showSaved() {
        saveStatus.setText(R.string.saved)
        saveStatus.setTextColor(ContextCompat.getColor(this, R.color.pet_success))
    }

    private fun continuePendingStart() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!notificationRequestActive) {
                notificationRequestActive = true
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
            return
        }
        dispatchPendingStart()
    }

    private fun dispatchPendingStart() {
        val request = pendingStart ?: return
        pendingStart = null
        if (request.count == 0) {
            PetCommands.start(this, request.species)
        } else {
            PetCommands.spawn(this, request.count, request.species)
        }
    }

    private fun applySystemInsets() {
        val originalTopPadding = topBar.paddingTop
        val originalPagerBottomPadding = pager.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { _, insets ->
            val topInset = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            topBar.updatePadding(top = originalTopPadding + topInset)
            pager.updatePadding(bottom = originalPagerBottomPadding + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.mainRoot))
    }

    private fun selectRequestedSpecies(intent: Intent?) {
        val species = PetSpecies.fromWireName(intent?.getStringExtra(EXTRA_OPEN_SPECIES))
        pager.setCurrentItem(species.ordinal, false)
    }

    private class PetPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = PetSpecies.entries.size

        override fun createFragment(position: Int): Fragment =
            PetDashboardFragment.newInstance(PetSpecies.entries[position])
    }

    companion object {
        const val EXTRA_OPEN_SPECIES = "open_species"
        private const val REQUEST_NOTIFICATIONS = 7102
    }
}
