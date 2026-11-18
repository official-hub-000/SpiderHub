package com.spiderhub.webverse.game

enum class Dimension(val label: String, val accentHex: Long) {
    SUNSET_NEXUS("METROPOLIS // GOLDEN DUSK", 0xFFFF6D3B),
    NEON_NOIR("NEON-NOIR // NIGHT", 0xFF00F0FF),
    INKVERSE("INKVERSE // RIFT-02", 0xFFFF2E63)
}

data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun copyFrom(other: Vec3) {
        x = other.x
        y = other.y
        z = other.z
    }
}

data class CityBuilding(
    val id: Int,
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val hasSetback: Boolean,
    val isSpire: Boolean,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
    val neonR: Float,
    val neonG: Float,
    val neonB: Float,
    val billboardTexIdx: Int = -1
)

data class StreetLamp(
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val isCyan: Boolean
)

data class HoverVehicle(
    val id: Int,
    var x: Float,
    var y: Float,
    var z: Float,
    val speed: Float,
    val axisX: Boolean,
    val dirSign: Float,
    val laneCoord: Float,
    val accentR: Float,
    val accentG: Float,
    val accentB: Float
)

data class ParticleFx(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var life: Float = 0f,
    var maxLife: Float = 1f,
    var size: Float = 0.25f,
    var r: Float = 0f,
    var g: Float = 0.9f,
    var b: Float = 1f
)

enum class NpcKind {
    HUSK,
    CITIZEN,
    WARDEN
}

data class NpcEntity(
    val id: Int,
    val kind: NpcKind,
    val variantB: Boolean = false,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var yaw: Float = 0f,
    var hp: Int = 100,
    var maxHp: Int = 100,
    var alive: Boolean = true,
    var respawnTimer: Float = 0f,
    var attackCooldown: Float = 0f,
    var hitFlash: Float = 0f,
    var walkPhase: Float = 0f
)

data class SwingAnchor(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

data class EnergyShard(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    var collected: Boolean = false,
    var bobPhase: Float = 0f
)

data class PortalGate(
    val x: Float,
    val y: Float,
    val z: Float,
    var active: Boolean = false
)

data class BundledModelSpec(
    val id: String,
    val fileName: String,
    val role: String,
    val format: String
)

data class LoadedAssetInfo(
    val spec: BundledModelSpec,
    val loaded: Boolean,
    val vertexCount: Int,
    val triangleCount: Int,
    val byteSize: Long
)

data class StoryBeat(
    val step: Int,
    val title: String,
    val objective: String
)

data class GameHudState(
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stamina: Float = 100f,
    val speedMps: Float = 0f,
    val altitudeM: Float = 0f,
    val score: Int = 0,
    val combo: Int = 0,
    val shardsCollected: Int = 0,
    val totalShards: Int = 12,
    val husksDefeated: Int = 0,
    val dimension: Dimension = Dimension.SUNSET_NEXUS,
    val isSwinging: Boolean = false,
    val isWallRunning: Boolean = false,
    val hasGrappleTarget: Boolean = false,
    val portalActive: Boolean = false,
    val portalDistanceM: Int = 132,
    val storyTitle: String = "ACT I: SKYLINE AWAKENING",
    val storyObjective: String = "Hold SWING to grapple skyscrapers and collect 4 Chrono-Shards.",
    val muted: Boolean = false,
    val ultraBloomEnabled: Boolean = true,
    val fps: Int = 60,
    val assetsReady: Boolean = false,
    val assetStats: List<LoadedAssetInfo> = emptyList(),
    val licenseText: String = ""
)

object AssetCatalog {
    val models: List<BundledModelSpec> = listOf(
        BundledModelSpec("kage", "kage.obj", "Spider-Hero Articulated Rig + PBR Suit", "PBR+OBJ"),
        BundledModelSpec("husk", "husk.obj", "Corrupted Exo-Husk Combat Rig", "PBR+OBJ"),
        BundledModelSpec("citizen", "citizen.obj", "Metropolis Citizen Rig (Variant A)", "PBR+OBJ"),
        BundledModelSpec("citizen_b", "citizen_b.obj", "Metropolis Citizen Rig (Variant B)", "PBR+OBJ"),
        BundledModelSpec("spire", "spire.obj", "Skyscraper Crown Spire", "PBR+OBJ"),
        BundledModelSpec("beacon", "beacon.obj", "Rooftop Quantum Relay Beacon", "PBR+OBJ"),
        BundledModelSpec("portal", "portal.obj", "Multiverse Stargate Rift Portal", "PBR+OBJ"),
        BundledModelSpec("shard", "shard.obj", "Chrono-Energy Gyro Crystal", "PBR+OBJ")
    )

    val storyBeats: List<StoryBeat> = listOf(
        StoryBeat(0, "ACT I: SKYLINE AWAKENING", "Hold SWING to grapple skyscrapers and collect 4 Chrono-Shards."),
        StoryBeat(1, "ACT II: HUSK SURGE", "Neutralize 4 corrupted Husks patrolling the streets."),
        StoryBeat(2, "ACT III: RIFT CONVERGENCE", "Reach the active Multiverse Portal to shift dimensions.")
    )
}
