package com.spiderhub.webverse.game

enum class Dimension(val label: String, val accentHex: Long) {
    NEON_NOIR("NEON-NOIR // NEXUS-9", 0xFF00F0FF),
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
    val isSpire: Boolean,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float
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
    var attackCooldown: Float = 0f
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
    val dimension: Dimension = Dimension.NEON_NOIR,
    val isSwinging: Boolean = false,
    val isWallRunning: Boolean = false,
    val portalActive: Boolean = false,
    val storyTitle: String = "ACT I: NEXUS AWAKENING",
    val storyObjective: String = "Swing across NEXUS-9 and collect 4 Chrono-Shards.",
    val muted: Boolean = false,
    val assetsReady: Boolean = false,
    val assetStats: List<LoadedAssetInfo> = emptyList(),
    val licenseText: String = ""
)

object AssetCatalog {
    val models: List<BundledModelSpec> = listOf(
        BundledModelSpec("apocalyptic_city", "apocalyptic_city.glb", "Ruined Skyline Sectors (GLB)", "GLB"),
        BundledModelSpec("kage", "kage.obj", "Kage Hero Suit Avatar", "OBJ"),
        BundledModelSpec("husk", "husk.obj", "Corrupted Husk Hostile NPC", "OBJ"),
        BundledModelSpec("citizen", "citizen.obj", "Nexus Citizen NPC (Variant A)", "OBJ"),
        BundledModelSpec("citizen_b", "citizen_b.obj", "Nexus Citizen NPC (Variant B)", "OBJ"),
        BundledModelSpec("spire", "spire.obj", "Cyberpunk Anchor Spire", "OBJ"),
        BundledModelSpec("beacon", "beacon.obj", "Rooftop Signal Beacon", "OBJ"),
        BundledModelSpec("portal", "portal.obj", "Multiverse Rift Portal", "OBJ"),
        BundledModelSpec("shard", "shard.obj", "Chrono-Energy Shard Pickup", "OBJ")
    )

    val storyBeats: List<StoryBeat> = listOf(
        StoryBeat(0, "ACT I: NEXUS AWAKENING", "Swing across NEXUS-9 and collect 4 Chrono-Shards."),
        StoryBeat(1, "ACT II: HUSK SURGE", "Neutralize 4 corrupted Husks patrolling the avenues."),
        StoryBeat(2, "ACT III: RIFT CONVERGENCE", "Shift into INKVERSE and reach the active Nexus Portal.")
    )
}
