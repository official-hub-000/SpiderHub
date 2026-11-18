package com.spiderhub.webverse.engine

import android.opengl.Matrix
import com.spiderhub.webverse.game.HoverVehicle
import com.spiderhub.webverse.game.NpcEntity
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class CharacterMeshes(upload: (CpuMesh) -> GpuMesh) {
    val torso = upload(RealisticMeshFactory.createSculptedSegment(0.20f, 0.29f, 0.31f, 0.68f, zScale = 0.68f, segments = 16, rings = 6))
    val head = upload(RealisticMeshFactory.createSculptedHead(0.145f, 0.175f, 0.165f))
    val visor = upload(RealisticMeshFactory.createHeroVisor())
    val upperArm = upload(RealisticMeshFactory.createSculptedSegment(0.082f, 0.098f, 0.106f, 0.34f, zScale = 0.92f, segments = 12, rings = 4))
    val forearm = upload(RealisticMeshFactory.createSculptedSegment(0.064f, 0.082f, 0.080f, 0.32f, zScale = 0.92f, segments = 12, rings = 4))
    val thigh = upload(RealisticMeshFactory.createSculptedSegment(0.105f, 0.138f, 0.148f, 0.44f, zScale = 0.95f, segments = 12, rings = 4))
    val calf = upload(RealisticMeshFactory.createSculptedSegment(0.078f, 0.105f, 0.095f, 0.44f, zScale = 0.92f, segments = 12, rings = 4))
    val pauldron = upload(RealisticMeshFactory.createSculptedHead(0.15f, 0.09f, 0.14f))
    val hoverCar = upload(RealisticMeshFactory.createHoverCar())
    val disc = upload(RealisticMeshFactory.createDisc(24))
    val ring = upload(RealisticMeshFactory.createTorusRing(1.0f, 0.10f, 24, 8))
}

class CharacterRendererHelper(
    private val meshes: CharacterMeshes,
    private val drawCall: (
        mesh: GpuMesh,
        matrix: FloatArray,
        r: Float, g: Float, b: Float,
        emissive: Float,
        uvScale: Float,
        pbr: PbrTextureSet
    ) -> Unit
) {
    private val baseMat = FloatArray(16)
    private val partMat = FloatArray(16)
    private val mUpperMat = FloatArray(16)
    private val mLowerMat = FloatArray(16)

    fun drawHero(
        engine: WebverseEngine,
        suitRedTex: PbrTextureSet,
        suitBlueTex: PbrTextureSet,
        neutralTex: PbrTextureSet
    ) {
        val px = engine.playerPos.x
        val py = engine.playerPos.y
        val pz = engine.playerPos.z
        val yawDeg = Math.toDegrees(engine.playerYaw.toDouble()).toFloat()
        val horizSpeed = sqrt(engine.playerVel.x * engine.playerVel.x + engine.playerVel.z * engine.playerVel.z)
        val stride = sin(engine.playerWalkCycle) * (horizSpeed / 16f).coerceIn(0f, 1f)

        Matrix.setIdentityM(baseMat, 0)
        Matrix.translateM(baseMat, 0, px, py, pz)
        Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)

        if (engine.isSwinging) {
            Matrix.rotateM(baseMat, 0, -28f, 1f, 0f, 0f)
        } else if (!engine.isGrounded) {
            val pitch = (engine.playerVel.y * 1.3f).coerceIn(-28f, 25f)
            Matrix.rotateM(baseMat, 0, -pitch, 1f, 0f, 0f)
        } else if (horizSpeed > 2f) {
            Matrix.rotateM(baseMat, 0, -12f, 1f, 0f, 0f)
        }

        // Contact shadow disc
        if (py < 20f) {
            Matrix.setIdentityM(partMat, 0)
            Matrix.translateM(partMat, 0, px, 0.04f, pz)
            Matrix.rotateM(partMat, 0, -90f, 1f, 0f, 0f)
            val s = (1.15f - py * 0.04f).coerceIn(0.35f, 1.15f)
            Matrix.scaleM(partMat, 0, s, s, 1f)
            drawCall(meshes.disc, partMat, 0.02f, 0.03f, 0.05f, 0f, 1f, neutralTex)
        }

        val pulseEm = if (engine.strikePulse > 0f) 0.75f else 0.08f
        val hipY = 0.86f

        // Crimson Red Upper Torso + Webbing
        drawPart(baseMat, 0f, hipY + 0.14f, 0f, 0f, 0f, 1.02f, 0.82f, 1.02f) { m ->
            drawCall(meshes.torso, m, 1.0f, 0.96f, 0.96f, pulseEm, 2f, suitRedTex)
        }
        // Cobalt Blue Waist / Belt Abdomen
        drawPart(baseMat, 0f, hipY - 0.04f, 0f, 0f, 0f, 0.98f, 0.38f, 0.98f) { m ->
            drawCall(meshes.torso, m, 0.95f, 0.98f, 1.0f, pulseEm * 0.5f, 1.5f, suitBlueTex)
        }

        // Iconic Glowing White/Cyan Spider Emblem on Back (+Z) and Chest (-Z)
        drawPart(baseMat, 0f, hipY + 0.44f, 0.21f, 0f, 0f, 0.38f, 0.45f, 0.12f) { m ->
            drawCall(meshes.pauldron, m, 0.95f, 0.98f, 1.0f, 1.25f, 1f, neutralTex)
        }
        drawPart(baseMat, 0f, hipY + 0.44f, -0.20f, 0f, 0f, 0.34f, 0.42f, 0.12f) { m ->
            drawCall(meshes.pauldron, m, 0.95f, 0.98f, 1.0f, 1.25f, 1f, neutralTex)
        }

        // Masked Spider-Hero Head + Expressive White Lenses
        drawPart(baseMat, 0f, hipY + 0.70f, 0f, 0f, 0f, 1f, 1f, 1f) { m ->
            drawCall(meshes.head, m, 1.0f, 0.96f, 0.96f, pulseEm, 1.6f, suitRedTex)
            drawCall(meshes.visor, m, 0.95f, 0.98f, 1.0f, 1.45f, 1f, neutralTex)
        }

        // Crimson Shoulder Armor Caps
        drawPart(baseMat, -0.32f, hipY + 0.56f, 0f, 0f, 0f, 0.88f, 0.88f, 0.88f) { m ->
            drawCall(meshes.pauldron, m, 1.0f, 0.96f, 0.96f, pulseEm, 1f, suitRedTex)
        }
        drawPart(baseMat, 0.32f, hipY + 0.56f, 0f, 0f, 0f, 0.88f, 0.88f, 0.88f) { m ->
            drawCall(meshes.pauldron, m, 1.0f, 0.96f, 0.96f, pulseEm, 1f, suitRedTex)
        }

        // Arms: Cobalt-Blue Upper Arms, Crimson-Red Forearms/Gloves
        val rightArmPitch = when {
            engine.strikePulse > 0f -> -118f
            engine.isSwinging -> -150f
            else -> stride * 40f + 12f
        }
        val leftArmPitch = when {
            engine.isSwinging -> 36f
            else -> -stride * 40f + 12f
        }
        drawLimbChain(baseMat, 0.31f, hipY + 0.60f, 0f, rightArmPitch, 14f, meshes.upperArm, meshes.forearm, 0.34f, suitBlueTex, suitRedTex, 1f, 1f, 1f, pulseEm)
        drawLimbChain(baseMat, -0.31f, hipY + 0.60f, 0f, leftArmPitch, -14f, meshes.upperArm, meshes.forearm, 0.34f, suitBlueTex, suitRedTex, 1f, 1f, 1f, pulseEm)

        // Legs: Cobalt-Blue Thighs, Crimson-Red Boots
        val rightLegPitch = if (engine.isSwinging) -44f else if (!engine.isGrounded) -26f else -stride * 44f
        val leftLegPitch = if (engine.isSwinging) 18f else if (!engine.isGrounded) 22f else stride * 44f
        drawLimbChain(baseMat, 0.13f, hipY, 0f, rightLegPitch, 4f, meshes.thigh, meshes.calf, 0.44f, suitBlueTex, suitRedTex, 1f, 1f, 1f, pulseEm * 0.5f)
        drawLimbChain(baseMat, -0.13f, hipY, 0f, leftLegPitch, -4f, meshes.thigh, meshes.calf, 0.44f, suitBlueTex, suitRedTex, 1f, 1f, 1f, pulseEm * 0.5f)
    }

    fun drawHusk(
        npc: NpcEntity,
        huskTex: PbrTextureSet,
        neutralTex: PbrTextureSet
    ) {
        val yawDeg = Math.toDegrees(npc.yaw.toDouble()).toFloat()
        val stride = sin(npc.walkPhase) * 0.85f
        val scale = 1.18f

        Matrix.setIdentityM(baseMat, 0)
        Matrix.translateM(baseMat, 0, npc.x, npc.y, npc.z)
        Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(baseMat, 0, scale, scale, scale)

        val flash = npc.hitFlash
        val r = 0.78f + flash * 0.6f
        val g = 0.24f + flash * 0.5f
        val b = 0.30f + flash * 0.5f
        val em = 0.25f + flash * 1.1f
        val hipY = 0.86f

        drawPart(baseMat, 0f, hipY, 0f, 0f, 0f, 1.18f, 1.05f, 1.15f) { m ->
            drawCall(meshes.torso, m, r, g, b, em, 1.5f, huskTex)
        }
        drawPart(baseMat, 0f, hipY + 0.42f, -0.22f, 0f, 0f, 0.22f, 0.22f, 0.08f) { m ->
            drawCall(meshes.pauldron, m, 1.0f, 0.14f, 0.25f, 1.4f, 1f, neutralTex)
        }
        drawPart(baseMat, 0f, hipY + 0.70f, 0f, 0f, 0f, 1.05f, 1.0f, 1.05f) { m ->
            drawCall(meshes.head, m, 0.35f, 0.38f, 0.45f, em * 0.4f, 1f, huskTex)
            drawCall(meshes.visor, m, 1.0f, 0.12f, 0.22f, 1.5f, 1f, neutralTex)
        }
        drawPart(baseMat, -0.39f, hipY + 0.60f, 0f, 0f, 18f, 1.35f, 1.25f, 1.2f) { m ->
            drawCall(meshes.pauldron, m, 0.45f, 0.16f, 0.22f, em, 1f, huskTex)
        }
        drawPart(baseMat, 0.39f, hipY + 0.60f, 0f, 0f, -18f, 1.35f, 1.25f, 1.2f) { m ->
            drawCall(meshes.pauldron, m, 0.45f, 0.16f, 0.22f, em, 1f, huskTex)
        }

        drawLimbChain(baseMat, 0.38f, hipY + 0.60f, 0f, stride * 34f - 18f, 10f, meshes.upperArm, meshes.forearm, 0.34f, huskTex, huskTex, r, g, b, em * 0.7f)
        drawLimbChain(baseMat, -0.38f, hipY + 0.60f, 0f, -stride * 34f - 18f, -10f, meshes.upperArm, meshes.forearm, 0.34f, huskTex, huskTex, r, g, b, em * 0.7f)
        drawLimbChain(baseMat, 0.16f, hipY, 0f, -stride * 36f, 4f, meshes.thigh, meshes.calf, 0.44f, huskTex, huskTex, 0.35f, 0.38f, 0.44f, 0.1f)
        drawLimbChain(baseMat, -0.16f, hipY, 0f, stride * 36f, -4f, meshes.thigh, meshes.calf, 0.44f, huskTex, huskTex, 0.35f, 0.38f, 0.44f, 0.1f)
    }

    fun drawCitizen(
        npc: NpcEntity,
        suitBlueTex: PbrTextureSet,
        neutralTex: PbrTextureSet
    ) {
        val yawDeg = Math.toDegrees(npc.yaw.toDouble()).toFloat()
        val stride = sin(npc.walkPhase) * 0.5f

        Matrix.setIdentityM(baseMat, 0)
        Matrix.translateM(baseMat, 0, npc.x, npc.y, npc.z)
        Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(baseMat, 0, 0.94f, 0.94f, 0.94f)

        val (cr, cg, cb) = if (npc.variantB) Triple(0.32f, 0.68f, 0.90f) else Triple(0.80f, 0.52f, 0.75f)
        val hipY = 0.84f

        drawPart(baseMat, 0f, hipY, 0f, 0f, 0f, 0.96f, 1.0f, 0.96f) { m ->
            drawCall(meshes.torso, m, cr, cg, cb, 0.08f, 1.2f, suitBlueTex)
        }
        drawPart(baseMat, 0f, hipY + 0.68f, 0f, 0f, 0f, 0.95f, 0.95f, 0.95f) { m ->
            drawCall(meshes.head, m, 0.82f, 0.70f, 0.64f, 0.04f, 1f, neutralTex)
        }
        drawLimbChain(baseMat, 0.28f, hipY + 0.58f, 0f, stride * 26f, 6f, meshes.upperArm, meshes.forearm, 0.34f, neutralTex, neutralTex, cr, cg, cb, 0.04f)
        drawLimbChain(baseMat, -0.28f, hipY + 0.58f, 0f, -stride * 26f, -6f, meshes.upperArm, meshes.forearm, 0.34f, neutralTex, neutralTex, cr, cg, cb, 0.04f)
        drawLimbChain(baseMat, 0.12f, hipY, 0f, -stride * 28f, 2f, meshes.thigh, meshes.calf, 0.44f, neutralTex, neutralTex, 0.24f, 0.28f, 0.38f, 0.02f)
        drawLimbChain(baseMat, -0.12f, hipY, 0f, stride * 28f, -2f, meshes.thigh, meshes.calf, 0.44f, neutralTex, neutralTex, 0.24f, 0.28f, 0.38f, 0.02f)
    }

    fun drawWardenDrone(
        npc: NpcEntity,
        elapsed: Float,
        huskTex: PbrTextureSet,
        neutralTex: PbrTextureSet
    ) {
        val yawDeg = Math.toDegrees(npc.yaw.toDouble()).toFloat()
        Matrix.setIdentityM(baseMat, 0)
        Matrix.translateM(baseMat, 0, npc.x, npc.y, npc.z)
        Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)

        drawPart(baseMat, 0f, 0f, 0f, 0f, 0f, 3.2f, 2.2f, 3.2f) { m ->
            drawCall(meshes.head, m, 0.35f, 0.25f, 0.55f, 0.35f, 1f, huskTex)
        }
        drawPart(baseMat, 0f, 0.2f, 0f, 90f, elapsed * 140f, 1.6f, 1.6f, 1.6f) { m ->
            drawCall(meshes.ring, m, 0.65f, 0.32f, 1.0f, 1.15f, 1f, neutralTex)
        }
        drawPart(baseMat, 0f, 0.2f, -0.5f, 0f, 0f, 1.2f, 1.2f, 1.2f) { m ->
            drawCall(meshes.pauldron, m, 0.2f, 0.95f, 1.0f, 1.5f, 1f, neutralTex)
        }
    }

    fun drawHoverCar(
        car: HoverVehicle,
        huskTex: PbrTextureSet,
        neutralTex: PbrTextureSet
    ) {
        val yawDeg = if (car.axisX) {
            if (car.dirSign > 0f) -90f else 90f
        } else {
            if (car.dirSign > 0f) 180f else 0f
        }

        Matrix.setIdentityM(baseMat, 0)
        Matrix.translateM(baseMat, 0, car.x, car.y, car.z)
        Matrix.rotateM(baseMat, 0, yawDeg, 0f, 1f, 0f)
        drawCall(meshes.hoverCar, baseMat, 0.42f, 0.48f, 0.62f, 0.14f, 1f, huskTex)

        drawPart(baseMat, 0f, 0.05f, -2.32f, 0f, 0f, 1.3f, 0.16f, 0.18f) { m ->
            drawCall(meshes.pauldron, m, 0.9f, 0.98f, 1.0f, 1.6f, 1f, neutralTex)
        }
        drawPart(baseMat, 0f, 0.10f, 2.35f, 0f, 0f, 1.5f, 0.20f, 0.20f) { m ->
            drawCall(meshes.pauldron, m, car.accentR, car.accentG, car.accentB, 1.8f, 1f, neutralTex)
        }
    }

    private inline fun drawPart(
        parent: FloatArray,
        ox: Float, oy: Float, oz: Float,
        rotX: Float, rotZ: Float,
        sx: Float, sy: Float, sz: Float,
        block: (FloatArray) -> Unit
    ) {
        System.arraycopy(parent, 0, partMat, 0, 16)
        Matrix.translateM(partMat, 0, ox, oy, oz)
        if (abs(rotX) > 0.01f) Matrix.rotateM(partMat, 0, rotX, 1f, 0f, 0f)
        if (abs(rotZ) > 0.01f) Matrix.rotateM(partMat, 0, rotZ, 0f, 0f, 1f)
        Matrix.scaleM(partMat, 0, sx, sy, sz)
        block(partMat)
    }

    private fun drawLimbChain(
        parent: FloatArray,
        ox: Float, oy: Float, oz: Float,
        pitchDeg: Float, rollDeg: Float,
        upperMesh: GpuMesh, lowerMesh: GpuMesh,
        segLen: Float,
        upperTex: PbrTextureSet,
        lowerTex: PbrTextureSet,
        r: Float, g: Float, b: Float, em: Float
    ) {
        System.arraycopy(parent, 0, mUpperMat, 0, 16)
        Matrix.translateM(mUpperMat, 0, ox, oy, oz)
        Matrix.rotateM(mUpperMat, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(mUpperMat, 0, rollDeg, 0f, 0f, 1f)
        Matrix.rotateM(mUpperMat, 0, 180f, 1f, 0f, 0f)
        drawCall(upperMesh, mUpperMat, r, g, b, em, 1.2f, upperTex)

        System.arraycopy(mUpperMat, 0, mLowerMat, 0, 16)
        Matrix.translateM(mLowerMat, 0, 0f, segLen, 0f)
        Matrix.rotateM(mLowerMat, 0, -18f, 1f, 0f, 0f)
        drawCall(lowerMesh, mLowerMat, r, g, b, em, 1.2f, lowerTex)
    }
}
