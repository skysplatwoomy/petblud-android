package com.skysplatwoomy.petblud

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class PetManager(
    settings: PetSettings = PetSettings(),
    bounds: WorldBounds,
    private val random: Random = Random.Default,
    hootsSettings: PetSettings = PetSettings(),
    seedyHootsCollision: Boolean = false,
) {
    private val mutablePets = mutableListOf<PetState>()
    val pets: List<PetState>
        get() = mutablePets

    var appSettings: AppSettings = AppSettings(
        seedy = settings,
        hoots = hootsSettings,
        seedyHootsCollision = seedyHootsCollision,
    ).normalized()
        private set

    private val speciesBounds = PetSpecies.entries.associateWith { bounds.normalized() }.toMutableMap()
    private val gravityDirections = PetSpecies.entries.associateWith { 0.0 to 1.0 }.toMutableMap()

    val settings: PetSettings
        get() = appSettings.seedy

    val bounds: WorldBounds
        get() = boundsFor(PetSpecies.SEEDY)

    val gravityX: Double
        get() = gravityFor(PetSpecies.SEEDY).first

    val gravityY: Double
        get() = gravityFor(PetSpecies.SEEDY).second

    private var nextId = 1L

    fun spawn(count: Int = 1, species: PetSpecies = PetSpecies.SEEDY): List<PetState> {
        val existing = count(species)
        val limit = if (species == PetSpecies.SEEDY) MAX_PETS else MAX_HOOTS
        val allowed = (limit - existing).coerceAtLeast(0)
        val actualCount = count.coerceIn(0, allowed)
        val settings = settingsFor(species)
        val bounds = boundsFor(species)
        return buildList {
            repeat(actualCount) {
                val width = PetState.baseWidth(species) * settings.sizePercent / 100.0
                val height = PetState.baseHeight(species) * settings.sizePercent / 100.0
                val pet = PetState(
                    id = nextId++,
                    species = species,
                    x = random.nextDouble() * (bounds.width - width).coerceAtLeast(0.0),
                    y = random.nextDouble() * (bounds.height - height).coerceAtLeast(0.0),
                    settings = settings,
                    specialTimer = random.nextDouble(4.5, 5.5),
                )
                if (physicsInteractionsDisabled(pet)) pet.vx = 0.0
                mutablePets += pet
                add(pet)
            }
        }
    }

    fun remove(id: Long): Boolean = mutablePets.removeAll { it.id == id }

    fun count(species: PetSpecies): Int = mutablePets.count { it.species == species }

    fun killAll() {
        mutablePets.clear()
    }

    fun killAll(species: PetSpecies) {
        mutablePets.removeAll { it.species == species }
    }

    fun killAllExcept(id: Long) {
        mutablePets.removeAll { it.id != id }
    }

    fun killOthersOfSpecies(id: Long) {
        val survivor = find(id) ?: return
        mutablePets.removeAll { it.species == survivor.species && it.id != id }
    }

    fun snapshot(id: Long): PetSnapshot? = find(id)?.let { pet ->
        pet.snapshot(
            showChatGptNotifications = pet.species == PetSpecies.HOOTS &&
                appSettings.hoots.showChatGptNotifications,
        )
    }

    fun applySettings(value: PetSettings) {
        applySettings(appSettings.copy(seedy = value))
    }

    fun applySettings(
        value: AppSettings,
        changedSpecies: Set<PetSpecies> = PetSpecies.entries.toSet(),
    ) {
        appSettings = value.normalized()
        mutablePets.filter { it.species in changedSpecies }.forEach { pet ->
            val settings = settingsFor(pet.species)
            pet.gravityEnabled = settings.gravity
            pet.physicsMultiplier = settings.physicsMultiplier
            pet.sizePercent = settings.sizePercent
            if (physicsInteractionsDisabled(pet)) {
                stopPhysicsMotion(pet)
                setAnimation(pet, PetAnimation.IDLE, loop = true, restart = true)
            }
            clampPosition(pet)
        }
    }

    fun updateBounds(value: WorldBounds, species: PetSpecies? = null) {
        if (species == null) {
            PetSpecies.entries.forEach { speciesBounds[it] = value.normalized() }
            mutablePets.forEach(::clampPosition)
        } else {
            speciesBounds[species] = value.normalized()
            mutablePets.filter { it.species == species }.forEach(::clampPosition)
        }
    }

    fun setGravityDirection(x: Double, y: Double, species: PetSpecies = PetSpecies.SEEDY) {
        val length = hypot(x, y)
        if (!length.isFinite() || length < MIN_GRAVITY_VECTOR_LENGTH) return
        gravityDirections[species] = x / length to y / length
    }

    fun resetGravityDirection(species: PetSpecies = PetSpecies.SEEDY) {
        gravityDirections[species] = 0.0 to 1.0
    }

    fun toggleGravity(id: Long): Boolean? {
        val pet = find(id) ?: return null
        pet.gravityEnabled = !pet.gravityEnabled
        return pet.gravityEnabled
    }

    fun setPetPhysicsMultiplier(id: Long, multiplier: Double) {
        find(id)?.physicsMultiplier = multiplier.coerceIn(
            PetSettings.MIN_PHYSICS_MULTIPLIER,
            PetSettings.MAX_PHYSICS_MULTIPLIER,
        )
    }

    fun pointerDown(
        id: Long,
        localX: Double,
        localY: Double,
        rawX: Double,
        rawY: Double,
        timeSeconds: Double,
    ) {
        val pet = find(id) ?: return
        pet.dragging = true
        pet.didDrag = false
        pet.dragDx = localX
        pet.dragDy = localY
        pet.pressRootX = rawX
        pet.pressRootY = rawY
        pet.dragSamples.clear()
        pet.dragSamples += DragSample(timeSeconds, rawX, rawY)
    }

    fun pointerMove(id: Long, rawX: Double, rawY: Double, timeSeconds: Double) {
        val pet = find(id) ?: return
        if (!pet.dragging) return

        val moved = hypot(rawX - pet.pressRootX, rawY - pet.pressRootY)
        if (!pet.didDrag) {
            if (moved < DRAG_THRESHOLD) return
            pet.didDrag = true
            pet.vx = 0.0
            pet.vy = 0.0
            pet.cheerOnLand = false
            pet.pendingOneShot = null
            pet.dragSamples.clear()
            pet.dragSamples += DragSample(timeSeconds, rawX, rawY)
            setAnimation(pet, PetAnimation.JUMP, loop = true, restart = true)
        }

        recordDragSample(pet, rawX, rawY, timeSeconds)
        val velocity = velocityFromSamples(pet.dragSamples)
        if (physicsInteractionsDisabled(pet)) {
            pet.vx = 0.0
            pet.vy = 0.0
        } else {
            pet.vx = velocity.first
            pet.vy = velocity.second
        }
        if (abs(pet.vx) > 1.0) pet.facing = if (pet.vx > 0.0) 1 else -1
        val bounds = boundsFor(pet.species)
        pet.x = (rawX - pet.dragDx).coerceIn(0.0, (bounds.width - pet.width).coerceAtLeast(0.0))
        pet.y = (rawY - pet.dragDy).coerceIn(0.0, (bounds.height - pet.height).coerceAtLeast(0.0))
    }

    fun pointerUp(
        id: Long,
        localX: Double,
        rawX: Double,
        rawY: Double,
        timeSeconds: Double,
    ) {
        val pet = find(id) ?: return
        if (!pet.dragging) return

        if (physicsInteractionsDisabled(pet)) {
            stopPhysicsMotion(pet)
            setAnimation(pet, PetAnimation.IDLE, loop = true, restart = true)
        } else if (!pet.didDrag) {
            hop(pet, localX)
        } else {
            recordDragSample(pet, rawX, rawY, timeSeconds)
            val velocity = velocityFromSamples(pet.dragSamples)
            pet.vx = velocity.first
            pet.vy = velocity.second
            val speed = hypot(pet.vx, pet.vy)
            pet.pendingOneShot = when {
                speed > 900.0 -> PetAnimation.DIZZY
                speed > 500.0 -> PetAnimation.SPIN
                else -> pet.pendingOneShot
            }
        }
        pet.dragSamples.clear()
        pet.dragging = false
        pet.didDrag = false
    }

    fun cancelPointer(id: Long) {
        find(id)?.apply {
            dragSamples.clear()
            dragging = false
            didDrag = false
            if (physicsInteractionsDisabled(this)) {
                stopPhysicsMotion(this)
                setAnimation(this, PetAnimation.IDLE, loop = true, restart = true)
            }
        }
    }

    fun explode(originX: Double, originY: Double, species: PetSpecies = PetSpecies.SEEDY) {
        mutablePets.filter {
            it.species == species && !physicsInteractionsDisabled(it)
        }.forEach { pet ->
            val centerX = pet.x + pet.width / 2.0
            val centerY = pet.y + pet.height / 2.0
            var dx = centerX - originX
            var dy = centerY - originY
            var distance = hypot(dx, dy)
            if (distance < 1.0) {
                val angle = (pet.id % 16) * TAU / 16.0
                dx = cos(angle)
                dy = sin(angle)
                distance = 1.0
            }
            val strength = 1050.0 / max(1.0, distance / 180.0)
            pet.vx += dx / distance * strength
            pet.vy += dy / distance * strength
            pet.facing = if (pet.vx >= 0.0) 1 else -1
            pet.pendingOneShot = PetAnimation.DIZZY
            setAnimation(pet, PetAnimation.JUMP, loop = true, restart = true)
        }
    }

    fun detonateAll(
        strengthMultiplier: Double = 1.0,
        species: PetSpecies = PetSpecies.SEEDY,
    ) {
        val multiplier = strengthMultiplier.coerceIn(1.0, MAX_DETONATION_MULTIPLIER)
        mutablePets.filter {
            it.species == species && !physicsInteractionsDisabled(it)
        }.forEach { pet ->
            val angle = random.nextDouble(0.0, TAU)
            val strength = random.nextDouble(750.0, 1150.0) * multiplier
            pet.vx += cos(angle) * strength
            pet.vy += sin(angle) * strength
            pet.facing = if (pet.vx >= 0.0) 1 else -1
            pet.pendingOneShot = PetAnimation.DIZZY
            setAnimation(pet, PetAnimation.JUMP, loop = true, restart = true)
        }
    }

    fun tick(deltaSeconds: Double) {
        val dt = deltaSeconds.coerceIn(0.0, MAX_DELTA_SECONDS)
        mutablePets.forEach { tickPet(it, dt) }
        if (appSettings.seedy.collision || appSettings.seedyHootsCollision) applyCollisions()
        mutablePets.forEach(::clampPosition)
    }

    private fun tickPet(pet: PetState, dt: Double) {
        val bounds = boundsFor(pet.species)
        val (gravityX, gravityY) = gravityFor(pet.species)
        val maxX = (bounds.width - pet.width).coerceAtLeast(0.0)
        val maxY = (bounds.height - pet.height).coerceAtLeast(0.0)
        var impactSpeed = 0.0
        val physicsDisabled = physicsInteractionsDisabled(pet)

        if (physicsDisabled) {
            pet.vx = 0.0
            pet.vy = 0.0
        } else if (!(pet.dragging && pet.didDrag)) {
            val modifier = pet.physicsMultiplier
            if (pet.gravityEnabled) {
                pet.vx += gravityX * GRAVITY * modifier * dt
                pet.vy += gravityY * GRAVITY * modifier * dt
            }
            pet.x += pet.vx * modifier * dt
            pet.y += pet.vy * modifier * dt

            if (pet.x < 0.0) {
                val speed = abs(pet.vx)
                pet.x = 0.0
                pet.vx = abs(pet.vx) * 0.8
                pet.facing = 1
                if (gravityX < 0.0) {
                    impactSpeed = max(impactSpeed, speed)
                    if (abs(pet.vx) < REST_SPEED) pet.vx = 0.0
                }
            } else if (pet.x > maxX) {
                val speed = abs(pet.vx)
                pet.x = maxX
                pet.vx = -abs(pet.vx) * 0.8
                pet.facing = -1
                if (gravityX > 0.0) {
                    impactSpeed = max(impactSpeed, speed)
                    if (abs(pet.vx) < REST_SPEED) pet.vx = 0.0
                }
            }

            if (pet.y < 0.0) {
                val speed = abs(pet.vy)
                pet.y = 0.0
                pet.vy = abs(pet.vy) * 0.7
                if (gravityY < 0.0) {
                    impactSpeed = max(impactSpeed, speed)
                    if (abs(pet.vy) < REST_SPEED) pet.vy = 0.0
                }
            } else if (pet.y > maxY) {
                val speed = abs(pet.vy)
                pet.y = maxY
                pet.vy = -abs(pet.vy) * 0.58
                pet.vx *= 0.9
                if (gravityY > 0.0) {
                    impactSpeed = max(impactSpeed, speed)
                    if (abs(pet.vy) < REST_SPEED) pet.vy = 0.0
                }
            }
            if (abs(pet.vx) < 2.0) pet.vx = 0.0
        }

        if (
            impactSpeed >= HARD_LAND_SPEED &&
            !pet.cheerOnLand &&
            pet.pendingOneShot == null &&
            !oneShotPlaying(pet)
        ) {
            pet.pendingOneShot = PetAnimation.DIZZY
        } else if (
            impactSpeed >= HARD_LAND_SPEED * 0.7 &&
            pet.pendingOneShot == null &&
            !oneShotPlaying(pet) &&
            pet.animation == PetAnimation.FALL
        ) {
            pet.pendingOneShot = PetAnimation.SPIN
        }

        val onGravitySurface = physicsDisabled || isOnGravitySurface(pet, maxX, maxY)
        val activelyDragging = pet.dragging && pet.didDrag
        maybeIdleSpecial(pet, dt, onGravitySurface && !activelyDragging)
        pickAnimation(pet, onGravitySurface, impactSpeed)
        if (pet.animation == null) pickAnimation(pet, onGravitySurface, 0.0)
        if (pet.animation != null && !activelyDragging) advanceAnimation(pet, dt)
    }

    private fun pickAnimation(pet: PetState, onGravitySurface: Boolean, impactSpeed: Double) {
        if (pet.dragging && pet.didDrag) {
            setAnimation(pet, PetAnimation.JUMP, loop = true)
            pet.frameIndex = minOf(2, PetAnimation.JUMP.frameCount - 1)
            return
        }
        if (pet.animation != null && oneShotPlaying(pet)) return

        if (onGravitySurface) {
            pet.pendingOneShot?.let { pending ->
                pet.pendingOneShot = null
                pet.cheerOnLand = false
                startOneShot(pet, pending)
                return
            }
            if (pet.cheerOnLand && abs(pet.vx) < WALK_SPEED && impactSpeed < HARD_LAND_SPEED) {
                pet.cheerOnLand = false
                startOneShot(pet, PetAnimation.CHEER)
                return
            }
            val surfaceVelocity = tangentVelocity(pet)
            if (abs(surfaceVelocity) >= WALK_SPEED) {
                pet.facing = if (pet.vx != 0.0) {
                    if (pet.vx > 0.0) 1 else -1
                } else {
                    if (surfaceVelocity > 0.0) 1 else -1
                }
                setAnimation(
                    pet,
                    if (pet.facing > 0) PetAnimation.WALK_RIGHT else PetAnimation.WALK_LEFT,
                    loop = true,
                )
            } else {
                setAnimation(pet, PetAnimation.IDLE, loop = true)
            }
            return
        }

        if (!pet.gravityEnabled) {
            setAnimation(pet, PetAnimation.IDLE, loop = true)
        } else if (gravityVelocity(pet) < -40.0) {
            setAnimation(pet, PetAnimation.JUMP, loop = true)
        } else if (gravityVelocity(pet) > FALL_SPEED) {
            setAnimation(pet, PetAnimation.FALL, loop = true)
        } else {
            setAnimation(pet, PetAnimation.JUMP, loop = true)
            pet.frameIndex = PetAnimation.JUMP.frameCount - 1
        }
    }

    private fun maybeIdleSpecial(pet: PetState, dt: Double, onFloor: Boolean) {
        if (!onFloor || pet.didDrag || oneShotPlaying(pet) || abs(tangentVelocity(pet)) >= WALK_SPEED) return
        pet.specialTimer -= dt
        if (pet.specialTimer > 0.0) return
        pet.specialTimer = random.nextDouble(4.5, 5.5)
        val choices = idleAnimationsFor(pet.species)
        val animation = if (pet.species == PetSpecies.HOOTS) {
            choices[random.nextInt(choices.size)]
        } else {
            when (random.nextInt(100)) {
                in 0..29 -> PetAnimation.WAVE
                in 30..59 -> PetAnimation.EAT
                in 60..69 -> PetAnimation.CODE
                else -> PetAnimation.CHEER
            }
        }
        startOneShot(pet, animation)
    }

    private fun hop(pet: PetState, localX: Double) {
        val (gravityX, gravityY) = gravityFor(pet.species)
        val tangentX = gravityY
        val tangentY = -gravityX
        val currentTangentVelocity = pet.vx * tangentX + pet.vy * tangentY
        val sideImpulse = if (localX < pet.width / 2.0) 150.0 else -150.0
        val targetTangentVelocity = currentTangentVelocity + sideImpulse
        pet.vx = gravityX * -HOP_SPEED + tangentX * targetTangentVelocity
        pet.vy = gravityY * -HOP_SPEED + tangentY * targetTangentVelocity
        pet.facing = if (pet.vx >= 0.0) 1 else -1
        pet.cheerOnLand = true
        pet.pendingOneShot = null
        setAnimation(pet, PetAnimation.JUMP, loop = true, restart = true)
    }

    private fun startOneShot(pet: PetState, animation: PetAnimation) {
        pet.pendingOneShot = null
        pet.codeTimeRemaining = if (animation == PetAnimation.CODE) 10.0 else 0.0
        setAnimation(pet, animation, loop = false, restart = true)
    }

    private fun setAnimation(
        pet: PetState,
        animation: PetAnimation,
        loop: Boolean,
        restart: Boolean = false,
    ) {
        if (pet.animation == animation && !restart) {
            pet.animationLoop = loop
            return
        }
        pet.animation = animation
        pet.animationLoop = loop
        if (animation != PetAnimation.CODE) pet.codeTimeRemaining = 0.0
        pet.frameIndex = 0
        pet.frameTime = 0.0
    }

    private fun advanceAnimation(pet: PetState, dt: Double) {
        val animation = pet.animation ?: return
        if (animation.frameCount <= 1) return
        if (animation == PetAnimation.CODE) {
            pet.codeTimeRemaining = (pet.codeTimeRemaining - dt).coerceAtLeast(0.0)
        }
        pet.frameTime += dt
        val step = 1.0 / animation.framesPerSecond.coerceAtLeast(1.0)
        while (pet.frameTime >= step) {
            pet.frameTime -= step
            pet.frameIndex += 1
            if (pet.frameIndex >= animation.frameCount) {
                if (animation == PetAnimation.CODE && pet.codeTimeRemaining > 0.0) {
                    pet.frameIndex = 0
                    continue
                }
                if (pet.animationLoop) {
                    pet.frameIndex = 0
                } else {
                    pet.frameIndex = animation.frameCount - 1
                    pet.frameTime = 0.0
                    if (animation.oneShot) pet.animation = null
                    break
                }
            }
        }
    }

    private fun oneShotPlaying(pet: PetState): Boolean =
        pet.animation?.oneShot == true && !pet.animationLoop

    private fun gravityVelocity(pet: PetState): Double =
        gravityFor(pet.species).let { (gravityX, gravityY) ->
            pet.vx * gravityX + pet.vy * gravityY
        }

    private fun tangentVelocity(pet: PetState): Double =
        gravityFor(pet.species).let { (gravityX, gravityY) ->
            pet.vx * gravityY - pet.vy * gravityX
        }

    private fun isOnGravitySurface(pet: PetState, maxX: Double, maxY: Double): Boolean {
        val (gravityX, gravityY) = gravityFor(pet.species)
        return if (abs(gravityX) > abs(gravityY)) {
            if (gravityX < 0.0) {
                pet.x <= SURFACE_EPSILON && pet.vx <= SURFACE_EPSILON
            } else {
                pet.x >= maxX - SURFACE_EPSILON && pet.vx >= -SURFACE_EPSILON
            }
        } else if (gravityY < 0.0) {
            pet.y <= SURFACE_EPSILON && pet.vy <= SURFACE_EPSILON
        } else {
            pet.y >= maxY - SURFACE_EPSILON && pet.vy >= -SURFACE_EPSILON
        }
    }

    private fun recordDragSample(pet: PetState, x: Double, y: Double, timeSeconds: Double) {
        pet.dragSamples += DragSample(timeSeconds, x, y)
        val cutoff = timeSeconds - DRAG_SAMPLE_WINDOW_SECONDS
        pet.dragSamples.removeAll { it.timeSeconds < cutoff }
    }

    internal fun velocityFromSamples(samples: List<DragSample>): Pair<Double, Double> {
        val useful = samples.toMutableList()
        while (useful.size >= 2) {
            val previous = useful[useful.lastIndex - 1]
            val last = useful.last()
            if (hypot(last.x - previous.x, last.y - previous.y) < 1.5) {
                useful.removeAt(useful.lastIndex)
            } else {
                break
            }
        }
        if (useful.size < 2) return 0.0 to 0.0
        val first = useful.first()
        val last = useful.last()
        val dt = last.timeSeconds - first.timeSeconds
        if (dt < 0.01) return 0.0 to 0.0
        return clampVelocity((last.x - first.x) / dt) to clampVelocity((last.y - first.y) / dt)
    }

    private fun applyCollisions() {
        if (mutablePets.size <= COLLISION_GRID_THRESHOLD) {
            for (index in mutablePets.indices) {
                for (otherIndex in index + 1 until mutablePets.size) {
                    val own = mutablePets[index]
                    val other = mutablePets[otherIndex]
                    if (shouldCollide(own, other)) resolveCollision(own, other)
                }
            }
            return
        }

        val cellSize = mutablePets.maxOfOrNull { max(it.width, it.height) } ?: return
        val grid = mutableMapOf<Pair<Int, Int>, MutableList<PetState>>()
        mutablePets.forEach { pet ->
            val cellX = floor((pet.x + pet.width / 2.0) / cellSize).toInt()
            val cellY = floor((pet.y + pet.height / 2.0) / cellSize).toInt()
            grid.getOrPut(cellX to cellY) { mutableListOf() } += pet
        }

        val checked = mutableSetOf<Pair<Long, Long>>()
        grid.forEach { (cell, ownGroup) ->
            for (offsetX in -1..1) {
                for (offsetY in -1..1) {
                    val otherGroup = grid[(cell.first + offsetX) to (cell.second + offsetY)] ?: continue
                    ownGroup.forEach { own ->
                        otherGroup.forEach { other ->
                            if (own.id == other.id) return@forEach
                            val pair = minOf(own.id, other.id) to maxOf(own.id, other.id)
                            if (checked.add(pair) && shouldCollide(own, other)) {
                                resolveCollision(own, other)
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun resolveCollision(own: PetState, other: PetState) {
        val ownWidth = own.width * 0.62
        val ownHeight = own.height * 0.62
        val otherWidth = other.width * 0.62
        val otherHeight = other.height * 0.62
        val dx = own.x + own.width / 2.0 - (other.x + other.width / 2.0)
        val dy = own.y + own.height / 2.0 - (other.y + other.height / 2.0)
        val overlapX = (ownWidth + otherWidth) / 2.0 - abs(dx)
        val overlapY = (ownHeight + otherHeight) / 2.0 - abs(dy)
        if (overlapX <= 0.0 || overlapY <= 0.0) return

        val ownFixed = physicsInteractionsDisabled(own)
        val otherFixed = physicsInteractionsDisabled(other)
        if (ownFixed && otherFixed) return
        val ownShare = if (ownFixed) 0.0 else if (otherFixed) 1.0 else 0.5
        val otherShare = if (otherFixed) 0.0 else if (ownFixed) 1.0 else 0.5
        val ownImpulse = if (ownFixed) 0.0 else if (otherFixed) 240.0 else 120.0
        val otherImpulse = if (otherFixed) 0.0 else if (ownFixed) 240.0 else 120.0

        if (overlapX < overlapY) {
            val direction = if (dx >= 0.0) 1.0 else -1.0
            own.x += direction * overlapX * ownShare
            other.x -= direction * overlapX * otherShare
            own.vx += direction * ownImpulse
            other.vx -= direction * otherImpulse
        } else {
            val direction = if (dy >= 0.0) 1.0 else -1.0
            own.y += direction * overlapY * ownShare
            other.y -= direction * overlapY * otherShare
            own.vy += direction * ownImpulse
            other.vy -= direction * otherImpulse
        }
        if (ownFixed) stopPhysicsMotion(own)
        if (otherFixed) stopPhysicsMotion(other)
    }

    private fun clampPosition(pet: PetState) {
        val bounds = boundsFor(pet.species)
        pet.x = pet.x.coerceIn(0.0, (bounds.width - pet.width).coerceAtLeast(0.0))
        pet.y = pet.y.coerceIn(0.0, (bounds.height - pet.height).coerceAtLeast(0.0))
    }

    private fun find(id: Long): PetState? = mutablePets.firstOrNull { it.id == id }

    private fun physicsInteractionsDisabled(pet: PetState): Boolean =
        pet.species == PetSpecies.HOOTS && appSettings.hoots.disablePhysicsInteractions

    private fun stopPhysicsMotion(pet: PetState) {
        pet.vx = 0.0
        pet.vy = 0.0
        pet.cheerOnLand = false
        pet.pendingOneShot = null
    }

    internal fun idleAnimationsFor(species: PetSpecies): List<PetAnimation> = when (species) {
        PetSpecies.SEEDY -> listOf(
            PetAnimation.WAVE,
            PetAnimation.EAT,
            PetAnimation.CODE,
            PetAnimation.CHEER,
        )
        PetSpecies.HOOTS -> listOf(
            PetAnimation.EAT,
            PetAnimation.CODE,
            PetAnimation.CHEER,
        )
    }

    fun speciesOf(id: Long): PetSpecies? = find(id)?.species

    fun settingsFor(species: PetSpecies): PetSettings = appSettings.forSpecies(species)

    fun boundsFor(species: PetSpecies): WorldBounds = speciesBounds.getValue(species)

    fun gravityFor(species: PetSpecies): Pair<Double, Double> =
        gravityDirections.getValue(species)

    private fun shouldCollide(own: PetState, other: PetState): Boolean = when {
        own.species != other.species -> appSettings.seedyHootsCollision
        own.species == PetSpecies.SEEDY -> appSettings.seedy.collision
        else -> false
    }

    private fun clampVelocity(value: Double): Double = value.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)

    companion object {
        const val MAX_PETS = 100
        const val MAX_HOOTS = 1
        const val MAX_DELTA_SECONDS = 0.033
        const val GRAVITY = 1350.0
        const val WALK_SPEED = 40.0
        const val HARD_LAND_SPEED = 420.0
        const val FALL_SPEED = 280.0
        const val DRAG_THRESHOLD = 18.0
        const val MAX_VELOCITY = 1800.0
        const val DRAG_SAMPLE_WINDOW_SECONDS = 0.12
        const val COLLISION_GRID_THRESHOLD = 24
        const val TAU = Math.PI * 2.0
        const val HOP_SPEED = 720.0
        const val REST_SPEED = 55.0
        const val SURFACE_EPSILON = 1.0
        const val MIN_GRAVITY_VECTOR_LENGTH = 0.001
        const val MAX_DETONATION_MULTIPLIER = 4.0
    }
}
