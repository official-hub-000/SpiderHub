package com.spiderhub.webverse.engine

import android.content.Context
import com.spiderhub.webverse.audio.SoundSynth
import com.spiderhub.webverse.game.AssetCatalog
import com.spiderhub.webverse.game.CityBuilding
import com.spiderhub.webverse.game.Dimension
import com.spiderhub.webverse.game.EnergyShard
import com.spiderhub.webverse.game.GameHudState
import com.spiderhub.webverse.game.LoadedAssetInfo
import com.spiderhub.webverse.game.NpcEntity
import com.spiderhub.webverse.game.NpcKind
import com.spiderhub.webverse.game.PortalGate
import com.spiderhub.webverse.game.SwingAnchor
import com.spiderhub.webverse.game.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WebverseEngine(
    context: Context,
    private val soundSynth: SoundSynth
) {
    private val prefs = context.getSharedPreferences("webverse_save_v1", Context.MODE_PRIVATE)

    val buildings = ArrayList<CityBuilding>()
    val anchors = ArrayList<SwingAnchor>()
    val beacons = ArrayList<Vec3>()
    val shards = ArrayList<EnergyShard>()
    val npcs = ArrayList<NpcEntity>()
    var portal = PortalGate(0f, 0f, -110f, active = false)

    // Player physics state
    val playerPos = Vec3(0f, 3f, 22f)
    val playerVel = Vec3(0f, 0f, 0f)
    var playerYaw = 0f // radians, 0 = facing -Z
    var cameraYaw = 0f
    var cameraPitch = 0.24f
    var isGrounded = false
    var isWallRunning = false
    var isSwinging = false
    val swingTarget = Vec3()
    var swingLength = 18f

    var health = 100
    var maxHealth = 100
    var stamina = 100f
    var score = prefs.getInt("best_score", 0)
    var combo = 0
    var comboTimer = 0f
    var shardsCollected = 0
    var husksDefeated = 0
    var dimension = Dimension.NEON_NOIR
    var elapsedTime = 0f
    var strikePulse = 0f

    // Input state written from UI thread
    @Volatile var moveStickX = 0f
    @Volatile var moveStickY = 0f
    @Volatile var swingHeld = false

    @Volatile var assetStats: List<LoadedAssetInfo> = emptyList()
    @Volatile var assetsReady: Boolean = false
    @Volatile var licenseText: String = ""

    private var hudListener: ((GameHudState) -> Unit)? = null
    private var hudPublishAccum = 0f

    init {
        buildCityWorld()
        spawnEntities()
    }

    fun setHudListener(listener: (GameHudState) -> Unit) {
        hudListener = listener
        publishHud()
    }

    private fun buildCityWorld() {
        val rng = Random(2147)
        var bId = 0
        var aId = 0

        val blockStep = 36f
        for (gx in -3..3) {
            for (gz in -4..3) {
                // Leave central avenue open for spawning & portal sightline
                if (gx == 0 && (gz == 0 || gz == 1 || gz == -3)) continue

                val cx = gx * blockStep + (rng.nextFloat() - 0.5f) * 4f
                val cz = gz * blockStep + (rng.nextFloat() - 0.5f) * 4f
                val w = 13f + rng.nextFloat() * 7f
                val d = 13f + rng.nextFloat() * 7f
                val h = 24f + rng.nextFloat() * 52f
                val isSpire = (abs(gx) + abs(gz)) % 3 == 0

                val tintChoice = rng.nextInt(3)
                val (tr, tg, tb) = when (tintChoice) {
                    0 -> Triple(0.10f, 0.16f, 0.28f)
                    1 -> Triple(0.14f, 0.12f, 0.24f)
                    else -> Triple(0.08f, 0.20f, 0.26f)
                }

                buildings.add(CityBuilding(bId++, cx, cz, w, d, h, isSpire, tr, tg, tb))

                // Swing anchors above building corners
                anchors.add(SwingAnchor(aId++, cx, h + 10f, cz))
                if (h > 36f) {
                    beacons.add(Vec3(cx, h, cz))
                }
            }
        }
    }

    private fun spawnEntities() {
        val rng = Random(9001)
        // 12 Chrono-Shards along high swing lanes
        for (i in 0 until 12) {
            val angle = i * (PI.toFloat() * 2f / 12f)
            val radius = 32f + (i % 3) * 28f
            val sx = cos(angle) * radius
            val sz = sin(angle) * radius - 16f
            val sy = 14f + (i % 4) * 9f
            shards.add(EnergyShard(i, sx, sy, sz, collected = false, bobPhase = i * 0.7f))
        }

        // Hostile Corrupted Husks patrolling streets & rooftops
        var npcId = 0
        for (i in 0 until 8) {
            val angle = i * (PI.toFloat() * 2f / 8f)
            val dist = 28f + rng.nextFloat() * 48f
            npcs.add(
                NpcEntity(
                    id = npcId++,
                    kind = NpcKind.HUSK,
                    x = cos(angle) * dist,
                    y = 0f,
                    z = sin(angle) * dist - 18f,
                    hp = 100,
                    maxHp = 100
                )
            )
        }

        // Nexus Citizens along safe avenues
        for (i in 0 until 10) {
            val side = if (i % 2 == 0) -6f else 6f
            npcs.add(
                NpcEntity(
                    id = npcId++,
                    kind = NpcKind.CITIZEN,
                    variantB = i % 2 == 1,
                    x = side + (rng.nextFloat() - 0.5f) * 4f,
                    y = 0f,
                    z = 26f - i * 12f,
                    yaw = if (i % 2 == 0) 0f else PI.toFloat()
                )
            )
        }

        // 2 Wardens hovering near the Nexus Portal
        npcs.add(NpcEntity(id = npcId++, kind = NpcKind.WARDEN, x = -14f, y = 18f, z = -102f))
        npcs.add(NpcEntity(id = npcId++, kind = NpcKind.WARDEN, x = 14f, y = 20f, z = -102f))
    }

    fun onLookDrag(dx: Float, dy: Float) {
        cameraYaw -= dx * 0.0055f
        cameraPitch = (cameraPitch + dy * 0.0045f).coerceIn(-0.45f, 1.15f)
    }

    fun onSwingButtonChanged(pressed: Boolean) {
        swingHeld = pressed
        if (pressed && !isSwinging) {
            attemptStartSwing()
        } else if (!pressed && isSwinging) {
            releaseSwing()
        }
    }

    private fun attemptStartSwing() {
        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        var bestAnchor: SwingAnchor? = null
        var bestScore = -Float.MAX_VALUE

        for (anchor in anchors) {
            val dx = anchor.x - playerPos.x
            val dy = anchor.y - playerPos.y
            val dz = anchor.z - playerPos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 6f || dist > 64f || dy < 2f) continue

            val dot = (dx * fwdX + dz * fwdZ) / dist
            val candidateScore = dot * 2.2f - dist * 0.025f + dy * 0.04f
            if (candidateScore > bestScore) {
                bestScore = candidateScore
                bestAnchor = anchor
            }
        }

        if (bestAnchor != null) {
            swingTarget.x = bestAnchor.x
            swingTarget.y = bestAnchor.y
            swingTarget.z = bestAnchor.z
            val dx = swingTarget.x - playerPos.x
            val dy = swingTarget.y - playerPos.y
            val dz = swingTarget.z - playerPos.z
            swingLength = sqrt(dx * dx + dy * dy + dz * dz).coerceIn(10f, 52f)
            isSwinging = true
            isGrounded = false
            // Initial upward arc impulse
            playerVel.y = max(playerVel.y + 6.5f, 4.5f)
            playerVel.x += fwdX * 6f
            playerVel.z += fwdZ * 6f
            soundSynth.playWebThwip()
        }
    }

    private fun releaseSwing() {
        if (!isSwinging) return
        isSwinging = false
        // Vault release boost
        playerVel.y += 6.5f
        playerVel.x *= 1.12f
        playerVel.z *= 1.12f
    }

    fun triggerJumpOrVault() {
        if (isSwinging) {
            releaseSwing()
            playerVel.y += 5.5f
            soundSynth.playWebThwip()
            return
        }
        if (isGrounded || isWallRunning) {
            playerVel.y = if (isWallRunning) 17.5f else 14.5f
            isGrounded = false
            isWallRunning = false
            soundSynth.playWebThwip()
        }
    }

    fun triggerWebZip() {
        if (stamina < 18f) return
        stamina = (stamina - 20f).coerceAtLeast(0f)
        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        playerVel.x = fwdX * 38f
        playerVel.y = 8.5f
        playerVel.z = fwdZ * 38f
        isSwinging = false
        isGrounded = false
        soundSynth.playZipDash()
    }

    fun triggerCombatStrike() {
        strikePulse = 1.0f
        soundSynth.playStrike()
        var hits = 0
        for (npc in npcs) {
            if (!npc.alive || npc.kind != NpcKind.HUSK) continue
            val dx = npc.x - playerPos.x
            val dy = npc.y - playerPos.y
            val dz = npc.z - playerPos.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq <= 11f * 11f) {
                npc.hp -= 55
                val dist = sqrt(distSq).coerceAtLeast(0.5f)
                npc.vx += (dx / dist) * 16f
                npc.vy = 7f
                npc.vz += (dz / dist) * 16f
                hits++
                if (npc.hp <= 0) {
                    npc.alive = false
                    npc.respawnTimer = 14f
                    husksDefeated++
                    combo++
                    comboTimer = 5f
                    score += 250 * combo.coerceAtLeast(1)
                    saveProgress()
                }
            }
        }
        if (hits > 0) {
            comboTimer = 5f
        }
    }

    fun toggleDimension() {
        dimension = if (dimension == Dimension.NEON_NOIR) Dimension.INKVERSE else Dimension.NEON_NOIR
        soundSynth.playDimensionShift()
        publishHud()
    }

    fun toggleMute(): Boolean {
        soundSynth.muted = !soundSynth.muted
        publishHud()
        return soundSynth.muted
    }

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.04f)
        elapsedTime += dt
        if (strikePulse > 0f) strikePulse = (strikePulse - dt * 3.5f).coerceAtLeast(0f)
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }
        stamina = (stamina + dt * 16f).coerceAtMost(100f)

        stepPlayerPhysics(dt)
        stepNpcs(dt)
        stepPickupsAndStory(dt)

        hudPublishAccum += dt
        if (hudPublishAccum >= 0.08f) {
            hudPublishAccum = 0f
            publishHud()
        }
    }

    private fun stepPlayerPhysics(dt: Float) {
        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        val rightX = cos(cameraYaw)
        val rightZ = -sin(cameraYaw)

        val wishX = rightX * moveStickX + fwdX * (-moveStickY)
        val wishZ = rightZ * moveStickX + fwdZ * (-moveStickY)
        val wishMag = sqrt(wishX * wishX + wishZ * wishZ)

        if (wishMag > 0.08f) {
            val accel = if (isGrounded) 56f else if (isSwinging) 26f else 22f
            playerVel.x += wishX * accel * dt
            playerVel.z += wishZ * accel * dt
            playerYaw = atan2(-wishX, -wishZ)
        } else if (isGrounded) {
            playerVel.x *= (1f - 9f * dt).coerceAtLeast(0f)
            playerVel.z *= (1f - 9f * dt).coerceAtLeast(0f)
        }

        // Gravity (lighter in Inkverse)
        val gravity = if (dimension == Dimension.INKVERSE) -22f else -27f
        playerVel.y += gravity * dt

        // Web-swing pendulum constraint
        if (isSwinging) {
            if (!swingHeld) {
                releaseSwing()
            } else {
                val toPlayerX = playerPos.x - swingTarget.x
                val toPlayerY = playerPos.y - swingTarget.y
                val toPlayerZ = playerPos.z - swingTarget.z
                val dist = sqrt(toPlayerX * toPlayerX + toPlayerY * toPlayerY + toPlayerZ * toPlayerZ)
                    .coerceAtLeast(0.01f)

                if (dist > swingLength) {
                    val nx = toPlayerX / dist
                    val ny = toPlayerY / dist
                    val nz = toPlayerZ / dist

                    // Snap back onto sphere radius
                    playerPos.x = swingTarget.x + nx * swingLength
                    playerPos.y = swingTarget.y + ny * swingLength
                    playerPos.z = swingTarget.z + nz * swingLength

                    // Remove outward radial velocity
                    val radialVel = playerVel.x * nx + playerVel.y * ny + playerVel.z * nz
                    if (radialVel > 0f) {
                        playerVel.x -= radialVel * nx
                        playerVel.y -= radialVel * ny
                        playerVel.z -= radialVel * nz
                    }

                    // Gentle forward swing momentum pump
                    playerVel.x += fwdX * 14f * dt
                    playerVel.z += fwdZ * 14f * dt
                }
                // Shorten rope gradually while holding above ground
                if (playerPos.y < 8f && swingLength > 12f) {
                    swingLength -= 5f * dt
                }
            }
        }

        // Air drag & speed cap
        val maxHorizSpeed = 48f
        val horizSpeed = sqrt(playerVel.x * playerVel.x + playerVel.z * playerVel.z)
        if (horizSpeed > maxHorizSpeed) {
            val s = maxHorizSpeed / horizSpeed
            playerVel.x *= s
            playerVel.z *= s
        }

        playerPos.x += playerVel.x * dt
        playerPos.y += playerVel.y * dt
        playerPos.z += playerVel.z * dt

        // World bounds
        playerPos.x = playerPos.x.coerceIn(-145f, 145f)
        playerPos.z = playerPos.z.coerceIn(-165f, 135f)

        // Building AABB collisions & rooftop landing + wall-run
        isWallRunning = false
        var groundedNow = false
        val pRadius = 0.8f
        val pHeight = 2.0f

        for (b in buildings) {
            val halfW = b.width * 0.5f + pRadius
            val halfD = b.depth * 0.5f + pRadius
            val dx = playerPos.x - b.x
            val dz = playerPos.z - b.z

            if (abs(dx) < halfW && abs(dz) < halfD) {
                if (playerPos.y >= b.height - 1.2f && playerVel.y <= 0.5f) {
                    // Land on rooftop
                    playerPos.y = b.height
                    playerVel.y = 0f
                    groundedNow = true
                } else if (playerPos.y < b.height - 0.6f) {
                    val penX = halfW - abs(dx)
                    val penZ = halfD - abs(dz)
                    if (penX < penZ) {
                        playerPos.x += if (dx >= 0f) penX else -penX
                        playerVel.x = 0f
                    } else {
                        playerPos.z += if (dz >= 0f) penZ else -penZ
                        playerVel.z = 0f
                    }
                    // Wall-run lift when moving along wall face
                    if (!isGrounded && horizSpeed > 7f) {
                        isWallRunning = true
                        playerVel.y = max(playerVel.y, 4.5f)
                    }
                }
            }
        }

        // Street level floor collision
        if (playerPos.y <= 0f) {
            playerPos.y = 0f
            if (playerVel.y < 0f) playerVel.y = 0f
            groundedNow = true
        }
        isGrounded = groundedNow

        // Passive health regen out of combat
        if (health < maxHealth && comboTimer <= 0f) {
            // Very slow regen every ~1.5s
            if ((elapsedTime * 10f).toInt() % 15 == 0) {
                health = (health + 1).coerceAtMost(maxHealth)
            }
        }
    }

    private fun stepNpcs(dt: Float) {
        for (npc in npcs) {
            if (!npc.alive) {
                npc.respawnTimer -= dt
                if (npc.respawnTimer <= 0f) {
                    npc.alive = true
                    npc.hp = npc.maxHp
                    npc.vx = 0f; npc.vy = 0f; npc.vz = 0f
                }
                continue
            }

            if (npc.attackCooldown > 0f) npc.attackCooldown -= dt

            when (npc.kind) {
                NpcKind.HUSK -> {
                    val dx = playerPos.x - npc.x
                    val dz = playerPos.z - npc.z
                    val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
                    if (dist < 42f && playerPos.y < 10f) {
                        val speed = 7.2f
                        npc.vx = (dx / dist) * speed
                        npc.vz = (dz / dist) * speed
                        npc.yaw = atan2(-dx, -dz)
                        if (dist < 2.2f && abs(playerPos.y - npc.y) < 2.4f && npc.attackCooldown <= 0f) {
                            health = (health - 9).coerceAtLeast(0)
                            npc.attackCooldown = 1.4f
                            if (health == 0) {
                                health = maxHealth
                                playerPos.x = 0f; playerPos.y = 4f; playerPos.z = 22f
                                playerVel.x = 0f; playerVel.y = 0f; playerVel.z = 0f
                                isSwinging = false
                            }
                        }
                    } else {
                        val patrolAngle = elapsedTime * 0.5f + npc.id
                        npc.vx = cos(patrolAngle) * 2.5f
                        npc.vz = sin(patrolAngle) * 2.5f
                        npc.yaw = atan2(-npc.vx, -npc.vz)
                    }
                    npc.vy -= 22f * dt
                    npc.x += npc.vx * dt
                    npc.y = max(0f, npc.y + npc.vy * dt)
                    if (npc.y == 0f) npc.vy = 0f
                    npc.z += npc.vz * dt
                }
                NpcKind.CITIZEN -> {
                    val phase = elapsedTime * 0.35f + npc.id * 1.1f
                    npc.x += cos(phase) * 1.4f * dt
                    npc.z += sin(phase) * 1.4f * dt
                    npc.yaw = -phase
                }
                NpcKind.WARDEN -> {
                    val angle = elapsedTime * 0.65f + npc.id * PI.toFloat()
                    npc.x = portal.x + cos(angle) * 15f
                    npc.z = portal.z + sin(angle) * 15f
                    npc.y = 18f + sin(elapsedTime * 2f + npc.id) * 2.5f
                    npc.yaw = -angle
                }
            }
        }
    }

    private fun stepPickupsAndStory(dt: Float) {
        for (shard in shards) {
            if (shard.collected) continue
            shard.bobPhase += dt * 2.6f
            val dx = shard.x - playerPos.x
            val dy = shard.y - (playerPos.y + 1.0f)
            val dz = shard.z - playerPos.z
            if (dx * dx + dy * dy + dz * dz < 3.6f * 3.6f) {
                shard.collected = true
                shardsCollected++
                score += 500
                stamina = 100f
                soundSynth.playShardChime()
                saveProgress()
            }
        }

        if (shardsCollected >= 4 && husksDefeated >= 4) {
            portal.active = true
        }

        if (portal.active) {
            val dx = portal.x - playerPos.x
            val dz = portal.z - playerPos.z
            if (dx * dx + dz * dz < 6.5f * 6.5f && playerPos.y < 8f) {
                // Multiverse Loop Bonus!
                score += 1500
                soundSynth.playDimensionShift()
                dimension = if (dimension == Dimension.NEON_NOIR) Dimension.INKVERSE else Dimension.NEON_NOIR
                // Respawn shards for endless replayability
                shards.forEach { it.collected = false }
                shardsCollected = 0
                husksDefeated = 0
                portal.active = false
                playerPos.x = 0f; playerPos.y = 14f; playerPos.z = 22f
                saveProgress()
            }
        }
    }

    private fun currentStoryBeatIndex(): Int {
        return when {
            shardsCollected < 4 -> 0
            husksDefeated < 4 -> 1
            else -> 2
        }
    }

    private fun saveProgress() {
        val prevBest = prefs.getInt("best_score", 0)
        if (score > prevBest) {
            prefs.edit().putInt("best_score", score).apply()
        }
    }

    fun publishHud() {
        val beat = AssetCatalog.storyBeats[currentStoryBeatIndex()]
        val speed = sqrt(playerVel.x * playerVel.x + playerVel.y * playerVel.y + playerVel.z * playerVel.z)
        hudListener?.invoke(
            GameHudState(
                health = health,
                maxHealth = maxHealth,
                stamina = stamina,
                speedMps = speed,
                altitudeM = playerPos.y,
                score = score,
                combo = combo,
                shardsCollected = shardsCollected,
                totalShards = shards.size,
                husksDefeated = husksDefeated,
                dimension = dimension,
                isSwinging = isSwinging,
                isWallRunning = isWallRunning,
                portalActive = portal.active,
                storyTitle = beat.title,
                storyObjective = beat.objective,
                muted = soundSynth.muted,
                assetsReady = assetsReady,
                assetStats = assetStats,
                licenseText = licenseText
            )
        )
    }
}
