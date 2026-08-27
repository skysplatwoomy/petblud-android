package com.skysplatwoomy.petblud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.Choreographer
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PetOverlayService : Service(), PetOverlayView.Listener, SensorEventListener {
    private data class ScreenGeometry(
        val leftPx: Int,
        val topPx: Int,
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
    ) {
        val worldBounds: WorldBounds
            get() = WorldBounds(widthPx / density.toDouble(), heightPx / density.toDouble())
    }

    private data class PetWindow(
        val view: PetOverlayView,
        val params: WindowManager.LayoutParams,
        var x: Int = Int.MIN_VALUE,
        var y: Int = Int.MIN_VALUE,
        var width: Int = -1,
        var height: Int = -1,
    )

    private data class MenuWindow(
        val petId: Long,
        val anchorX: Int,
        val anchorY: Int,
        val originWorldX: Double,
        val originWorldY: Double,
        val view: PetOverlayMenuView,
        val params: WindowManager.LayoutParams,
    )

    private data class BubbleWindow(
        val view: HootsStatusBubbleView,
        val params: WindowManager.LayoutParams,
        var status: ChatGptNotificationStatus? = null,
        var x: Int = Int.MIN_VALUE,
        var y: Int = Int.MIN_VALUE,
        var width: Int = -1,
        var height: Int = -1,
        var interactive: Boolean = false,
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var manager: PetManager
    private val geometries = mutableMapOf<PetSpecies, ScreenGeometry>()
    private val geometry: ScreenGeometry
        get() = geometries.getValue(PetSpecies.SEEDY)
    private lateinit var sensorManager: SensorManager
    private lateinit var displayManager: DisplayManager
    private val idleBitmaps = mutableMapOf<PetSpecies, Bitmap>()
    private val spriteSheets = mutableMapOf<PetSpecies, Bitmap?>()
    private val windows = mutableMapOf<Long, PetWindow>()
    private var menuWindow: MenuWindow? = null
    private var bubbleWindow: BubbleWindow? = null
    private var activeChatGptStatus: ChatGptNotificationStatus? = null
    private var currentSettings = AppSettings()
    private var gravitySensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private val registeredMotionSensors = mutableSetOf<Sensor>()
    private var sensorFilterInitialized = false
    private val filteredGravity = FloatArray(3)
    private val shakeDetector = ShakeDetector()

    private val choreographer by lazy { Choreographer.getInstance() }
    private var framePosted = false
    private var lastFrameNanos = 0L
    private var lastGeometryCheckNanos = 0L
    private var screenPaused = false

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        framePosted = false
        if (screenPaused || manager.pets.isEmpty()) {
            lastFrameNanos = 0L
            return@FrameCallback
        }
        val dt = if (lastFrameNanos == 0L) {
            0.0
        } else {
            (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0
        }
        lastFrameNanos = frameTimeNanos
        refreshKeyboardGeometry(frameTimeNanos)
        manager.tick(dt)
        syncWindows()
        postFrame()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenPaused = true
                    dismissPetMenu(restoreBubble = false)
                    dismissHootsBubble()
                    unregisterMotionSensors()
                    stopFrameLoop()
                }

                Intent.ACTION_SCREEN_ON -> {
                    screenPaused = false
                    configureMotionSensor()
                    syncHootsBubble()
                    postFrame()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        displayManager = getSystemService(DisplayManager::class.java)
        settingsRepository = SettingsRepository(this)
        PetSpecies.entries.forEach { geometries[it] = readGeometry(avoidKeyboard = false) }
        manager = PetManager(bounds = geometry.worldBounds)
        liveService = WeakReference(this)
        idleBitmaps[PetSpecies.SEEDY] = decodeBitmap(R.drawable.seedy_idle)
        idleBitmaps[PetSpecies.HOOTS] = decodeBitmap(R.drawable.hoots_idle)
        spriteSheets[PetSpecies.SEEDY] = BitmapFactory.decodeResource(resources, R.drawable.seedy_spritesheet)
        spriteSheets[PetSpecies.HOOTS] = BitmapFactory.decodeResource(resources, R.drawable.hoots_spritesheet)

        createNotificationChannel()
        startForegroundCompat(buildNotification())
        registerScreenReceiver()

        serviceScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                val changedSpecies = PetSpecies.entries.filterTo(linkedSetOf()) {
                    currentSettings.forSpecies(it) != settings.forSpecies(it)
                }
                currentSettings = settings
                manager.applySettings(settings, changedSpecies)
                configureMotionSensor()
                PetSpecies.entries.forEach { species ->
                    updateGeometry(
                        readGeometry(settings.forSpecies(species).avoidKeyboard),
                        species,
                    )
                }
                syncWindows()
            }
        }
        serviceScope.launch {
            ChatGptNotificationBridge.notifications.current.collectLatest { status ->
                activeChatGptStatus = status
                syncHootsBubble()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopEverything()
            return START_NOT_STICKY
        }

        val species = PetCommands.species(intent)
        when (intent?.action) {
            null, PetCommands.ACTION_START -> if (manager.count(species) == 0) manager.spawn(1, species)
            PetCommands.ACTION_SPAWN -> manager.spawn(
                intent.getIntExtra(PetCommands.EXTRA_COUNT, 1),
                species,
            )
            PetCommands.ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }

            PetCommands.ACTION_KILL -> manager.remove(intent.getLongExtra(PetCommands.EXTRA_PET_ID, -1L))
            PetCommands.ACTION_KILL_SPECIES -> manager.killAll(species)
            PetCommands.ACTION_KILL_OTHERS -> manager.killOthersOfSpecies(
                intent.getLongExtra(PetCommands.EXTRA_PET_ID, -1L),
            )

            PetCommands.ACTION_EXPLODE -> manager.explode(
                intent.getDoubleExtra(PetCommands.EXTRA_ORIGIN_X, 0.0),
                intent.getDoubleExtra(PetCommands.EXTRA_ORIGIN_Y, 0.0),
                species,
            )

            PetCommands.ACTION_DETONATE -> manager.detonateAll(species = species)
            PetCommands.ACTION_TOGGLE_GRAVITY -> manager.toggleGravity(
                intent.getLongExtra(PetCommands.EXTRA_PET_ID, -1L),
            )

            PetCommands.ACTION_SET_PET_PHYSICS -> manager.setPetPhysicsMultiplier(
                intent.getLongExtra(PetCommands.EXTRA_PET_ID, -1L),
                intent.getDoubleExtra(PetCommands.EXTRA_PHYSICS_MULTIPLIER, 1.0),
            )

            PetCommands.ACTION_APPLY_SETTINGS -> Unit
        }

        if (manager.pets.isEmpty()) {
            stopEverything()
            return START_NOT_STICKY
        }
        syncWindows()
        postFrame()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lastGeometryCheckNanos = 0L
        PetSpecies.entries.forEach { species ->
            updateGeometry(
                readGeometry(currentSettings.forSpecies(species).avoidKeyboard),
                species,
            )
        }
        syncWindows()
    }

    override fun onDestroy() {
        stopFrameLoop()
        unregisterMotionSensors()
        dismissPetMenu(restoreBubble = false)
        dismissHootsBubble()
        removeAllWindows()
        runCatching { unregisterReceiver(screenReceiver) }
        serviceScope.cancel()
        if (liveService?.get() === this) liveService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        val tiltSpecies = MotionFeatureRouting.tiltSpecies(currentSettings)
        if (tiltSpecies.isEmpty()) return
        if (
            MotionFeatureRouting.shakeSpecies(currentSettings).isNotEmpty() &&
            sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER
        ) {
            shakeDetector.addAccelerometerSample(
                x = sensorEvent.values.getOrElse(0) { 0f }.toDouble(),
                y = sensorEvent.values.getOrElse(1) { 0f }.toDouble(),
                z = sensorEvent.values.getOrElse(2) { 0f }.toDouble(),
                timestampNanos = sensorEvent.timestamp,
            )?.let { strength ->
                MotionFeatureRouting.shakeSpecies(currentSettings)
                    .forEach { manager.detonateAll(strength, it) }
            }
        }

        if (sensorEvent.sensor != gravitySensor) return
        val values = sensorEvent.values
        if (values.size < 3) return

        if (!sensorFilterInitialized) {
            for (index in 0..2) filteredGravity[index] = values[index]
            sensorFilterInitialized = true
        } else {
            for (index in 0..2) {
                filteredGravity[index] =
                    SENSOR_FILTER_ALPHA * filteredGravity[index] +
                    (1f - SENSOR_FILTER_ALPHA) * values[index]
            }
        }

        val (screenX, screenY) = MotionGravityMapper.toScreen(
            rawX = filteredGravity[0].toDouble(),
            rawY = filteredGravity[1].toDouble(),
            displayRotation = displayRotation(),
        )
        if (hypot(screenX, screenY) >= MIN_TILT_PROJECTION) {
            tiltSpecies.forEach { manager.setGravityDirection(screenX, screenY, it) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onPointerDown(
        petId: Long,
        localX: Float,
        localY: Float,
        rawX: Float,
        rawY: Float,
        eventTimeMillis: Long,
    ) {
        val geometry = geometryForPet(petId)
        val localPetPoint = windows[petId]?.view?.toSpriteLocal(localX, localY)
            ?: (localX to localY)
        manager.pointerDown(
            id = petId,
            localX = localPetPoint.first.toDouble() / geometry.density,
            localY = localPetPoint.second.toDouble() / geometry.density,
            rawX = screenToWorldX(rawX, geometry),
            rawY = screenToWorldY(rawY, geometry),
            timeSeconds = eventTimeMillis / 1000.0,
        )
    }

    override fun onPointerMove(petId: Long, rawX: Float, rawY: Float, eventTimeMillis: Long) {
        val geometry = geometryForPet(petId)
        manager.pointerMove(
            id = petId,
            rawX = screenToWorldX(rawX, geometry),
            rawY = screenToWorldY(rawY, geometry),
            timeSeconds = eventTimeMillis / 1000.0,
        )
        syncWindows()
    }

    override fun onPointerUp(
        petId: Long,
        localX: Float,
        localY: Float,
        rawX: Float,
        rawY: Float,
        eventTimeMillis: Long,
    ) {
        val geometry = geometryForPet(petId)
        val localPetPoint = windows[petId]?.view?.toSpriteLocal(localX, localY)
            ?: (localX to localY)
        manager.pointerUp(
            id = petId,
            localX = localPetPoint.first.toDouble() / geometry.density,
            rawX = screenToWorldX(rawX, geometry),
            rawY = screenToWorldY(rawY, geometry),
            timeSeconds = eventTimeMillis / 1000.0,
        )
        postFrame()
    }

    override fun onPointerCancel(petId: Long) {
        manager.cancelPointer(petId)
    }

    override fun onLongPress(petId: Long, rawX: Float, rawY: Float) {
        showPetMenu(petId, rawX.roundToInt(), rawY.roundToInt())
    }

    private fun syncWindows() {
        if (!Settings.canDrawOverlays(this)) {
            stopEverything()
            return
        }

        val activeIds = manager.pets.mapTo(mutableSetOf()) { it.id }
        if (menuWindow?.petId !in activeIds) dismissPetMenu(restoreBubble = false)
        windows.keys.filterNot(activeIds::contains).forEach(::removeWindow)

        manager.pets.forEach { pet ->
            val settings = currentSettings.forSpecies(pet.species)
            val geometry = geometryFor(pet.species)
            val rotateSprites = settings.tiltGravity && settings.keepUpright
            val rotationDegrees = if (rotateSprites) {
                val gravity = manager.gravityFor(pet.species)
                MotionGravityMapper.spriteRotationDegrees(gravity.first, gravity.second)
            } else {
                0f
            }
            val petWindow = windows[pet.id] ?: addWindow(pet)
            val spriteWidth = (pet.width * geometry.density).roundToInt().coerceAtLeast(1)
            val spriteHeight = (pet.height * geometry.density).roundToInt().coerceAtLeast(1)
            val diagonal = ceil(hypot(spriteWidth.toDouble(), spriteHeight.toDouble()))
                .toInt().coerceAtLeast(1)
            val width = if (rotateSprites) diagonal else spriteWidth
            val height = if (rotateSprites) diagonal else spriteHeight
            val centerX = geometry.leftPx + ((pet.x + pet.width / 2.0) * geometry.density)
            val centerY = geometry.topPx + ((pet.y + pet.height / 2.0) * geometry.density)
            val x = (centerX - width / 2.0).roundToInt()
            val y = (centerY - height / 2.0).roundToInt()
            val changed =
                petWindow.x != x || petWindow.y != y ||
                    petWindow.width != width || petWindow.height != height
            if (changed) {
                petWindow.params.x = x
                petWindow.params.y = y
                petWindow.params.width = width
                petWindow.params.height = height
                petWindow.x = x
                petWindow.y = y
                petWindow.width = width
                petWindow.height = height
                runCatching { windowManager.updateViewLayout(petWindow.view, petWindow.params) }
            }
            petWindow.view.showFrame(
                value = pet.animation,
                index = pet.frameIndex,
                rotationDegrees = rotationDegrees,
                spriteWidthPx = spriteWidth,
                spriteHeightPx = spriteHeight,
            )
        }
        syncHootsBubble()
    }

    private fun addWindow(pet: PetState): PetWindow {
        val view = PetOverlayView(
            context = this,
            petId = pet.id,
            species = pet.species,
            spriteSheet = spriteSheets[pet.species],
            idleBitmap = idleBitmaps.getValue(pet.species),
            listener = this,
        )
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "PetBlud ${pet.species.displayName} ${pet.id}"
        }
        val petWindow = PetWindow(view = view, params = params)
        windows[pet.id] = petWindow
        windowManager.addView(view, params)
        return petWindow
    }

    private fun removeWindow(id: Long) {
        windows.remove(id)?.let { petWindow ->
            runCatching { windowManager.removeViewImmediate(petWindow.view) }
        }
    }

    private fun removeAllWindows() {
        windows.keys.toList().forEach(::removeWindow)
    }

    private fun showPetMenu(petId: Long, anchorX: Int, anchorY: Int) {
        val snapshot = manager.snapshot(petId) ?: return
        dismissPetMenu(restoreBubble = false)
        val species = snapshot.species
        val petGeometry = geometryFor(species)
        val originWorldX = screenToWorldX(anchorX.toFloat(), petGeometry)
        val originWorldY = screenToWorldY(anchorY.toFloat(), petGeometry)
        val theme = if (species == PetSpecies.HOOTS) {
            R.style.Theme_PetBlud_Hoots
        } else {
            R.style.Theme_PetBlud
        }
        val themedContext = ContextThemeWrapper(this, theme)
        val listener = object : PetOverlayMenuView.Listener {
            override fun onDismiss() = dismissPetMenu()

            override fun onSpawn() {
                manager.spawn(1, species)
                syncWindows()
                postFrame()
            }

            override fun onExplode() {
                manager.explode(originWorldX, originWorldY, species)
                dismissPetMenu()
                syncWindows()
                postFrame()
            }

            override fun onDetonate() {
                manager.detonateAll(species = species)
                dismissPetMenu()
                syncWindows()
                postFrame()
            }

            override fun onGravityChanged(enabled: Boolean) {
                val current = manager.snapshot(petId)?.gravityEnabled ?: return
                if (current != enabled) manager.toggleGravity(petId)
            }

            override fun onPhysicsChanged(multiplier: Double) {
                manager.setPetPhysicsMultiplier(petId, multiplier)
            }

            override fun onChatGptBubbleChanged(enabled: Boolean) {
                if (species != PetSpecies.HOOTS) return
                if (!enabled) dismissHootsBubble()
                serviceScope.launch {
                    settingsRepository.setHootsChatGptNotifications(enabled)
                }
            }

            override fun onOpenSettings() {
                dismissPetMenu()
                val intent = Intent(this@PetOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_SPECIES, species.wireName)
                }
                runCatching { startActivity(intent) }
            }

            override fun onRemoveThis() {
                manager.remove(petId)
                dismissPetMenu()
                finishAction()
            }

            override fun onRemoveOthers() {
                manager.killOthersOfSpecies(petId)
                syncWindows()
            }

            override fun onRemoveAll() {
                manager.killAll(species)
                dismissPetMenu()
                finishAction()
            }
        }
        val view = PetOverlayMenuView(themedContext, petId, snapshot, listener)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "PetBlud ${species.displayName} menu"
        }
        val window = MenuWindow(
            petId = petId,
            anchorX = anchorX,
            anchorY = anchorY,
            originWorldX = originWorldX,
            originWorldY = originWorldY,
            view = view,
            params = params,
        )
        menuWindow = window
        positionPetMenu(window)
        runCatching { windowManager.addView(view, params) }
            .onFailure { menuWindow = null }
        if (species == PetSpecies.HOOTS) dismissHootsBubble()
    }

    private fun positionPetMenu(window: MenuWindow) {
        val menuGeometry = readGeometry(avoidKeyboard = true)
        val margin = dp(MENU_MARGIN_DP)
        val width = minOf(
            dp(MENU_MAX_WIDTH_DP),
            (menuGeometry.widthPx - margin * 2).coerceAtLeast(1),
        )
        val maxHeight = (menuGeometry.heightPx - margin * 2).coerceAtLeast(1)
        window.view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
        )
        val height = window.view.measuredHeight.coerceIn(1, maxHeight)
        val bounds = PixelBounds(
            left = menuGeometry.leftPx + margin,
            top = menuGeometry.topPx + margin,
            right = menuGeometry.leftPx + menuGeometry.widthPx - margin,
            bottom = menuGeometry.topPx + menuGeometry.heightPx - margin,
        )
        val point = PopoverPlacement.place(
            anchorX = window.anchorX,
            anchorY = window.anchorY,
            menuWidth = width,
            menuHeight = height,
            bounds = bounds,
            gap = dp(MENU_GAP_DP),
        )
        window.params.width = width
        window.params.height = height
        window.params.x = point.x
        window.params.y = point.y
    }

    private fun repositionPetMenu() {
        val window = menuWindow ?: return
        positionPetMenu(window)
        runCatching { windowManager.updateViewLayout(window.view, window.params) }
    }

    private fun dismissPetMenu(restoreBubble: Boolean = true) {
        val window = menuWindow ?: return
        menuWindow = null
        runCatching { windowManager.removeViewImmediate(window.view) }
        if (restoreBubble) syncHootsBubble()
    }

    private fun finishAction() {
        if (manager.pets.isEmpty()) stopEverything() else {
            syncWindows()
            postFrame()
        }
    }

    private fun stopEverything() {
        manager.killAll()
        dismissPetMenu(restoreBubble = false)
        dismissHootsBubble()
        removeAllWindows()
        stopFrameLoop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postFrame() {
        if (framePosted || screenPaused || manager.pets.isEmpty()) return
        framePosted = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (framePosted) choreographer.removeFrameCallback(frameCallback)
        framePosted = false
        lastFrameNanos = 0L
    }

    private fun readGeometry(avoidKeyboard: Boolean): ScreenGeometry {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val windowInsets = metrics.windowInsets
            val insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val keyboardBottom = if (
                avoidKeyboard && windowInsets.isVisible(WindowInsets.Type.ime())
            ) {
                windowInsets.getInsets(WindowInsets.Type.ime()).bottom
            } else {
                0
            }
            return ScreenGeometry(
                leftPx = bounds.left + insets.left,
                topPx = bounds.top + insets.top,
                widthPx = (bounds.width() - insets.left - insets.right).coerceAtLeast(1),
                heightPx = (
                    bounds.height() - insets.top - max(insets.bottom, keyboardBottom)
                ).coerceAtLeast(1),
                density = density,
            )
        }
        val displayMetrics = resources.displayMetrics
        return ScreenGeometry(
            leftPx = 0,
            topPx = 0,
            widthPx = displayMetrics.widthPixels.coerceAtLeast(1),
            heightPx = displayMetrics.heightPixels.coerceAtLeast(1),
            density = density,
        )
    }

    private fun refreshKeyboardGeometry(frameTimeNanos: Long) {
        if (
            (
                PetSpecies.entries.none { currentSettings.forSpecies(it).avoidKeyboard } &&
                    menuWindow == null && bubbleWindow == null
            ) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) return
        if (frameTimeNanos - lastGeometryCheckNanos < GEOMETRY_CHECK_INTERVAL_NANOS) return
        lastGeometryCheckNanos = frameTimeNanos
        PetSpecies.entries.forEach { species ->
            updateGeometry(
                readGeometry(avoidKeyboard = currentSettings.forSpecies(species).avoidKeyboard),
                species,
            )
        }
        if (menuWindow != null) repositionPetMenu()
        if (bubbleWindow != null) syncHootsBubble()
    }

    private fun updateGeometry(updated: ScreenGeometry, species: PetSpecies) {
        if (geometries[species] == updated) return
        geometries[species] = updated
        manager.updateBounds(updated.worldBounds, species)
        repositionPetMenu()
    }

    private fun configureMotionSensor() {
        unregisterMotionSensors()
        val tiltSpecies = MotionFeatureRouting.tiltSpecies(currentSettings)
        PetSpecies.entries.filterNot(tiltSpecies::contains).forEach(manager::resetGravityDirection)
        if (tiltSpecies.isEmpty() || screenPaused) {
            return
        }
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: accelerometerSensor
        sensorFilterInitialized = false
        registerMotionSensor(gravitySensor)
        if (MotionFeatureRouting.shakeSpecies(currentSettings).isNotEmpty()) {
            registerMotionSensor(accelerometerSensor)
        }
    }

    private fun registerMotionSensor(sensor: Sensor?) {
        if (sensor == null || sensor in registeredMotionSensors) return
        if (sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
            registeredMotionSensors += sensor
        }
    }

    private fun unregisterMotionSensors() {
        if (registeredMotionSensors.isNotEmpty()) sensorManager.unregisterListener(this)
        registeredMotionSensors.clear()
        gravitySensor = null
        accelerometerSensor = null
        sensorFilterInitialized = false
        shakeDetector.reset()
    }

    private fun displayRotation(): Int = runCatching {
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
    }.getOrNull() ?: Surface.ROTATION_0

    private fun geometryFor(species: PetSpecies): ScreenGeometry = geometries.getValue(species)

    private fun geometryForPet(petId: Long): ScreenGeometry =
        geometryFor(manager.speciesOf(petId) ?: PetSpecies.SEEDY)

    private fun screenToWorldX(screenX: Float, geometry: ScreenGeometry): Double =
        (screenX - geometry.leftPx) / geometry.density.toDouble()

    private fun screenToWorldY(screenY: Float, geometry: ScreenGeometry): Double =
        (screenY - geometry.topPx) / geometry.density.toDouble()

    private fun dp(value: Int): Int = (value * geometry.density).roundToInt()

    private fun syncHootsBubble() {
        val status = activeChatGptStatus
        val hoots = manager.pets.firstOrNull { it.species == PetSpecies.HOOTS }
        val petWindow = hoots?.let { windows[it.id] }
        val hootsMenuOpen = menuWindow?.petId?.let(manager::speciesOf) == PetSpecies.HOOTS
        if (
            status == null || hoots == null || petWindow == null || screenPaused || hootsMenuOpen ||
            !currentSettings.hoots.showChatGptNotifications || !Settings.canDrawOverlays(this)
        ) {
            dismissHootsBubble()
            return
        }

        val boundsGeometry = readGeometry(avoidKeyboard = true)
        val margin = dp(BUBBLE_MARGIN_DP)
        val bounds = PixelBounds(
            left = boundsGeometry.leftPx + margin,
            top = boundsGeometry.topPx + margin,
            right = boundsGeometry.leftPx + boundsGeometry.widthPx - margin,
            bottom = boundsGeometry.topPx + boundsGeometry.heightPx - margin,
        )
        val maxWidth = minOf(
            dp(BUBBLE_MAX_WIDTH_DP),
            (bounds.right - bounds.left).coerceAtLeast(1),
        )
        val minWidth = minOf(dp(BUBBLE_MIN_WIDTH_DP), maxWidth)
        val window = bubbleWindow ?: createBubbleWindow()
        window.view.showStatus(status)
        window.view.applyAppearance(
            border = currentSettings.hoots.bubbleBorderColor,
            fill = currentSettings.hoots.bubbleFillColor,
            opacity = currentSettings.hoots.bubbleOpacityPercent,
        )
        val interactive = currentSettings.hoots.bubbleTapOpensChatGpt
        window.view.isClickable = interactive
        window.params.flags = HootsBubbleInteraction.windowFlags(interactive)
        window.view.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val width = window.view.measuredWidth.coerceIn(minWidth, maxWidth)
        window.view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = window.view.measuredHeight.coerceAtLeast(1)
        val placement = HootsBubblePlacement.place(
            pet = PixelRect(
                left = petWindow.x,
                top = petWindow.y,
                right = petWindow.x + petWindow.width,
                bottom = petWindow.y + petWindow.height,
            ),
            bubbleWidth = width,
            bubbleHeight = height,
            bounds = bounds,
            gap = dp(BUBBLE_GAP_DP),
            tailInset = dp(BUBBLE_TAIL_INSET_DP),
        )
        window.view.setTail(placement.tailAtTop, placement.tailCenterX)
        window.params.x = placement.x
        window.params.y = placement.y
        window.params.width = width
        window.params.height = height
        val changed =
            window.status != status || window.x != placement.x || window.y != placement.y ||
                window.width != width || window.height != height || window.interactive != interactive
        window.status = status
        window.x = placement.x
        window.y = placement.y
        window.width = width
        window.height = height
        window.interactive = interactive
        if (bubbleWindow == null) {
            bubbleWindow = window
            runCatching { windowManager.addView(window.view, window.params) }
                .onFailure { bubbleWindow = null }
        } else if (changed) {
            runCatching { windowManager.updateViewLayout(window.view, window.params) }
        }
    }

    private fun createBubbleWindow(): BubbleWindow {
        val view = HootsStatusBubbleView(this).apply {
            setOnClickListener { openChatGpt() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            HootsBubbleInteraction.windowFlags(interactive = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "PetBlud Hoots ChatGPT status"
        }
        return BubbleWindow(view = view, params = params)
    }

    private fun openChatGpt() {
        val launchIntent = packageManager.getLaunchIntentForPackage(
            ChatGptNotificationParser.CHATGPT_PACKAGE,
        ) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launchIntent) }
    }

    private fun dismissHootsBubble() {
        val window = bubbleWindow ?: return
        bubbleWindow = null
        runCatching { windowManager.removeViewImmediate(window.view) }
    }

    private fun decodeBitmap(resourceId: Int): Bitmap =
        BitmapFactory.decodeResource(resources, resourceId)
            ?: error("Could not decode bundled pet sprite $resourceId")

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val spawnIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PetOverlayService::class.java).apply {
                action = PetCommands.ACTION_SPAWN
                putExtra(PetCommands.EXTRA_COUNT, 1)
                putExtra(PetCommands.EXTRA_SPECIES, PetSpecies.SEEDY.wireName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, PetOverlayService::class.java).apply { action = PetCommands.ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hootsIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, PetOverlayService::class.java).apply {
                action = PetCommands.ACTION_START
                putExtra(PetCommands.EXTRA_SPECIES, PetSpecies.HOOTS.wireName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionIcon = Icon.createWithResource(this, R.drawable.ic_pet)
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(actionIcon, "+ Seedy", spawnIntent).build())
            .addAction(Notification.Action.Builder(actionIcon, "Wake Hoots", hootsIntent).build())
            .addAction(Notification.Action.Builder(actionIcon, "Stop all", stopIntent).build())
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "petblud_overlay"
        private const val NOTIFICATION_ID = 20260721
        private const val SENSOR_FILTER_ALPHA = 0.82f
        private const val MIN_TILT_PROJECTION = 1.0
        private const val GEOMETRY_CHECK_INTERVAL_NANOS = 100_000_000L
        private const val MENU_MAX_WIDTH_DP = 320
        private const val MENU_MARGIN_DP = 12
        private const val MENU_GAP_DP = 10
        private const val BUBBLE_MAX_WIDTH_DP = 280
        private const val BUBBLE_MIN_WIDTH_DP = 160
        private const val BUBBLE_MARGIN_DP = 8
        private const val BUBBLE_GAP_DP = 5
        private const val BUBBLE_TAIL_INSET_DP = 24

        @Volatile
        private var liveService: WeakReference<PetOverlayService>? = null

        fun isRunning(): Boolean = liveService?.get() != null

        fun statusSnapshot(): ServiceStatus = liveService?.get()?.let { service ->
            ServiceStatus(
                running = true,
                seedyCount = service.manager.count(PetSpecies.SEEDY),
                hootsCount = service.manager.count(PetSpecies.HOOTS),
            )
        } ?: ServiceStatus(running = false, seedyCount = 0, hootsCount = 0)

        fun snapshot(petId: Long): PetSnapshot? = liveService?.get()?.manager?.snapshot(petId)
    }
}

data class ServiceStatus(
    val running: Boolean,
    val seedyCount: Int,
    val hootsCount: Int,
) {
    val petCount: Int
        get() = seedyCount + hootsCount

    fun count(species: PetSpecies): Int = when (species) {
        PetSpecies.SEEDY -> seedyCount
        PetSpecies.HOOTS -> hootsCount
    }
}
