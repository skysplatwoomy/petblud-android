package com.skysplatwoomy.petblud

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlinx.coroutines.launch

class PetDashboardFragment : Fragment() {
    interface Host {
        fun requestPetStart(species: PetSpecies, count: Int)
        fun openOverlayPermission()
        fun openNotificationListenerAccess()
        fun showSaving()
        fun showSaved()
    }

    private data class SaveRequest(
        val revision: Long,
        val settings: PetSettings,
        val crossCollision: Boolean?,
    )

    private val species: PetSpecies
        get() = PetSpecies.fromWireName(requireArguments().getString(ARG_SPECIES))
    private val host: Host
        get() = requireActivity() as Host

    private lateinit var repository: SettingsRepository
    private lateinit var saveDebouncer: LiveSaveDebouncer<SaveRequest>
    private lateinit var root: View
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var heroAction: MaterialButton
    private lateinit var spawnButton: MaterialButton
    private lateinit var detonateButton: MaterialButton
    private lateinit var permissionsButton: MaterialButton
    private lateinit var gravitySwitch: MaterialSwitch
    private lateinit var tiltGravitySwitch: MaterialSwitch
    private lateinit var shakeSwitch: MaterialSwitch
    private lateinit var uprightSwitch: MaterialSwitch
    private lateinit var keyboardSwitch: MaterialSwitch
    private lateinit var collisionSwitch: MaterialSwitch
    private lateinit var crossCollisionSwitch: MaterialSwitch
    private lateinit var disablePhysicsInteractionsSwitch: MaterialSwitch
    private lateinit var chatGptNotificationsCard: View
    private lateinit var chatGptNotificationsSwitch: MaterialSwitch
    private lateinit var chatGptNotificationsSupport: TextView
    private lateinit var grantNotificationAccessButton: MaterialButton
    private lateinit var bubbleAppearanceControls: View
    private lateinit var bubbleBorderColorButton: MaterialButton
    private lateinit var bubbleFillColorButton: MaterialButton
    private lateinit var bubbleOpacitySlider: Slider
    private lateinit var bubbleOpacityValue: TextView
    private lateinit var resetBubbleAppearanceButton: MaterialButton
    private lateinit var bubbleTapOpensChatGptSwitch: MaterialSwitch
    private lateinit var motionChildren: LinearLayout
    private lateinit var physicsSlider: Slider
    private lateinit var sizeSlider: Slider
    private lateinit var physicsValue: TextView
    private lateinit var sizeValue: TextView
    private var hydrating = true
    private var saveRevision = 0L
    private var bubbleBorderColor = PetSettings.DEFAULT_BUBBLE_BORDER_COLOR
    private var bubbleFillColor = PetSettings.DEFAULT_BUBBLE_FILL_COLOR

    private val statusRefresh = object : Runnable {
        override fun run() {
            if (!isAdded || !::heroAction.isInitialized) return
            refreshDashboard()
            heroAction.postDelayed(this, STATUS_POLL_MILLIS)
        }
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val theme = if (species == PetSpecies.HOOTS) {
            R.style.Theme_PetBlud_Hoots
        } else {
            R.style.Theme_PetBlud
        }
        return inflater.cloneInContext(ContextThemeWrapper(requireContext(), theme))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_pet_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view
        repository = SettingsRepository(requireContext())
        bindViews()
        configureSpeciesUi()
        bindListeners()
        saveDebouncer = LiveSaveDebouncer(
            viewLifecycleOwner.lifecycleScope,
            SAVE_DEBOUNCE_MILLIS,
            ::persistSettings,
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = repository.read()
            applySettingsToControls(settings)
            hydrating = false
            host.showSaved()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::heroAction.isInitialized) {
            heroAction.removeCallbacks(statusRefresh)
            statusRefresh.run()
            refreshNotificationControls()
        }
    }

    override fun onPause() {
        if (::heroAction.isInitialized) heroAction.removeCallbacks(statusRefresh)
        super.onPause()
    }

    override fun onDestroyView() {
        if (::saveDebouncer.isInitialized) saveDebouncer.cancel()
        super.onDestroyView()
    }

    private fun bindViews() {
        heroTitle = root.findViewById(R.id.heroTitle)
        heroSubtitle = root.findViewById(R.id.heroSubtitle)
        heroAction = root.findViewById(R.id.heroAction)
        spawnButton = root.findViewById(R.id.spawnButton)
        detonateButton = root.findViewById(R.id.detonateButton)
        permissionsButton = root.findViewById(R.id.permissionsButton)
        gravitySwitch = root.findViewById(R.id.gravitySwitch)
        tiltGravitySwitch = root.findViewById(R.id.tiltGravitySwitch)
        shakeSwitch = root.findViewById(R.id.shakeSwitch)
        uprightSwitch = root.findViewById(R.id.uprightSwitch)
        keyboardSwitch = root.findViewById(R.id.keyboardSwitch)
        collisionSwitch = root.findViewById(R.id.collisionSwitch)
        crossCollisionSwitch = root.findViewById(R.id.crossCollisionSwitch)
        disablePhysicsInteractionsSwitch = root.findViewById(R.id.disablePhysicsInteractionsSwitch)
        chatGptNotificationsCard = root.findViewById(R.id.chatGptNotificationsCard)
        chatGptNotificationsSwitch = root.findViewById(R.id.chatGptNotificationsSwitch)
        chatGptNotificationsSupport = root.findViewById(R.id.chatGptNotificationsSupport)
        grantNotificationAccessButton = root.findViewById(R.id.grantNotificationAccessButton)
        bubbleAppearanceControls = root.findViewById(R.id.bubbleAppearanceControls)
        bubbleBorderColorButton = root.findViewById(R.id.bubbleBorderColorButton)
        bubbleFillColorButton = root.findViewById(R.id.bubbleFillColorButton)
        bubbleOpacitySlider = root.findViewById(R.id.bubbleOpacitySlider)
        bubbleOpacityValue = root.findViewById(R.id.bubbleOpacityValue)
        resetBubbleAppearanceButton = root.findViewById(R.id.resetBubbleAppearanceButton)
        bubbleTapOpensChatGptSwitch = root.findViewById(R.id.bubbleTapOpensChatGptSwitch)
        motionChildren = root.findViewById(R.id.motionChildren)
        physicsSlider = root.findViewById(R.id.physicsSlider)
        sizeSlider = root.findViewById(R.id.sizeSlider)
        physicsValue = root.findViewById(R.id.physicsValue)
        sizeValue = root.findViewById(R.id.sizeValue)
    }

    private fun configureSpeciesUi() {
        val hoots = species == PetSpecies.HOOTS
        root.findViewById<ImageView>(R.id.heroPet).apply {
            setImageResource(if (hoots) R.drawable.hoots_idle else R.drawable.seedy_idle)
            contentDescription = species.displayName
        }
        spawnButton.apply {
            text = getString(if (hoots) R.string.wake_hoots else R.string.add_seedy)
            contentDescription = getString(if (hoots) R.string.spawn_hoots else R.string.spawn_one_seedy)
        }
        detonateButton.contentDescription = getString(
            if (hoots) R.string.detonate_hoots else R.string.detonate_all_seedys,
        )
        uprightSwitch.text = getString(
            if (hoots) R.string.keep_hoots_upright else R.string.keep_seedy_upright,
        )
        root.findViewById<TextView>(R.id.motionSupport).text = getString(
            if (hoots) R.string.motion_support_hoots else R.string.motion_support,
        )
        root.findViewById<TextView>(R.id.sizeLabel).text = getString(
            if (hoots) R.string.hoots_size else R.string.seedy_size,
        )
        sizeSlider.contentDescription = getString(
            if (hoots) R.string.hoots_size_percentage else R.string.seedy_size_percentage,
        )
        root.findViewById<TextView>(R.id.autoSaveSupport).text = getString(
            if (hoots) R.string.auto_save_support_hoots else R.string.auto_save_support,
        )
        collisionSwitch.visibility = if (hoots) View.GONE else View.VISIBLE
        crossCollisionSwitch.visibility = if (hoots) View.VISIBLE else View.GONE
        disablePhysicsInteractionsSwitch.visibility = if (hoots) View.VISIBLE else View.GONE
        chatGptNotificationsCard.visibility = if (hoots) View.VISIBLE else View.GONE
    }

    private fun bindListeners() {
        listOf(
            gravitySwitch,
            shakeSwitch,
            uprightSwitch,
            keyboardSwitch,
            collisionSwitch,
            crossCollisionSwitch,
            disablePhysicsInteractionsSwitch,
            bubbleTapOpensChatGptSwitch,
        )
            .forEach { control ->
                control.setOnCheckedChangeListener { _, _ -> saveImmediatelyIfReady() }
            }
        tiltGravitySwitch.setOnCheckedChangeListener { _, enabled ->
            showMotionChildren(enabled)
            saveImmediatelyIfReady()
        }
        chatGptNotificationsSwitch.setOnCheckedChangeListener { _, enabled ->
            refreshNotificationControls()
            if (!hydrating) {
                saveImmediatelyIfReady()
                if (
                    enabled && NotificationListenerAccess.isSupported(requireContext()) &&
                    !NotificationListenerAccess.isGranted(requireContext())
                ) {
                    host.openNotificationListenerAccess()
                }
            }
        }
        grantNotificationAccessButton.setOnClickListener {
            host.openNotificationListenerAccess()
        }
        bubbleBorderColorButton.setOnClickListener {
            BubbleColorPickerDialog.show(
                requireContext(),
                getString(R.string.choose_bubble_border),
                bubbleBorderColor,
            ) { color ->
                bubbleBorderColor = color
                refreshBubbleAppearanceControls()
                saveImmediatelyIfReady()
            }
        }
        bubbleFillColorButton.setOnClickListener {
            BubbleColorPickerDialog.show(
                requireContext(),
                getString(R.string.choose_bubble_fill),
                bubbleFillColor,
            ) { color ->
                bubbleFillColor = color
                refreshBubbleAppearanceControls()
                saveImmediatelyIfReady()
            }
        }
        bubbleOpacitySlider.addOnChangeListener { _, value, fromUser ->
            bubbleOpacityValue.text = String.format(Locale.getDefault(), "%.0f%%", value)
            if (fromUser && !hydrating) queueSliderSave()
        }
        resetBubbleAppearanceButton.setOnClickListener {
            bubbleBorderColor = PetSettings.DEFAULT_BUBBLE_BORDER_COLOR
            bubbleFillColor = PetSettings.DEFAULT_BUBBLE_FILL_COLOR
            bubbleOpacitySlider.value = PetSettings.DEFAULT_BUBBLE_OPACITY_PERCENT.toFloat()
            refreshBubbleAppearanceControls()
            saveImmediatelyIfReady()
        }
        physicsSlider.addOnChangeListener { _, value, fromUser ->
            physicsValue.text = String.format(Locale.getDefault(), "%.1fx", value)
            if (fromUser && !hydrating) queueSliderSave()
        }
        sizeSlider.addOnChangeListener { _, value, fromUser ->
            sizeValue.text = String.format(Locale.getDefault(), "%.0f%%", value)
            if (fromUser && !hydrating) queueSliderSave()
        }
        spawnButton.setOnClickListener { host.requestPetStart(species, 1) }
        detonateButton.setOnClickListener {
            if (PetOverlayService.statusSnapshot().count(species) > 0) {
                PetCommands.send(requireContext(), PetCommands.ACTION_DETONATE) {
                    putExtra(PetCommands.EXTRA_SPECIES, species.wireName)
                }
            }
        }
        permissionsButton.setOnClickListener { host.openOverlayPermission() }
    }

    private fun applySettingsToControls(appSettings: AppSettings) {
        val settings = appSettings.forSpecies(species)
        gravitySwitch.isChecked = settings.gravity
        tiltGravitySwitch.isChecked = settings.tiltGravity
        shakeSwitch.isChecked = settings.shakeDetonation
        uprightSwitch.isChecked = settings.keepUpright
        keyboardSwitch.isChecked = settings.avoidKeyboard
        collisionSwitch.isChecked = settings.collision
        crossCollisionSwitch.isChecked = appSettings.seedyHootsCollision
        disablePhysicsInteractionsSwitch.isChecked = settings.disablePhysicsInteractions
        chatGptNotificationsSwitch.isChecked = settings.showChatGptNotifications
        bubbleBorderColor = settings.bubbleBorderColor
        bubbleFillColor = settings.bubbleFillColor
        bubbleOpacitySlider.value = settings.bubbleOpacityPercent.toFloat()
        bubbleTapOpensChatGptSwitch.isChecked = settings.bubbleTapOpensChatGpt
        refreshBubbleAppearanceControls()
        refreshNotificationControls()
        showMotionChildren(settings.tiltGravity)
        physicsSlider.value = settings.physicsMultiplier.toFloat()
        sizeSlider.value = settings.sizePercent.toFloat()
        physicsValue.text = String.format(Locale.getDefault(), "%.1fx", settings.physicsMultiplier)
        sizeValue.text = String.format(Locale.getDefault(), "%.0f%%", settings.sizePercent)
    }

    private fun settingsFromControls() = PetSettings(
        gravity = gravitySwitch.isChecked,
        tiltGravity = tiltGravitySwitch.isChecked,
        shakeDetonation = shakeSwitch.isChecked,
        keepUpright = uprightSwitch.isChecked,
        avoidKeyboard = keyboardSwitch.isChecked,
        physicsMultiplier = physicsSlider.value.toDouble(),
        sizePercent = sizeSlider.value.toDouble(),
        collision = species == PetSpecies.SEEDY && collisionSwitch.isChecked,
        disablePhysicsInteractions = species == PetSpecies.HOOTS &&
            disablePhysicsInteractionsSwitch.isChecked,
        showChatGptNotifications = species == PetSpecies.HOOTS &&
            chatGptNotificationsSwitch.isChecked,
        bubbleBorderColor = if (species == PetSpecies.HOOTS) {
            bubbleBorderColor
        } else {
            PetSettings.DEFAULT_BUBBLE_BORDER_COLOR
        },
        bubbleFillColor = if (species == PetSpecies.HOOTS) {
            bubbleFillColor
        } else {
            PetSettings.DEFAULT_BUBBLE_FILL_COLOR
        },
        bubbleOpacityPercent = if (species == PetSpecies.HOOTS) {
            bubbleOpacitySlider.value.toDouble()
        } else {
            PetSettings.DEFAULT_BUBBLE_OPACITY_PERCENT
        },
        bubbleTapOpensChatGpt = species == PetSpecies.HOOTS &&
            bubbleTapOpensChatGptSwitch.isChecked,
    )

    private fun saveImmediatelyIfReady() {
        if (hydrating) return
        host.showSaving()
        saveDebouncer.saveNow(nextSaveRequest())
    }

    private fun queueSliderSave() {
        host.showSaving()
        saveDebouncer.submit(nextSaveRequest())
    }

    private fun nextSaveRequest(): SaveRequest {
        saveRevision += 1
        return SaveRequest(
            revision = saveRevision,
            settings = settingsFromControls(),
            crossCollision = if (species == PetSpecies.HOOTS) crossCollisionSwitch.isChecked else null,
        )
    }

    private suspend fun persistSettings(request: SaveRequest) {
        repository.saveDashboard(species, request.settings, request.crossCollision)
        val appContext = context?.applicationContext ?: return
        if (PetOverlayService.isRunning()) {
            PetCommands.send(appContext, PetCommands.ACTION_APPLY_SETTINGS)
        }
        if (request.revision == saveRevision && isAdded) host.showSaved()
    }

    private fun showMotionChildren(visible: Boolean) {
        motionChildren.visibility = if (visibleMotionRows(visible).isEmpty()) View.GONE else View.VISIBLE
    }

    private fun refreshDashboard() {
        val overlayAllowed = android.provider.Settings.canDrawOverlays(requireContext())
        val snapshot = PetOverlayService.statusSnapshot()
        val count = snapshot.count(species)
        val content = dashboardContent(overlayAllowed, snapshot.running, count, species)
        heroTitle.text = content.title
        heroSubtitle.text = content.subtitle
        heroAction.text = content.actionLabel
        heroAction.icon = ContextCompat.getDrawable(
            requireContext(),
            when (content.action) {
                DashboardAction.GRANT_PERMISSION -> R.drawable.ic_shield
                DashboardAction.START -> R.drawable.ic_play
                DashboardAction.STOP -> R.drawable.ic_stop
            },
        )
        heroAction.setOnClickListener {
            when (content.action) {
                DashboardAction.GRANT_PERMISSION -> host.openOverlayPermission()
                DashboardAction.START -> host.requestPetStart(species, 0)
                DashboardAction.STOP -> PetCommands.send(requireContext(), PetCommands.ACTION_KILL_SPECIES) {
                    putExtra(PetCommands.EXTRA_SPECIES, species.wireName)
                }
            }
            heroAction.postDelayed(::refreshDashboard, STATUS_REFRESH_DELAY_MILLIS)
        }
        val dotColor = when {
            !overlayAllowed -> R.color.pet_warning
            content.active -> if (species == PetSpecies.HOOTS) R.color.hoots_orange else R.color.pet_success
            else -> R.color.pet_text_secondary
        }
        TextViewCompat.setCompoundDrawableTintList(
            heroTitle,
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), dotColor)),
        )
        spawnButton.isEnabled = overlayAllowed
        detonateButton.isEnabled = count > 0
        refreshNotificationControls()
    }

    private fun refreshNotificationControls() {
        if (!::chatGptNotificationsCard.isInitialized || species != PetSpecies.HOOTS) return
        val supported = NotificationListenerAccess.isSupported(requireContext())
        val granted = supported && NotificationListenerAccess.isGranted(requireContext())
        chatGptNotificationsSwitch.isEnabled = supported
        bubbleAppearanceControls.visibility = if (chatGptNotificationsSwitch.isChecked) {
            View.VISIBLE
        } else {
            View.GONE
        }
        chatGptNotificationsSupport.setText(
            when {
                !supported -> R.string.chatgpt_notifications_unavailable
                chatGptNotificationsSwitch.isChecked && granted ->
                    R.string.chatgpt_notifications_listening
                chatGptNotificationsSwitch.isChecked ->
                    R.string.chatgpt_notifications_permission_required
                else -> R.string.chatgpt_notifications_privacy
            },
        )
        grantNotificationAccessButton.visibility = if (
            supported && chatGptNotificationsSwitch.isChecked && !granted
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun refreshBubbleAppearanceControls() {
        if (!::bubbleBorderColorButton.isInitialized) return
        val borderHex = BubbleColorPickerDialog.formatColor(bubbleBorderColor)
        val fillHex = BubbleColorPickerDialog.formatColor(bubbleFillColor)
        bubbleBorderColorButton.text = getString(
            R.string.bubble_color_value,
            getString(R.string.bubble_border),
            borderHex,
        )
        bubbleFillColorButton.text = getString(
            R.string.bubble_color_value,
            getString(R.string.bubble_fill),
            fillHex,
        )
        bubbleBorderColorButton.strokeColor = ColorStateList.valueOf(bubbleBorderColor)
        bubbleFillColorButton.strokeColor = ColorStateList.valueOf(bubbleFillColor)
        bubbleOpacityValue.text = String.format(
            Locale.getDefault(),
            "%.0f%%",
            bubbleOpacitySlider.value,
        )
    }

    companion object {
        private const val ARG_SPECIES = "species"
        private const val SAVE_DEBOUNCE_MILLIS = 200L
        private const val STATUS_REFRESH_DELAY_MILLIS = 180L
        private const val STATUS_POLL_MILLIS = 500L

        fun newInstance(species: PetSpecies) = PetDashboardFragment().apply {
            arguments = Bundle().apply { putString(ARG_SPECIES, species.wireName) }
        }
    }
}
