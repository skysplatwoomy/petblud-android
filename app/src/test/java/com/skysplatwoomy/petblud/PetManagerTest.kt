package com.skysplatwoomy.petblud

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetManagerTest {
    private fun manager(settings: PetSettings = PetSettings()) = PetManager(
        settings = settings,
        bounds = WorldBounds(1080.0, 1920.0),
        random = Random(7),
    )

    @Test
    fun settingsAreNormalizedToDesktopLimits() {
        val normalized = PetSettings(
            physicsMultiplier = 99.0,
            sizePercent = -50.0,
        ).normalized()

        assertEquals(5.0, normalized.physicsMultiplier, 0.0)
        assertEquals(10.0, normalized.sizePercent, 0.0)
        assertFalse(normalized.tiltGravity)
        assertFalse(normalized.shakeDetonation)
        assertFalse(normalized.keepUpright)
        assertFalse(normalized.avoidKeyboard)
        assertFalse(normalized.disablePhysicsInteractions)
        assertFalse(normalized.bubbleTapOpensChatGpt)
        assertEquals(
            PetSettings.DEFAULT_BUBBLE_BORDER_COLOR,
            normalized.bubbleLightAppearance.borderColor,
        )
        assertEquals(
            PetSettings.DEFAULT_BUBBLE_FILL_COLOR,
            normalized.bubbleLightAppearance.fillColor,
        )
    }

    @Test
    fun spawnIsCappedAtOneHundredAndKillOperationsWork() {
        val manager = manager()
        manager.spawn(100)
        manager.spawn(100)
        assertEquals(PetManager.MAX_PETS, manager.pets.size)

        val survivor = manager.pets[42].id
        manager.killAllExcept(survivor)
        assertEquals(listOf(survivor), manager.pets.map { it.id })

        manager.killAll()
        assertTrue(manager.pets.isEmpty())
    }

    @Test
    fun seedyAndSingleHootsCoexistWithGloballyUniqueIds() {
        val manager = manager()
        val seedys = manager.spawn(3, PetSpecies.SEEDY)
        val firstHoots = manager.spawn(1, PetSpecies.HOOTS).single()
        val repeatedHoots = manager.spawn(1, PetSpecies.HOOTS)

        assertEquals(3, manager.count(PetSpecies.SEEDY))
        assertEquals(1, manager.count(PetSpecies.HOOTS))
        assertTrue(repeatedHoots.isEmpty())
        assertEquals(PetSpecies.HOOTS, firstHoots.species)
        assertEquals(4, (seedys + firstHoots).map { it.id }.distinct().size)
    }

    @Test
    fun hootsAtOneHundredPercentUsesComparableSmallerBaseFootprint() {
        val manager = manager()
        val seedy = manager.spawn(1, PetSpecies.SEEDY).single()
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()

        assertEquals(PetState.BASE_WIDTH, seedy.width, 0.0)
        assertEquals(PetState.BASE_HEIGHT, seedy.height, 0.0)
        assertEquals(PetState.BASE_WIDTH * PetState.HOOTS_BASE_SCALE, hoots.width, 0.0)
        assertEquals(PetState.BASE_HEIGHT * PetState.HOOTS_BASE_SCALE, hoots.height, 0.0)
        assertTrue(hoots.width < seedy.width)
        assertTrue(hoots.height < seedy.height)
    }

    @Test
    fun hootsSnapshotCarriesLiveChatGptBubbleSetting() {
        val manager = manager()
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        assertFalse(manager.snapshot(hoots.id)?.showChatGptNotifications ?: true)

        manager.applySettings(
            manager.appSettings.copy(
                hoots = manager.appSettings.hoots.copy(showChatGptNotifications = true),
            ),
        )

        assertTrue(manager.snapshot(hoots.id)?.showChatGptNotifications == true)
    }

    @Test
    fun speciesScopedRemovalAndChaosLeaveOtherSpeciesUntouched() {
        val manager = manager()
        val seedy = manager.spawn(1, PetSpecies.SEEDY).single()
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        seedy.vx = 0.0
        seedy.vy = 0.0
        hoots.vx = 0.0
        hoots.vy = 0.0

        manager.detonateAll(species = PetSpecies.HOOTS)

        assertEquals(0.0, seedy.vx, 0.0)
        assertEquals(0.0, seedy.vy, 0.0)
        assertTrue(hypot(hoots.vx, hoots.vy) > 0.0)
        manager.killAll(PetSpecies.HOOTS)
        assertEquals(listOf(seedy.id), manager.pets.map { it.id })
    }

    @Test
    fun speciesSettingsBoundsAndGravityRemainIndependent() {
        val manager = manager()
        val seedy = manager.spawn(1, PetSpecies.SEEDY).single()
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        manager.applySettings(
            AppSettings(
                seedy = PetSettings(gravity = false, physicsMultiplier = 2.0, sizePercent = 80.0),
                hoots = PetSettings(gravity = true, physicsMultiplier = 0.5, sizePercent = 140.0),
            ),
        )
        manager.updateBounds(WorldBounds(400.0, 600.0), PetSpecies.HOOTS)
        manager.setGravityDirection(1.0, 0.0, PetSpecies.HOOTS)

        assertFalse(seedy.gravityEnabled)
        assertEquals(2.0, seedy.physicsMultiplier, 0.0)
        assertEquals(80.0, seedy.sizePercent, 0.0)
        assertTrue(hoots.gravityEnabled)
        assertEquals(0.5, hoots.physicsMultiplier, 0.0)
        assertEquals(140.0, hoots.sizePercent, 0.0)
        assertEquals(WorldBounds(1080.0, 1920.0), manager.boundsFor(PetSpecies.SEEDY))
        assertEquals(WorldBounds(400.0, 600.0), manager.boundsFor(PetSpecies.HOOTS))
        assertEquals(0.0 to 1.0, manager.gravityFor(PetSpecies.SEEDY))
        assertEquals(1.0 to 0.0, manager.gravityFor(PetSpecies.HOOTS))
    }

    @Test
    fun crossSpeciesCollisionUsesOnlyHootsPageToggle() {
        val manager = PetManager(
            settings = PetSettings(gravity = false, collision = true),
            hootsSettings = PetSettings(gravity = false),
            bounds = WorldBounds(1080.0, 1920.0),
            random = Random(7),
        )
        val seedy = manager.spawn(1, PetSpecies.SEEDY).single()
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        listOf(seedy, hoots).forEach {
            it.x = 300.0
            it.y = 300.0
            it.vx = 0.0
            it.vy = 0.0
        }

        manager.tick(0.0)
        assertEquals(300.0, seedy.x, 0.0)
        assertEquals(300.0, hoots.x, 0.0)

        manager.applySettings(manager.appSettings.copy(seedyHootsCollision = true))
        manager.tick(0.0)
        assertTrue(hypot(seedy.x - hoots.x, seedy.y - hoots.y) > 0.001)
    }

    @Test
    fun disabledHootsPhysicsAllowsDraggingButRejectsAllLaunchForces() {
        val manager = PetManager(
            settings = PetSettings(gravity = false),
            hootsSettings = PetSettings(disablePhysicsInteractions = true),
            bounds = WorldBounds(1080.0, 1920.0),
            random = Random(7),
        )
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        hoots.x = 300.0
        hoots.y = 400.0
        val originalX = hoots.x
        val originalY = hoots.y

        manager.tick(PetManager.MAX_DELTA_SECONDS)
        manager.explode(0.0, 0.0, PetSpecies.HOOTS)
        manager.detonateAll(4.0, PetSpecies.HOOTS)
        assertEquals(originalX, hoots.x, 0.0)
        assertEquals(originalY, hoots.y, 0.0)
        assertEquals(0.0, hoots.vx, 0.0)
        assertEquals(0.0, hoots.vy, 0.0)

        manager.pointerDown(hoots.id, 10.0, 10.0, 310.0, 410.0, 0.8)
        manager.pointerUp(hoots.id, 10.0, 310.0, 410.0, 0.85)
        assertEquals(0.0, hoots.vx, 0.0)
        assertEquals(0.0, hoots.vy, 0.0)

        manager.pointerDown(hoots.id, 10.0, 10.0, 310.0, 410.0, 1.0)
        manager.pointerMove(hoots.id, 610.0, 710.0, 1.05)
        manager.pointerUp(hoots.id, 10.0, 800.0, 900.0, 1.08)

        assertTrue(hoots.x > originalX)
        assertTrue(hoots.y > originalY)
        assertEquals(0.0, hoots.vx, 0.0)
        assertEquals(0.0, hoots.vy, 0.0)
        assertEquals(PetAnimation.IDLE, hoots.animation)
    }

    @Test
    fun disabledHootsPhysicsKeepsCrossCollisionAsAnImmovableObstacle() {
        val manager = PetManager(
            settings = PetSettings(gravity = false),
            hootsSettings = PetSettings(gravity = false, disablePhysicsInteractions = true),
            seedyHootsCollision = true,
            bounds = WorldBounds(1080.0, 1920.0),
            random = Random(7),
        )
        val seedy = manager.spawn(1, PetSpecies.SEEDY).single()
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        listOf(seedy, hoots).forEach {
            it.x = 300.0
            it.y = 300.0
            it.vx = 0.0
            it.vy = 0.0
        }

        manager.tick(0.0)

        assertEquals(300.0, hoots.x, 0.0)
        assertEquals(300.0, hoots.y, 0.0)
        assertEquals(0.0, hoots.vx, 0.0)
        assertEquals(0.0, hoots.vy, 0.0)
        assertTrue(hypot(seedy.x - hoots.x, seedy.y - hoots.y) > 0.001)
        assertTrue(hypot(seedy.vx, seedy.vy) > 0.0)
    }

    @Test
    fun hootsIdleActivitiesAreBookWritingAndWingMovementWithEqualSelectionSlots() {
        val manager = manager()

        assertEquals(
            listOf(PetAnimation.EAT, PetAnimation.CODE, PetAnimation.CHEER),
            manager.idleAnimationsFor(PetSpecies.HOOTS),
        )
    }

    @Test
    fun hootsReadingAndWritingUseLaptopLengthAndRequestedFrameHolds() {
        assertEquals(
            PetAnimationTiming.LAPTOP_ACTIVITY_SECONDS,
            PetAnimationTiming.activityDuration(PetSpecies.SEEDY, PetAnimation.CODE),
            0.0,
        )
        assertEquals(
            PetAnimationTiming.LAPTOP_ACTIVITY_SECONDS,
            PetAnimationTiming.activityDuration(PetSpecies.HOOTS, PetAnimation.CODE),
            0.0,
        )
        assertEquals(
            PetAnimationTiming.LAPTOP_ACTIVITY_SECONDS,
            PetAnimationTiming.activityDuration(PetSpecies.HOOTS, PetAnimation.CHEER),
            0.0,
        )
        PetAnimation.CHEER.frameCount.let { count ->
            repeat(count) { frame ->
                assertEquals(
                    0.15,
                    PetAnimationTiming.frameDuration(
                        PetSpecies.HOOTS,
                        PetAnimation.CHEER,
                        frame,
                    ),
                    0.0,
                )
            }
        }
        assertEquals(
            0.25,
            PetAnimationTiming.frameDuration(
                PetSpecies.HOOTS,
                PetAnimation.CODE,
                PetAnimationTiming.HOOTS_COFFEE_FRAME_INDEX,
            ),
            0.0,
        )
        assertEquals(
            1.0 / PetAnimation.CODE.framesPerSecond,
            PetAnimationTiming.frameDuration(PetSpecies.HOOTS, PetAnimation.CODE, 1),
            0.0,
        )
    }

    @Test
    fun hootsReadingRunsForTenSecondsAndCoffeeFrameHoldsQuarterSecond() {
        val manager = PetManager(
            hootsSettings = PetSettings(disablePhysicsInteractions = true),
            bounds = WorldBounds(1080.0, 1920.0),
            random = Random(7),
        )
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        hoots.animation = PetAnimation.CODE
        hoots.animationLoop = false
        hoots.frameIndex = PetAnimationTiming.HOOTS_COFFEE_FRAME_INDEX
        hoots.frameTime = 0.0
        hoots.idleActivityTimeRemaining = PetAnimationTiming.LAPTOP_ACTIVITY_SECONDS

        repeat(9) { manager.tick(0.025) }
        assertEquals(PetAnimationTiming.HOOTS_COFFEE_FRAME_INDEX, hoots.frameIndex)
        manager.tick(0.025)
        assertEquals(PetAnimationTiming.HOOTS_COFFEE_FRAME_INDEX + 1, hoots.frameIndex)

        hoots.animation = PetAnimation.CHEER
        hoots.animationLoop = false
        hoots.frameIndex = 0
        hoots.frameTime = 0.0
        hoots.idleActivityTimeRemaining = PetAnimationTiming.LAPTOP_ACTIVITY_SECONDS
        repeat(5) { manager.tick(0.025) }
        assertEquals(0, hoots.frameIndex)
        manager.tick(0.025)
        assertEquals(1, hoots.frameIndex)
        repeat(394) { manager.tick(0.025) }
        assertEquals(null, hoots.animation)
        assertEquals(0.0, hoots.idleActivityTimeRemaining, 0.0)
    }

    @Test
    fun disabledHootsPhysicsCanPlayIdleActivitiesWhileFloating() {
        val manager = PetManager(
            hootsSettings = PetSettings(disablePhysicsInteractions = true),
            bounds = WorldBounds(1080.0, 1920.0),
            random = Random(7),
        )
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()
        hoots.x = 400.0
        hoots.y = 500.0
        hoots.specialTimer = 0.0

        manager.tick(PetManager.MAX_DELTA_SECONDS)

        assertTrue(hoots.animation in manager.idleAnimationsFor(PetSpecies.HOOTS))
        assertFalse(hoots.animationLoop)
        assertEquals(400.0, hoots.x, 0.0)
        assertEquals(500.0, hoots.y, 0.0)
    }

    @Test
    fun tickClampsLargeDeltaAndAppliesGravity() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.vx = 0.0
        pet.vy = 0.0
        val originalY = pet.y

        manager.tick(10.0)

        assertEquals(PetManager.GRAVITY * PetManager.MAX_DELTA_SECONDS, pet.vy, 0.001)
        assertTrue(pet.y > originalY)
    }

    @Test
    fun directedGravityIsNormalizedAndAcceleratesOnBothAxes() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.x = 500.0
        pet.y = 500.0
        pet.vx = 0.0
        pet.vy = 0.0
        manager.setGravityDirection(3.0, 4.0)

        manager.tick(PetManager.MAX_DELTA_SECONDS)

        assertEquals(0.6, manager.gravityX, 0.0001)
        assertEquals(0.8, manager.gravityY, 0.0001)
        assertEquals(PetManager.GRAVITY * 0.6 * PetManager.MAX_DELTA_SECONDS, pet.vx, 0.001)
        assertEquals(PetManager.GRAVITY * 0.8 * PetManager.MAX_DELTA_SECONDS, pet.vy, 0.001)
    }

    @Test
    fun tapHopsOppositeSidewaysGravity() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.vx = 0.0
        pet.vy = 0.0
        manager.setGravityDirection(1.0, 0.0)

        manager.pointerDown(pet.id, 5.0, 5.0, pet.x + 5.0, pet.y + 5.0, 1.0)
        manager.pointerUp(pet.id, 5.0, pet.x + 5.0, pet.y + 5.0, 1.05)

        assertEquals(-PetManager.HOP_SPEED, pet.vx, 0.001)
        assertEquals(-150.0, pet.vy, 0.001)
    }

    @Test
    fun resettingGravityRestoresScreenDown() {
        val manager = manager()
        manager.setGravityDirection(-1.0, 0.0)

        manager.resetGravityDirection()

        assertEquals(0.0, manager.gravityX, 0.0)
        assertEquals(1.0, manager.gravityY, 0.0)
    }

    @Test
    fun sidewaysGravitySettlesAgainstSideWall() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.x = 0.0
        pet.y = 500.0
        pet.vx = 0.0
        pet.vy = 0.0
        manager.setGravityDirection(-1.0, 0.0)

        manager.tick(PetManager.MAX_DELTA_SECONDS)

        assertEquals(0.0, pet.x, 0.0)
        assertEquals(0.0, pet.vx, 0.0)
        assertEquals(PetAnimation.IDLE, pet.animation)
    }

    @Test
    fun gravitySensorVectorIsMappedAcrossDisplayRotations() {
        assertEquals(
            -2.0 to 3.0,
            MotionGravityMapper.toScreen(2.0, 3.0, MotionGravityMapper.ROTATION_0),
        )
        assertEquals(
            3.0 to 2.0,
            MotionGravityMapper.toScreen(2.0, 3.0, MotionGravityMapper.ROTATION_90),
        )
        assertEquals(
            2.0 to -3.0,
            MotionGravityMapper.toScreen(2.0, 3.0, MotionGravityMapper.ROTATION_180),
        )
        assertEquals(
            -3.0 to -2.0,
            MotionGravityMapper.toScreen(2.0, 3.0, MotionGravityMapper.ROTATION_270),
        )
    }

    @Test
    fun spriteRotationKeepsFeetAlignedWithGravity() {
        assertEquals(0.0f, MotionGravityMapper.spriteRotationDegrees(0.0, 1.0), 0.001f)
        assertEquals(-90.0f, MotionGravityMapper.spriteRotationDegrees(1.0, 0.0), 0.001f)
        assertEquals(90.0f, MotionGravityMapper.spriteRotationDegrees(-1.0, 0.0), 0.001f)
        assertEquals(-180.0f, MotionGravityMapper.spriteRotationDegrees(0.0, -1.0), 0.001f)
    }

    @Test
    fun shakeDetectorScalesStrengthAndEnforcesCooldown() {
        val weaker = ShakeDetector()
        assertEquals(null, weaker.addAccelerometerSample(0.0, 0.0, 9.81, 0L))
        val weakerStrength = weaker.addAccelerometerSample(16.0, 0.0, 9.81, 1_000_000_000L)
            ?: error("Expected the weaker shake to trigger")

        val stronger = ShakeDetector()
        stronger.addAccelerometerSample(0.0, 0.0, 9.81, 0L)
        val strongerStrength = stronger.addAccelerometerSample(50.0, 0.0, 9.81, 1_000_000_000L)
            ?: error("Expected the stronger shake to trigger")
        assertTrue(strongerStrength > weakerStrength)
        assertEquals(ShakeDetector.MAX_STRENGTH, strongerStrength, 0.0)

        assertEquals(
            null,
            stronger.addAccelerometerSample(-50.0, 0.0, 9.81, 1_100_000_000L),
        )
        assertTrue(
            stronger.addAccelerometerSample(50.0, 0.0, 9.81, 2_000_000_000L) != null,
        )
    }

    @Test
    fun detonationMultiplierScalesLaunchSpeed() {
        val normal = manager()
        val amplified = manager()
        val normalPet = normal.spawn().single()
        val amplifiedPet = amplified.spawn().single()
        normalPet.vx = 0.0
        normalPet.vy = 0.0
        amplifiedPet.vx = 0.0
        amplifiedPet.vy = 0.0

        normal.detonateAll(1.0)
        amplified.detonateAll(3.0)

        val normalSpeed = hypot(normalPet.vx, normalPet.vy)
        val amplifiedSpeed = hypot(amplifiedPet.vx, amplifiedPet.vy)
        assertEquals(normalSpeed * 3.0, amplifiedSpeed, 0.001)
    }

    @Test
    fun floorBouncePreservesDesktopCoefficients() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.x = 500.0
        pet.y = manager.bounds.height - pet.height + 10.0
        pet.vx = 100.0
        pet.vy = 500.0

        manager.tick(0.0)

        assertEquals(manager.bounds.height - pet.height, pet.y, 0.001)
        assertEquals(-290.0, pet.vy, 0.001)
        assertEquals(90.0, pet.vx, 0.001)
        assertEquals(PetAnimation.DIZZY, pet.pendingOneShot)
    }

    @Test
    fun tapProducesHopAndDirectionalImpulse() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.vx = 0.0

        manager.pointerDown(pet.id, 5.0, 5.0, pet.x + 5.0, pet.y + 5.0, 1.0)
        manager.pointerUp(pet.id, 5.0, pet.x + 5.0, pet.y + 5.0, 1.05)

        assertEquals(-720.0, pet.vy, 0.0)
        assertEquals(150.0, pet.vx, 0.0)
        assertEquals(PetAnimation.JUMP, pet.animation)
        assertTrue(pet.cheerOnLand)
    }

    @Test
    fun oneShotAnimationFinishesBeforeLocomotionResumes() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.x = 300.0
        pet.y = manager.bounds.height - pet.height
        pet.vx = 0.0
        pet.vy = 0.0
        pet.pendingOneShot = PetAnimation.WAVE

        manager.tick(0.0)
        assertEquals(PetAnimation.WAVE, pet.animation)
        assertFalse(pet.animationLoop)

        repeat(20) { manager.tick(PetManager.MAX_DELTA_SECONDS) }
        assertTrue(pet.animation == PetAnimation.IDLE || pet.animation == null)
    }

    @Test
    fun dragReleaseRetainsMomentumAndClampsVelocity() {
        val manager = manager()
        val pet = manager.spawn().single()

        manager.pointerDown(pet.id, 10.0, 10.0, 100.0, 100.0, 1.0)
        manager.pointerMove(pet.id, 140.0, 100.0, 1.02)
        manager.pointerMove(pet.id, 180.0, 100.0, 1.04)
        manager.pointerUp(pet.id, 10.0, 220.0, 100.0, 1.06)

        assertEquals(PetManager.MAX_VELOCITY, pet.vx, 0.001)
        assertEquals(0.0, pet.vy, 0.001)
        assertEquals(PetAnimation.DIZZY, pet.pendingOneShot)
        assertFalse(pet.dragging)
    }

    @Test
    fun draggingUsesAnimatedSpritesForEveryScreenDirectionAndBothSpecies() {
        val manager = manager()
        assertEquals(PetAnimation.WALK_RIGHT, manager.dragAnimationFor(500.0, 20.0))
        assertEquals(PetAnimation.WALK_LEFT, manager.dragAnimationFor(-500.0, 20.0))
        assertEquals(PetAnimation.JUMP, manager.dragAnimationFor(20.0, -500.0))
        assertEquals(PetAnimation.FALL, manager.dragAnimationFor(20.0, 500.0))

        PetSpecies.entries.forEach { species ->
            val pet = manager.spawn(1, species).single()
            manager.pointerDown(pet.id, 10.0, 10.0, 100.0, 100.0, 1.0)
            manager.pointerMove(pet.id, 180.0, 100.0, 1.02)
            manager.pointerMove(pet.id, 260.0, 100.0, 1.08)
            repeat(4) { manager.tick(PetManager.MAX_DELTA_SECONDS) }

            assertEquals(PetAnimation.WALK_RIGHT, pet.animation)
            assertTrue(pet.frameIndex > 0)
            manager.cancelPointer(pet.id)
        }
    }

    @Test
    fun physicsLockedHootsStillUsesDragDirectionAnimation() {
        val manager = PetManager(
            hootsSettings = PetSettings(disablePhysicsInteractions = true),
            bounds = WorldBounds(1080.0, 1920.0),
            random = Random(7),
        )
        val hoots = manager.spawn(1, PetSpecies.HOOTS).single()

        manager.pointerDown(hoots.id, 10.0, 10.0, 300.0, 300.0, 1.0)
        manager.pointerMove(hoots.id, 300.0, 220.0, 1.02)
        manager.pointerMove(hoots.id, 300.0, 140.0, 1.08)
        repeat(4) { manager.tick(PetManager.MAX_DELTA_SECONDS) }

        assertEquals(PetAnimation.JUMP, hoots.animation)
        assertEquals(0.0, hoots.vx, 0.0)
        assertEquals(0.0, hoots.vy, 0.0)
        assertTrue(hoots.frameIndex > 0)
    }

    @Test
    fun collisionSeparatesOverlappingPets() {
        val manager = manager(PetSettings(collision = true))
        val first = manager.spawn().single()
        val second = manager.spawn().single()
        first.x = 300.0
        first.y = 300.0
        second.x = 300.0
        second.y = 300.0
        first.vx = 0.0
        first.vy = 0.0
        second.vx = 0.0
        second.vy = 0.0

        manager.tick(0.0)

        assertTrue(hypot(first.x - second.x, first.y - second.y) > 0.001)
        assertTrue(hypot(first.vx - second.vx, first.vy - second.vy) >= 240.0)
    }

    @Test
    fun explosionPushesPetsAwayFromOrigin() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.x = 500.0
        pet.y = 500.0
        pet.vx = 0.0
        pet.vy = 0.0

        manager.explode(0.0, 0.0)

        assertTrue(pet.vx > 0.0)
        assertTrue(pet.vy > 0.0)
        assertEquals(PetAnimation.DIZZY, pet.pendingOneShot)
        assertEquals(PetAnimation.JUMP, pet.animation)
    }

    @Test
    fun applyingGlobalSettingsUpdatesExistingPetsAndClampsPosition() {
        val manager = manager()
        val pet = manager.spawn().single()
        pet.x = 1000.0
        pet.y = 1800.0

        manager.applySettings(
            PetSettings(
                gravity = false,
                physicsMultiplier = 2.5,
                sizePercent = 500.0,
                collision = true,
            ),
        )

        assertFalse(pet.gravityEnabled)
        assertEquals(2.5, pet.physicsMultiplier, 0.0)
        assertEquals(500.0, pet.sizePercent, 0.0)
        assertTrue(pet.x <= manager.bounds.width - pet.width)
        assertTrue(pet.y <= manager.bounds.height - pet.height)
    }
}
