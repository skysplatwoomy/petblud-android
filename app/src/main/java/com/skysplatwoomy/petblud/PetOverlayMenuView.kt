package com.skysplatwoomy.petblud

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.util.Locale

@SuppressLint("ViewConstructor")
class PetOverlayMenuView(
    context: Context,
    petId: Long,
    snapshot: PetSnapshot,
    private val listener: Listener,
) : FrameLayout(context) {
    interface Listener {
        fun onDismiss()
        fun onSpawn()
        fun onExplode()
        fun onDetonate()
        fun onGravityChanged(enabled: Boolean)
        fun onPhysicsChanged(multiplier: Double)
        fun onOpenSettings()
        fun onRemoveThis()
        fun onRemoveOthers()
        fun onRemoveAll()
    }

    private val confirmation = DangerConfirmation()
    private val species = snapshot.species
    private val removeAll: MaterialButton by lazy { findViewById(R.id.menuRemoveAll) }
    private val resetConfirmation = Runnable {
        confirmation.reset()
        removeAll.text = context.getString(
            if (species == PetSpecies.SEEDY) R.string.remove_all_seedys else R.string.remove_hoots,
        )
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pet_menu, this, true)
        findViewById<TextView>(R.id.menuTitle).text = context.getString(
            if (species == PetSpecies.SEEDY) R.string.seedy_number else R.string.hoots_title,
            petId,
        )
        findViewById<MaterialButton>(R.id.menuClose).apply {
            contentDescription = context.getString(
                if (species == PetSpecies.SEEDY) R.string.close_seedy_menu else R.string.close_hoots_menu,
            )
            setOnClickListener { listener.onDismiss() }
        }
        findViewById<MaterialButton>(R.id.menuSpawn).apply {
            visibility = if (species == PetSpecies.HOOTS) View.GONE else View.VISIBLE
            setOnClickListener { listener.onSpawn() }
        }
        findViewById<MaterialButton>(R.id.menuExplode).setOnClickListener { listener.onExplode() }
        findViewById<MaterialButton>(R.id.menuDetonate).setOnClickListener { listener.onDetonate() }

        findViewById<MaterialSwitch>(R.id.menuGravity).apply {
            text = context.getString(
                if (species == PetSpecies.SEEDY) R.string.gravity_for_seedy else R.string.gravity_for_hoots,
            )
            isChecked = snapshot.gravityEnabled
            setOnCheckedChangeListener { _, enabled -> listener.onGravityChanged(enabled) }
        }
        val physicsValue = findViewById<TextView>(R.id.menuPhysicsValue)
        findViewById<Slider>(R.id.menuPhysics).apply {
            contentDescription = context.getString(
                if (species == PetSpecies.SEEDY) R.string.physics_for_seedy else R.string.physics_for_hoots,
            )
            value = snapshot.physicsMultiplier.toFloat()
            physicsValue.text = formatPhysics(value)
            addOnChangeListener { _, current, fromUser ->
                physicsValue.text = formatPhysics(current)
                if (fromUser) listener.onPhysicsChanged(current.toDouble())
            }
        }

        findViewById<MaterialButton>(R.id.menuSettings).setOnClickListener {
            listener.onOpenSettings()
        }
        findViewById<MaterialButton>(R.id.menuRemoveThis).apply {
            text = context.getString(
                if (species == PetSpecies.SEEDY) R.string.remove_this_seedy else R.string.remove_hoots,
            )
            setOnClickListener { listener.onRemoveThis() }
        }
        findViewById<MaterialButton>(R.id.menuRemoveOthers).apply {
            visibility = if (species == PetSpecies.HOOTS) View.GONE else View.VISIBLE
            setOnClickListener { listener.onRemoveOthers() }
        }
        removeAll.visibility = if (species == PetSpecies.HOOTS) View.GONE else View.VISIBLE
        removeAll.setOnClickListener {
            removeCallbacks(resetConfirmation)
            if (confirmation.tap(SystemClock.elapsedRealtime())) {
                listener.onRemoveAll()
            } else {
                removeAll.setText(R.string.confirm_remove_all)
                postDelayed(resetConfirmation, CONFIRMATION_TIMEOUT_MILLIS)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            listener.onDismiss()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(resetConfirmation)
        super.onDetachedFromWindow()
    }

    private fun formatPhysics(value: Float): String =
        String.format(Locale.getDefault(), "%.1fx", value)

    companion object {
        private const val CONFIRMATION_TIMEOUT_MILLIS = 3_000L
    }
}
