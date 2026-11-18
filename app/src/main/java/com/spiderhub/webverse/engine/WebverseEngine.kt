package com.spiderhub.webverse.engine

import android.content.Context
import com.spiderhub.webverse.audio.SoundSynth
import com.spiderhub.webverse.game.AssetCatalog
import com.spiderhub.webverse.game.CityBuilding
import com.spiderhub.webverse.game.Dimension
import com.spiderhub.webverse.game.EnergyShard
import com.spiderhub.webverse.game.GameHudState
import com.spiderhub.webverse.game.HoverVehicle
import com.spiderhub.webverse.game.LoadedAssetInfo
import com.spiderhub.webverse.game.NpcEntity
import com.spiderhub.webverse.game.NpcKind
import com.spiderhub.webverse.game.ParticleFx
import com.spiderhub.webverse.game.PortalGate
import com.spiderhub.webverse.game.StreetLamp
import com.spiderhub.webverse.game.SwingAnchor
import com.spiderhub.webverse.game.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WebverseEngine(
    context: Context,
    private val soundSynth: SoundSynth
) {
    private val prefs = context.getSharedPreferences("webverse_save_v1", Context.MODE_PRIVATE)
    val physics = WebPhysicsEngine()

    val buildings = ArrayList<CityBuilding>()
    val anchors = ArrayList<SwingAnchor>()
    val beacons = ArrayList<Vec3>()
    val streetLamps = ArrayList<StreetLamp>()
    val hoverVehicles = ArrayList<HoverVehicle>()
    val particles = ArrayList<ParticleFx>()
    val shards = ArrayList<EnergyShard>()
    val npcs = ArrayList<NpcEntity>()
    var portal = PortalGate(0f, 0f, -110f, active = false)

    // Player physics & animation state
    val playerPos = Vec3(0f, 0f, 22f)
    val playerVel = Vec3(0f, 0f, 0f)
    var playerYaw = 0f
    var cameraYaw = 0f
    var cameraPitch = 0.18f
    var cameraShake = 0f
    var playerWalkCycle = 0f

    var isGrounded = true
    var isWallRunning = false
    var isSwinging = false
    val swingTarget = Vec3()
    var swingLength = 18f
    var bestAnchorPreview: SwingAnchor? = null

    var health = 100
    var maxHealth = 100
    var stamina = 100f
    var score = prefs.getInt("best_score", 0)
    var combo = 0
    var comboTimer = 0f
    var shardsCollected = 0
    var husksDefeated = 0
    var dimension = Dimension.SUNSET_NEXUS
    var elapsedTime = 0f
    var strikePulse = 0f
    var ultraBloomEnabled = true
    var currentFps = 60
    private var fpsFrames = 0
    private var fpsAccum = 0f

    @Volatile var moveStickX = 0f
    @Volatile var moveStickY = 0f
    @Volatile var swingHeld = false

    @Volatile var assetStats: List<LoadedAssetInfo> = emptyList()
    @Volatile var assetsReady: Boolean = false
    @Volatile var licenseText: String = ""

    private var hudListener: ((GameHudState) -> Unit)? = null
    private var hudPublishAccum = 0f
    private val fxRng = Random(777)

    init {
        buildCityWorld()
        physics.initWorld(buildings, playerPos)
        spawnEntities()
        spawnTraffic()
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
                if (gx == 0 && (gz == 0 || gz == 1 || gz == -3)) continue

                val cx = gx * blockStep + (rng.nextFloat() - 0.5f) * 3.5f
                val cz = gz * blockStep + (rng.nextFloat() - 0.5f) * 3.5f
                val w = 14f + rng.nextFloat() * 7.5f
                val d = 14f + rng.nextFloat() * 7.5f
                val h = 32f + rng.nextFloat() * 62f
                val isSpire = (abs(gx) + abs(gz)) % 3 == 0
                val hasSetback = h > 40f

                val tintChoice = rng.nextInt(3)
                val (tr, tg, tb) = when (tintChoice) {
                    0 -> Triple(0.32f, 0.38f, 0.50f)
                    1 -> Triple(0.36f, 0.34f, 0.46f)
                    else -> Triple(0.28f, 0.40f, 0.48f)
                }

                val neonChoice = rng.nextInt(3)
                val (nr, ng, nb) = when (neonChoice) {
                    0 -> Triple(0.0f, 0.94f, 1.0f)
                    1 -> Triple(1.0f, 0.28f, 0.42f)
                    else -> Triple(1.0f, 0.65f, 0.18f)
                }

                val billboardIdx = if (rng.nextFloat() > 0.55f) rng.nextInt(3) else -1

                buildings.add(
                    CityBuilding(
                        id = bId++, x = cx, z = cz, width = w, depth = d, height = h,
                        hasSetback = hasSetback, isSpire = isSpire,
                        tintR = tr, tintG = tg, tintB = tb,
                        neonR = nr, neonG = ng, neonB = nb,
                        billboardTexIdx = billboardIdx
                    )
                )

                // Add rooftop + street-facing grapple anchors for fluid Manhattan-style swinging
                anchors.add(SwingAnchor(aId++, cx, h + 10f, cz))
                anchors.add(SwingAnchor(aId++, cx - w * 0.48f, h * 0.72f, cz + d * 0.48f))
                anchors.add(SwingAnchor(aId++, cx + w * 0.48f, h * 0.72f, cz - d * 0.48f))
                if (h > 44f) {
                    beacons.add(Vec3(cx, h + (if (hasSetback) 10f else 0f), cz))
                }
            }
        }

        for (i in -5..3) {
            val zPos = i * 24f
            streetLamps.add(StreetLamp(-11f, zPos, 90f, isCyan = i % 2 == 0))
            streetLamps.add(StreetLamp(11f, zPos, -90f, isCyan = i % 2 != 0))
        }
    }

    private fun spawnTraffic() {
        val rng = Random(4411)
        for (i in 0 until 14) {
            val axisX = i % 2 == 0
            val dirSign = if (i % 4 < 2) 1f else -1f
            val laneCoord = -18f + (i % 5) * 18f + (rng.nextFloat() - 0.5f) * 3f
            val alt = 18f + (i % 4) * 12f
            val speed = 18f + rng.nextFloat() * 14f
            val isCyan = i % 2 == 0
            hoverVehicles.add(
                HoverVehicle(
                    id = i,
                    x = if (axisX) (rng.nextFloat() * 240f - 120f) else laneCoord,
                    y = alt,
                    z = if (axisX) laneCoord else (rng.nextFloat() * 240f - 120f),
                    speed = speed,
                    axisX = axisX,
                    dirSign = dirSign,
                    laneCoord = laneCoord,
                    accentR = if (isCyan) 0f else 1f,
                    accentG = if (isCyan) 0.92f else 0.22f,
                    accentB = if (isCyan) 1f else 0.5f
                )
            )
        }
    }

    private fun spawnEntities() {
        val rng = Random(9001)
        for (i in 0 until 12) {
            val angle = i * (PI.toFloat() * 2f / 12f)
            val radius = 32f + (i % 3) * 28f
            val sx = cos(angle) * radius
            val sz = sin(angle) * radius - 16f
            val sy = 14f + (i % 4) * 9f
            shards.add(EnergyShard(i, sx, sy, sz, collected = false, bobPhase = i * 0.7f))
        }

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

        for (i in 0 until 10) {
            val side = if (i % 2 == 0) -8f else 8f
            npcs.add(
                NpcEntity(
                    id = npcId++,
                    kind = NpcKind.CITIZEN,
                    variantB = i % 2 == 1,
                    x = side + (rng.nextFloat() - 0.5f) * 2.5f,
                    y = 0f,
                    z = 26f - i * 12f,
                    yaw = if (i % 2 == 0) 0f else PI.toFloat()
                )
            )
        }

        npcs.add(NpcEntity(id = npcId++, kind = NpcKind.WARDEN, x = -14f, y = 18f, z = -102f))
        npcs.add(NpcEntity(id = npcId++, kind = NpcKind.WARDEN, x = 14f, y = 20f, z = -102f))
    }

    fun onLookDrag(dx: Float, dy: Float) {
        cameraYaw -= dx * 0.0052f
        cameraPitch = (cameraPitch + dy * 0.0042f).coerceIn(-0.45f, 1.15f)
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
        val anchor = physics.findBestSwingAnchor(playerPos, cameraYaw, anchors) ?: return
        swingLength = physics.startSwing(anchor, playerPos, playerVel, cameraYaw, swingTarget)
        isSwinging = true
        isGrounded = false
        spawnBurst(swingTarget.x, swingTarget.y, swingTarget.z, 10, 0.85f, 0.95f, 1f)
        soundSynth.playWebThwip()
    }

    private fun releaseSwing() {
        if (!isSwinging) return
        isSwinging = false
        physics.releaseSwingVault(playerVel)
    }

    fun triggerJumpOrVault() {
        if (isSwinging) {
            releaseSwing()
            playerVel.y += 5.0f
            physics.applyJumpOrWebZip(playerVel.x, playerVel.y, playerVel.z)
            soundSynth.playWebThwip()
            return
        }
        if (isGrounded || isWallRunning) {
            playerVel.y = if (isWallRunning) 18.5f else 15.5f
            isGrounded = false
            isWallRunning = false
            physics.applyJumpOrWebZip(playerVel.x, playerVel.y, playerVel.z)
            spawnBurst(playerPos.x, playerPos.y + 0.2f, playerPos.z, 8, 0.9f, 0.95f, 1f)
            soundSynth.playWebThwip()
        }
    }

    fun triggerWebZip() {
        if (stamina < 18f) return
        stamina = (stamina - 20f).coerceAtLeast(0f)
        val fwdX = -sin(cameraYaw)
        val fwdZ = -cos(cameraYaw)
        playerVel.x = fwdX * 44f
        playerVel.y = 9.5f
        playerVel.z = fwdZ * 44f
        isSwinging = false
        isGrounded = false
        physics.applyJumpOrWebZip(playerVel.x, playerVel.y, playerVel.z)
        cameraShake = 0.22f
        spawnBurst(playerPos.x, playerPos.y + 1.2f, playerPos.z, 16, 0.2f, 0.95f, 1f)
        soundSynth.playZipDash()
    }

    fun triggerCombatStrike() {
        strikePulse = 1.0f
        cameraShake = 0.25f
        soundSynth.playStrike()
        spawnBurst(playerPos.x, playerPos.y + 1.1f, playerPos.z, 14, 1f, 0.24f, 0.45f)

        var hits = 0
        for (npc in npcs) {
            if (!npc.alive || npc.kind != NpcKind.HUSK) continue
            val dx = npc.x - playerPos.x
            val dy = npc.y - playerPos.y
            val dz = npc.z - playerPos.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq <= 12.5f * 12.5f) {
                npc.hp -= 55
                npc.hitFlash = 1.0f
                val dist = sqrt(distSq).coerceAtLeast(0.5f)
                npc.vx += (dx / dist) * 18f
                npc.vy = 8f
                npc.vz += (dz / dist) * 18f
                hits++
                spawnBurst(npc.x, npc.y + 1.2f, npc.z, 18, 1f, 0.2f, 0.35f)
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
            cameraShake = 0.45f
        }
    }

    fun toggleDimension() {
        dimension = when (dimension) {
            Dimension.SUNSET_NEXUS -> Dimension.NEON_NOIR
            Dimension.NEON_NOIR -> Dimension.INKVERSE
            Dimension.INKVERSE -> Dimension.SUNSET_NEXUS
        }
        cameraShake = 0.30f
        spawnBurst(playerPos.x, playerPos.y + 1.2f, playerPos.z, 22, 1f, 0.45f, 0.2f)
        soundSynth.playDimensionShift()
        publishHud()
    }

    fun toggleMute(): Boolean {
        soundSynth.muted = !soundSynth.muted
        publishHud()
        return soundSynth.muted
    }

    fun toggleBloom(): Boolean {
        ultraBloomEnabled = !ultraBloomEnabled
        publishHud()
        return ultraBloomEnabled
    }

    private fun spawnBurst(x: Float, y: Float, z: Float, count: Int, r: Float, g: Float, b: Float) {
        if (particles.size > 160) return
        repeat(count) {
            val a = fxRng.nextFloat() * PI.toFloat() * 2f
            val spd = 3f + fxRng.nextFloat() * 11f
            particles.add(
                ParticleFx(
                    x = x, y = y, z = z,
                    vx = cos(a) * spd,
                    vy = (fxRng.nextFloat() - 0.15f) * 9f,
                    vz = sin(a) * spd,
                    life = 0.55f + fxRng.nextFloat() * 0.35f,
                    maxLife = 0.9f,
                    size = 0.20f + fxRng.nextFloat() * 0.22f,
                    r = r, g = g, b = b
                )
            )
        }
    }

    fun update(dtRaw: Float) {
        fpsFrames++
        fpsAccum += dtRaw.coerceIn(0.0005f, 0.25f)
        if (fpsAccum >= 0.5f) {
            currentFps = (fpsFrames / fpsAccum).toInt().coerceIn(1, 240)
            fpsFrames = 0
            fpsAccum = 0f
        }
        val dt = dtRaw.coerceIn(0.001f, 0.04f)
        elapsedTime += dt
        if (strikePulse > 0f) strikePulse = (strikePulse - dt * 3.5f).coerceAtLeast(0f)
        if (cameraShake > 0f) cameraShake = (cameraShake - dt * 1.8f).coerceAtLeast(0f)
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 0
        }
        stamina = (stamina + dt * 16f).coerceAtMost(100f)

        physics.gravity = if (dimension == Dimension.INKVERSE) -22f else -27f

        if (isSwinging && !swingHeld) {
            releaseSwing()
        } else if (!isSwinging && swingHeld) {
            attemptStartSwing()
        }

        val stepRes = physics.step(
            dt = dt,
            playerPos = playerPos,
            playerVel = playerVel,
            moveStickX = moveStickX,
            moveStickY = moveStickY,
            cameraYaw = cameraYaw,
            isGrounded = isGrounded,
            isSwinging = isSwinging,
            swingTarget = swingTarget,
            swingLength = swingLength,
            buildings = buildings,
            onPlayerYawUpdated = { yaw -> playerYaw = yaw },
            onStateUpdated = { groundedNow, wallRunNow, newLen ->
                isGrounded = groundedNow
                isWallRunning = wallRunNow
                swingLength = newLen
            }
        )

        if (stepRes.landedHard) cameraShake = 0.22f
        val horizSpeed = sqrt(playerVel.x * playerVel.x + playerVel.z * playerVel.z)
        if (isGrounded && horizSpeed > 0.5f) {
            playerWalkCycle += dt * horizSpeed * 0.65f
        }
        if (health < maxHealth && comboTimer <= 0f && (elapsedTime * 10f).toInt() % 15 == 0) {
            health = (health + 1).coerceAtMost(maxHealth)
        }

        stepNpcs(dt)
        stepTraffic(dt)
        stepParticles(dt)
        stepPickupsAndStory(dt)
        bestAnchorPreview = if (!isSwinging) physics.findBestSwingAnchor(playerPos, cameraYaw, anchors) else null

        hudPublishAccum += dt
        if (hudPublishAccum >= 0.08f) {
            hudPublishAccum = 0f
            publishHud()
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
            if (npc.hitFlash > 0f) npc.hitFlash = (npc.hitFlash - dt * 4f).coerceAtLeast(0f)

            when (npc.kind) {
                NpcKind.HUSK -> {
                    val dx = playerPos.x - npc.x
                    val dz = playerPos.z - npc.z
                    val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
                    if (dist < 42f && playerPos.y < 10f) {
                        val speed = 7.4f
                        npc.vx = (dx / dist) * speed
                        npc.vz = (dz / dist) * speed
                        npc.yaw = atan2(-dx, -dz)
                        npc.walkPhase += dt * 7f
                        if (dist < 2.2f && abs(playerPos.y - npc.y) < 2.4f && npc.attackCooldown <= 0f) {
                            health = (health - 9).coerceAtLeast(0)
                            cameraShake = 0.30f
                            npc.attackCooldown = 1.4f
                            if (health == 0) {
                                health = maxHealth
                                playerPos.x = 0f; playerPos.y = 4f; playerPos.z = 22f
                                playerVel.x = 0f; playerVel.y = 0f; playerVel.z = 0f
                                isSwinging = false
                                physics.teleportPlayer(playerPos, playerVel)
                            }
                        }
                    } else {
                        val patrolAngle = elapsedTime * 0.5f + npc.id
                        npc.vx = cos(patrolAngle) * 2.5f
                        npc.vz = sin(patrolAngle) * 2.5f
                        npc.yaw = atan2(-npc.vx, -npc.vz)
                        npc.walkPhase += dt * 3.5f
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
                    npc.walkPhase += dt * 3.2f
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

    private fun stepTraffic(dt: Float) {
        for (car in hoverVehicles) {
            if (car.axisX) {
                car.x += car.speed * car.dirSign * dt
                if (car.x > 140f) car.x = -140f
                if (car.x < -140f) car.x = 140f
            } else {
                car.z += car.speed * car.dirSign * dt
                if (car.z > 140f) car.z = -160f
                if (car.z < -160f) car.z = 140f
            }
        }
    }

    private fun stepParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                it.remove()
            } else {
                p.vy -= 9f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt
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
            if (dx * dx + dy * dy + dz * dz < 3.8f * 3.8f) {
                shard.collected = true
                shardsCollected++
                score += 500
                stamina = 100f
                spawnBurst(shard.x, shard.y, shard.z, 18, 0f, 0.95f, 1f)
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
                score += 1500
                soundSynth.playDimensionShift()
                toggleDimension()
                shards.forEach { it.collected = false }
                shardsCollected = 0
                husksDefeated = 0
                portal.active = false
                playerPos.x = 0f; playerPos.y = 14f; playerPos.z = 22f
                playerVel.x = 0f; playerVel.y = 0f; playerVel.z = 0f
                physics.teleportPlayer(playerPos, playerVel)
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
        val pdx = portal.x - playerPos.x
        val pdz = portal.z - playerPos.z
        val portalDist = sqrt(pdx * pdx + pdz * pdz).toInt()
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
                hasGrappleTarget = bestAnchorPreview != null,
                portalActive = portal.active,
                portalDistanceM = portalDist,
                storyTitle = beat.title,
                storyObjective = beat.objective,
                muted = soundSynth.muted,
                ultraBloomEnabled = ultraBloomEnabled,
                fps = currentFps,
                assetsReady = assetsReady,
                assetStats = assetStats,
                licenseText = licenseText
            )
        )
    }
}
