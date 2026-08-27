package com.skysplatwoomy.petblud

enum class DashboardAction {
    GRANT_PERMISSION,
    START,
    STOP,
}

enum class MotionRow {
    SHAKE_TO_BLAST,
    KEEP_UPRIGHT,
}

fun visibleMotionRows(tiltGravityEnabled: Boolean): Set<MotionRow> =
    if (tiltGravityEnabled) MotionRow.entries.toSet() else emptySet()

data class DashboardContent(
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val action: DashboardAction,
    val active: Boolean,
)

fun dashboardContent(
    overlayAllowed: Boolean,
    serviceRunning: Boolean,
    petCount: Int,
    species: PetSpecies = PetSpecies.SEEDY,
): DashboardContent = when {
    !overlayAllowed -> DashboardContent(
        title = "${species.displayName} needs permission",
        subtitle = "Allow PetBlud to appear over other apps.",
        actionLabel = "Grant overlay access",
        action = DashboardAction.GRANT_PERMISSION,
        active = false,
    )

    !serviceRunning || petCount == 0 -> DashboardContent(
        title = "${species.displayName} is napping",
        subtitle = "Ready to roam whenever you are.",
        actionLabel = "Wake ${species.displayName}",
        action = DashboardAction.START,
        active = false,
    )

    petCount == 1 -> DashboardContent(
        title = "${species.displayName} is roaming",
        subtitle = "One tiny creature is loose on your screen.",
        actionLabel = "Send ${species.displayName} home",
        action = DashboardAction.STOP,
        active = true,
    )

    else -> DashboardContent(
        title = "$petCount ${species.pluralName} are roaming",
        subtitle = "Your screen is now a small, lively ecosystem.",
        actionLabel = "Send ${species.pluralName} home",
        action = DashboardAction.STOP,
        active = true,
    )
}

val PetSpecies.displayName: String
    get() = when (this) {
        PetSpecies.SEEDY -> "Seedy"
        PetSpecies.HOOTS -> "Hoots"
    }

val PetSpecies.pluralName: String
    get() = when (this) {
        PetSpecies.SEEDY -> "Seedys"
        PetSpecies.HOOTS -> "Hoots"
    }
