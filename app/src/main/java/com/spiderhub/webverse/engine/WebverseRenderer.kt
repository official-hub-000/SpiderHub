package com.spiderhub.webverse.engine

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.spiderhub.webverse.game.AssetCatalog
import com.spiderhub.webverse.game.Dimension
import com.spiderhub.webverse.game.LoadedAssetInfo
import com.spiderhub.webverse.game.NpcKind
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GpuMesh(
    val vboId: Int,
    val vertexCount: Int
)

class WebverseRenderer(
    private val assets: AssetManager,
    private val engine: WebverseEngine
) : GLSurfaceView.Renderer {

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private val pipeline = ShaderPipeline()

    private lateinit var texFacadePbr: PbrTextureSet
    private lateinit var texAsphaltPbr: PbrTextureSet
    private lateinit var texSuitRedPbr: PbrTextureSet
    private lateinit var texSuitBluePbr: PbrTextureSet
    private lateinit var texHuskPbr: PbrTextureSet
    private lateinit var texInkPbr: PbrTextureSet
    private lateinit var texNeutralPbr: PbrTextureSet
    private var billboardPbrs = emptyList<PbrTextureSet>()

    private lateinit var gpuSkyDome: GpuMesh
    private lateinit var gpuBox: GpuMesh
    private lateinit var gpuGround: GpuMesh
    private lateinit var gpuCrystal: GpuMesh
    private lateinit var gpuRing: GpuMesh
    private lateinit var gpuDisc: GpuMesh
    private lateinit var gpuCylinder: GpuMesh
    private lateinit var gpuBatchedFacade: GpuMesh
    private lateinit var gpuBatchedProps: GpuMesh
    private lateinit var gpuBatchedNeon: GpuMesh
    private lateinit var charHelper: CharacterRendererHelper

    private val pendingCpuMeshes = ConcurrentHashMap<String, CpuMesh>()
    private val uploadedMeshes = HashMap<String, GpuMesh>()

    private var surfaceWidth = 1280
    private var surfaceHeight = 720
    private var lastFrameNs = System.nanoTime()
    private val assetLoaderExecutor = Executors.newSingleThreadExecutor()

    private var activeAlbedoTex = -1
    private var activeVbo = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        pipeline.init()

        texFacadePbr = TextureGenerator.createSkyscraperPbrTextures()
        texAsphaltPbr = TextureGenerator.createWetAsphaltPbrTextures()
        texSuitRedPbr = TextureGenerator.createHeroRedSuitPbrTextures()
        texSuitBluePbr = TextureGenerator.createHeroBlueSuitPbrTextures()
        texHuskPbr = TextureGenerator.createHuskArmorPbrTextures()
        texInkPbr = TextureGenerator.createInkversePbrTextures()

        val flatNorm = TextureGenerator.createFlatNormalTexture()
        val defaultPbr = TextureGenerator.createDefaultPbrTexture(roughness = 0.26f, metallic = 0.72f, emissive = 0.1f)
        val whiteBmp = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val whiteTexIds = IntArray(1)
        GLES20.glGenTextures(1, whiteTexIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, whiteTexIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, whiteBmp, 0)
        whiteBmp.recycle()
        texNeutralPbr = PbrTextureSet(whiteTexIds[0], flatNorm, defaultPbr)

        val billboardEmissivePbr = TextureGenerator.createDefaultPbrTexture(roughness = 0.25f, metallic = 0.2f, emissive = 0.80f)
        billboardPbrs = (0..2).map { idx ->
            PbrTextureSet(TextureGenerator.createBillboardTexture(idx), flatNorm, billboardEmissivePbr)
        }

        gpuSkyDome = uploadMesh(RealisticMeshFactory.createSkyDome(320f))
        gpuBox = uploadMesh(RealisticMeshFactory.createDetailedBuildingBox())
        gpuGround = uploadMesh(MeshLoader.createGroundQuad(220f, 36f))
        gpuCrystal = uploadMesh(RealisticMeshFactory.createCrystalShard(0.75f, 1.75f))
        gpuRing = uploadMesh(RealisticMeshFactory.createTorusRing(1.0f, 0.08f, 32, 10))
        gpuDisc = uploadMesh(RealisticMeshFactory.createDisc(28))
        gpuCylinder = uploadMesh(MeshLoader.createCylinder(12))

        // Bake static city into 3 batched VBOs for maximum Android FPS
        val batched = StaticCityBatcher.buildBatchedCity(engine.buildings, engine.streetLamps)
        gpuBatchedFacade = uploadMesh(batched.facadeMesh)
        gpuBatchedProps = uploadMesh(batched.propsAndSidewalksMesh)
        gpuBatchedNeon = uploadMesh(batched.neonTrimMesh)

        val charMeshes = CharacterMeshes(::uploadMesh)
        charHelper = CharacterRendererHelper(charMeshes) { mesh, mat, r, g, b, em, uvScale, pbr ->
            drawMeshWithMatrix(mesh, mat, r, g, b, em, uvScale, pbr)
        }

        startAsyncAssetLoading()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        pipeline.resize(surfaceWidth, surfaceHeight)
    }

    fun loadCustomModelStream(streamProvider: () -> InputStream?, displayName: String) {
        assetLoaderExecutor.execute {
            val isGlb = displayName.lowercase().endsWith(".glb")
            val mesh = streamProvider()?.use { stream ->
                if (isGlb) {
                    MeshLoader.loadGlbFromStream(stream, targetHeight = 8.5f)
                } else {
                    MeshLoader.loadObjFromStream(stream, targetHeight = 8.5f, convertZUpToYUp = false)
                }
            }
            if (mesh != null) {
                pendingCpuMeshes["custom_import"] = mesh
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000.0).toFloat()
        lastFrameNs = now

        activeAlbedoTex = -1
        activeVbo = -1
        drainPendingMeshes()
        engine.update(dt)

        val worldMode = when (engine.dimension) {
            Dimension.SUNSET_NEXUS -> 0f
            Dimension.NEON_NOIR -> 1f
            Dimension.INKVERSE -> 2f
        }
        val (fogR, fogG, fogB) = when (engine.dimension) {
            Dimension.SUNSET_NEXUS -> Triple(0.68f, 0.42f, 0.34f)
            Dimension.NEON_NOIR -> Triple(0.03f, 0.06f, 0.14f)
            Dimension.INKVERSE -> Triple(0.18f, 0.07f, 0.25f)
        }

        val speed = sqrt(engine.playerVel.x * engine.playerVel.x + engine.playerVel.z * engine.playerVel.z)
        val speedNorm = (speed / 44f).coerceIn(0f, 1f)
        val fov = 62f + speedNorm * 14f + if (engine.isSwinging) 4f else 0f
        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, fov, aspect, 0.35f, 440f)

        val camDist = (if (engine.isSwinging) 7.4f else 5.6f) + speedNorm * 1.4f
        val px = engine.playerPos.x
        val py = engine.playerPos.y + 1.45f
        val pz = engine.playerPos.z

        val shakeX = if (engine.cameraShake > 0f) sin(engine.elapsedTime * 55f) * engine.cameraShake * 0.42f else 0f
        val shakeY = if (engine.cameraShake > 0f) cos(engine.elapsedTime * 48f) * engine.cameraShake * 0.42f else 0f

        val horizDist = cos(engine.cameraPitch) * camDist
        val camX = px + sin(engine.cameraYaw) * horizDist + shakeX
        val camY = (py + sin(engine.cameraPitch) * camDist + 0.75f + shakeY).coerceAtLeast(1.1f)
        val camZ = pz + cos(engine.cameraYaw) * horizDist

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, px, py + 0.35f, pz, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val useBloom = engine.ultraBloomEnabled
        pipeline.beginScenePass(useBloom)

        GLES20.glClearColor(fogR, fogG, fogB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        drawSkyDome(camX, camY, camZ, worldMode)

        GLES20.glUseProgram(pipeline.pbrProgram)
        GLES20.glUniform3f(pipeline.uCamPos, camX, camY, camZ)
        GLES20.glUniform3f(pipeline.uFogColor, fogR, fogG, fogB)
        GLES20.glUniform1f(pipeline.uWorldMode, worldMode)
        GLES20.glUniform1f(pipeline.uTime, engine.elapsedTime)

        GLES20.glUniform3f(pipeline.uPointLight0, px, py + 1.5f, pz)
        if (engine.strikePulse > 0f) {
            GLES20.glUniform3f(pipeline.uPointColor0, 2.4f, 0.55f, 0.95f)
        } else if (engine.dimension == Dimension.SUNSET_NEXUS) {
            GLES20.glUniform3f(pipeline.uPointColor0, 0.65f, 0.55f, 0.45f)
        } else {
            GLES20.glUniform3f(pipeline.uPointColor0, 0.15f, 1.35f, 1.65f)
        }

        GLES20.glUniform3f(pipeline.uPointLight1, engine.portal.x, 8f, engine.portal.z)
        if (engine.portal.active) {
            GLES20.glUniform3f(pipeline.uPointColor1, 0.2f, 2.4f, 1.9f)
        } else {
            GLES20.glUniform3f(pipeline.uPointColor1, 1.8f, 0.35f, 0.75f)
        }

        val isInk = engine.dimension == Dimension.INKVERSE
        val worldFacadeTex = if (isInk) texInkPbr else texFacadePbr
        val worldAsphaltTex = if (isInk) texInkPbr else texAsphaltPbr

        // 1. Urban Asphalt Boulevard
        Matrix.setIdentityM(modelMatrix, 0)
        drawMeshWithMatrix(gpuGround, modelMatrix, 0.96f, 0.98f, 1.0f, 0.02f, 1.0f, worldAsphaltTex)

        // 2. Entire Metropolis Skyline, Sidewalks, Water Towers, HVACs, Skybridges & Neon Trims in 3 batched draw calls!
        drawMeshWithMatrix(gpuBatchedFacade, modelMatrix, 0.78f, 0.84f, 0.98f, 0.08f, 1.0f, worldFacadeTex)
        drawMeshWithMatrix(gpuBatchedProps, modelMatrix, 0.48f, 0.52f, 0.60f, 0.02f, 1.0f, texNeutralPbr)
        val trimEm = if (engine.dimension == Dimension.SUNSET_NEXUS) 0.95f else 1.45f
        drawMeshWithMatrix(gpuBatchedNeon, modelMatrix, 0.2f, 0.94f, 1.0f, trimEm, 1.0f, texNeutralPbr)

        // 3. Holographic Billboards & Rooftop Spires (Culled by distance)
        val spireMesh = uploadedMeshes["spire"]
        for (b in engine.buildings) {
            val dx = b.x - px; val dz = b.z - pz
            if (dx * dx + dz * dz > 195f * 195f) continue

            if (b.billboardTexIdx in billboardPbrs.indices) {
                val baseH = if (b.hasSetback) b.height * 0.72f else b.height
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, b.x, baseH * 0.56f, b.z + b.depth * 0.5f + 0.25f)
                Matrix.scaleM(modelMatrix, 0, b.width * 0.72f, 4.4f, 0.35f)
                drawMeshWithMatrix(gpuBox, modelMatrix, 1f, 1f, 1f, 0.85f, 1.0f, billboardPbrs[b.billboardTexIdx])
            }

            if (b.isSpire && spireMesh != null) {
                val topY = if (b.hasSetback) b.height + 8f else b.height
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, b.x, topY, b.z)
                Matrix.scaleM(modelMatrix, 0, 1.15f, 1.15f, 1.15f)
                drawMeshWithMatrix(spireMesh, modelMatrix, b.neonR, b.neonG, b.neonB, 0.65f, 1.0f, texNeutralPbr)
            }
        }

        // Optional User-Imported Sketchfab/CGTrader .GLB/.OBJ Monument in Central Plaza
        val customMesh = uploadedMeshes["custom_import"]
        if (customMesh != null) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, 0.28f, -14f)
            Matrix.rotateM(modelMatrix, 0, engine.elapsedTime * 18f, 0f, 1f, 0f)
            drawMeshWithMatrix(customMesh, modelMatrix, 0.92f, 0.95f, 1.0f, 0.22f, 1.0f, texNeutralPbr)
        }

        // 4. Flying Cyber Hover-Traffic
        for (car in engine.hoverVehicles) {
            val dx = car.x - px; val dz = car.z - pz
            if (dx * dx + dz * dz > 195f * 195f) continue
            charHelper.drawHoverCar(car, texHuskPbr, texNeutralPbr)
        }

        // 5. Rooftop Quantum Signal Beacons
        val beaconMesh = uploadedMeshes["beacon"] ?: gpuCylinder
        for (bPos in engine.beacons) {
            val dx = bPos.x - px; val dz = bPos.z - pz
            if (dx * dx + dz * dz > 195f * 195f) continue

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, bPos.x, bPos.y, bPos.z)
            if (beaconMesh === gpuCylinder) Matrix.scaleM(modelMatrix, 0, 1.1f, 4.2f, 1.1f)
            drawMeshWithMatrix(beaconMesh, modelMatrix, 1.0f, 0.24f, 0.48f, 1.05f, 1f, texNeutralPbr)

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, bPos.x, bPos.y + 4.4f, bPos.z)
            Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            val pulse = 1.4f + sin(engine.elapsedTime * 4f) * 0.25f
            Matrix.scaleM(modelMatrix, 0, pulse, pulse, pulse)
            drawMeshWithMatrix(gpuRing, modelMatrix, 0f, 0.96f, 1f, 1.4f, 1f, texNeutralPbr)
        }

        // 6. Chrono-Shards
        for (shard in engine.shards) {
            if (shard.collected) continue
            val bobY = shard.y + sin(shard.bobPhase) * 0.45f
            val spin = engine.elapsedTime * 95f + shard.id * 28f

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, shard.x, bobY, shard.z)
            Matrix.rotateM(modelMatrix, 0, spin, 0f, 1f, 0f)
            drawMeshWithMatrix(gpuCrystal, modelMatrix, 0.05f, 0.96f, 1.0f, 1.3f, 1f, texNeutralPbr)

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, shard.x, bobY, shard.z)
            Matrix.rotateM(modelMatrix, 0, spin * 1.4f, 0.4f, 1f, 0.3f)
            Matrix.scaleM(modelMatrix, 0, 1.15f, 1.15f, 1.15f)
            drawMeshWithMatrix(gpuRing, modelMatrix, 1.0f, 0.28f, 0.65f, 1.3f, 1f, texNeutralPbr)
        }

        // 7. Multiverse Stargate Portal
        val pColor = if (engine.portal.active) floatArrayOf(0f, 1f, 0.85f) else floatArrayOf(1f, 0.28f, 0.45f)
        val portalY = 6.2f
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, engine.portal.x, portalY, engine.portal.z)
        Matrix.rotateM(modelMatrix, 0, engine.elapsedTime * 32f, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, 5.8f, 5.8f, 5.8f)
        drawMeshWithMatrix(gpuRing, modelMatrix, pColor[0], pColor[1], pColor[2], 1.25f, 1f, texNeutralPbr)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, engine.portal.x, portalY, engine.portal.z)
        Matrix.rotateM(modelMatrix, 0, -engine.elapsedTime * 55f, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, 4.6f, 4.6f, 4.6f)
        drawMeshWithMatrix(gpuRing, modelMatrix, 0.3f, 0.95f, 1f, 1.45f, 1f, texNeutralPbr)

        if (engine.portal.active) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, engine.portal.x, portalY, engine.portal.z)
            Matrix.scaleM(modelMatrix, 0, 4.2f, 4.2f, 1f)
            drawMeshWithMatrix(gpuDisc, modelMatrix, 0.05f, 0.92f, 0.95f, 1.2f, 1f, texInkPbr)
        }

        // 8. Articulated NPCs (Culled beyond 150m for max FPS)
        for (npc in engine.npcs) {
            if (!npc.alive) continue
            val dx = npc.x - px; val dz = npc.z - pz
            if (dx * dx + dz * dz > 155f * 155f) continue
            when (npc.kind) {
                NpcKind.HUSK -> charHelper.drawHusk(npc, texHuskPbr, texNeutralPbr)
                NpcKind.CITIZEN -> charHelper.drawCitizen(npc, texSuitBluePbr, texNeutralPbr)
                NpcKind.WARDEN -> charHelper.drawWardenDrone(npc, engine.elapsedTime, texHuskPbr, texNeutralPbr)
            }
        }

        // 9. Spider-Hero Kage in Crimson & Cobalt Webbed Suit
        charHelper.drawHero(engine, texSuitRedPbr, texSuitBluePbr, texNeutralPbr)

        // 10. Silky White-Silver Web Tether & Grapple Target Lock-On Ring
        if (engine.isSwinging) {
            drawCurvedWebLine(
                engine.playerPos.x, engine.playerPos.y + 1.55f, engine.playerPos.z,
                engine.swingTarget.x, engine.swingTarget.y, engine.swingTarget.z
            )
        } else {
            val anchor = engine.bestAnchorPreview
            if (anchor != null) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, anchor.x, anchor.y, anchor.z)
                Matrix.rotateM(modelMatrix, 0, engine.elapsedTime * 110f, 0f, 1f, 0f)
                Matrix.scaleM(modelMatrix, 0, 1.7f, 1.7f, 1.7f)
                drawMeshWithMatrix(gpuRing, modelMatrix, 0.95f, 0.98f, 1.0f, 1.55f, 1f, texNeutralPbr)
            }
        }

        // 11. Particles & Optional Weather
        drawWeatherAndParticles(px, py, pz, engine.dimension)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        pipeline.finishPostProcessPass(useBloom, speedNorm, worldMode)
    }

    private fun drawSkyDome(camX: Float, camY: Float, camZ: Float, worldMode: Float) {
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(pipeline.skyProgram)
        GLES20.glUniformMatrix4fv(pipeline.uSkyVp, 1, false, vpMatrix, 0)
        GLES20.glUniform3f(pipeline.uSkyCamPos, camX, camY, camZ)
        GLES20.glUniform1f(pipeline.uSkyWorldMode, worldMode)
        GLES20.glUniform1f(pipeline.uSkyTime, engine.elapsedTime)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpuSkyDome.vboId)
        val stride = MeshLoader.FLOATS_PER_VERTEX * 4
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glDisableVertexAttribArray(1)
        GLES20.glDisableVertexAttribArray(2)
        GLES20.glDisableVertexAttribArray(3)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gpuSkyDome.vertexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDepthMask(true)
    }

    private fun drawCurvedWebLine(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        val segs = 6
        var prevX = x0; var prevY = y0; var prevZ = z0
        for (i in 1..segs) {
            val t = i.toFloat() / segs
            val sag = sin(t * Math.PI.toFloat()) * 1.15f
            val nx = x0 + (x1 - x0) * t
            val ny = y0 + (y1 - y0) * t - sag
            val nz = z0 + (z1 - z0) * t
            drawWebSegment(prevX, prevY, prevZ, nx, ny, nz)
            prevX = nx; prevY = ny; prevZ = nz
        }
    }

    private fun drawWebSegment(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val horiz = sqrt(dx * dx + dz * dz)
        val pitchDeg = Math.toDegrees(atan2(horiz.toDouble(), dy.toDouble())).toFloat()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x0, y0, z0)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.085f, len, 0.085f)
        drawMeshWithMatrix(gpuCylinder, modelMatrix, 0.96f, 0.98f, 1.0f, 1.45f, 1f, texNeutralPbr)
    }

    private fun drawWeatherAndParticles(px: Float, py: Float, pz: Float, dim: Dimension) {
        if (dim != Dimension.SUNSET_NEXUS) {
            val rainColor = if (dim == Dimension.INKVERSE) Triple(1f, 0.25f, 0.55f) else Triple(0.35f, 0.85f, 1.0f)
            for (i in 0 until 20) {
                val rx = px + sin(i * 19.7f) * 18f
                val rz = pz + cos(i * 31.3f) * 18f
                val fall = ((engine.elapsedTime * 22f + i * 3.7f) % 24f)
                val ry = (py + 12f - fall).coerceAtLeast(0.2f)
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, rx, ry, rz)
                Matrix.scaleM(modelMatrix, 0, 0.03f, 0.85f, 0.03f)
                drawMeshWithMatrix(gpuBox, modelMatrix, rainColor.first, rainColor.second, rainColor.third, 1.1f, 1f, texNeutralPbr)
            }
        }

        for (p in engine.particles) {
            val alphaScale = (p.life / p.maxLife).coerceIn(0.1f, 1f)
            val s = p.size * alphaScale
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
            Matrix.scaleM(modelMatrix, 0, s, s, s)
            drawMeshWithMatrix(gpuCrystal, modelMatrix, p.r, p.g, p.b, 1.65f, 1f, texNeutralPbr)
        }
    }

    private fun drawMeshWithMatrix(
        mesh: GpuMesh,
        matrix: FloatArray,
        r: Float,
        g: Float,
        b: Float,
        emissive: Float,
        uvScale: Float,
        pbr: PbrTextureSet
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, matrix, 0)
        GLES20.glUniformMatrix4fv(pipeline.uMvp, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(pipeline.uModel, 1, false, matrix, 0)
        GLES20.glUniform3f(pipeline.uColor, r, g, b)
        GLES20.glUniform1f(pipeline.uEmissive, emissive)
        GLES20.glUniform1f(pipeline.uUvScale, uvScale)

        if (activeAlbedoTex != pbr.albedoTex) {
            activeAlbedoTex = pbr.albedoTex
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pbr.albedoTex)
            GLES20.glUniform1i(pipeline.uAlbedoTex, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pbr.normalTex)
            GLES20.glUniform1i(pipeline.uNormalTex, 1)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pbr.pbrTex)
            GLES20.glUniform1i(pipeline.uPbrTex, 2)
        }

        bindVertexAttributes(mesh)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun bindVertexAttributes(mesh: GpuMesh) {
        if (activeVbo != mesh.vboId) {
            activeVbo = mesh.vboId
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboId)
            val stride = MeshLoader.FLOATS_PER_VERTEX * 4
            GLES20.glEnableVertexAttribArray(0)
            GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0)
            GLES20.glEnableVertexAttribArray(1)
            GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
            GLES20.glEnableVertexAttribArray(2)
            GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, stride, 6 * 4)
            GLES20.glEnableVertexAttribArray(3)
            GLES20.glVertexAttribPointer(3, 3, GLES20.GL_FLOAT, false, stride, 8 * 4)
        }
    }

    private fun startAsyncAssetLoading() {
        assetLoaderExecutor.execute {
            val stats = ArrayList<LoadedAssetInfo>()
            for (spec in AssetCatalog.models) {
                val path = "models/${spec.fileName}"
                val sizeBytes = MeshLoader.getAssetByteSize(assets, path)
                val targetH = when (spec.id) {
                    "spire" -> 16f
                    "portal" -> 10f
                    "beacon" -> 4.5f
                    "shard" -> 1.8f
                    else -> 2.1f
                }
                val mesh = MeshLoader.loadObjFromAssets(assets, path, targetHeight = targetH, convertZUpToYUp = true)
                if (mesh != null) {
                    pendingCpuMeshes[spec.id] = mesh
                }
                stats.add(
                    LoadedAssetInfo(
                        spec = spec,
                        loaded = mesh != null,
                        vertexCount = mesh?.vertexCount ?: 0,
                        triangleCount = mesh?.triangleCount ?: 0,
                        byteSize = sizeBytes
                    )
                )
            }

            val license = try {
                assets.open("models/LICENSES.md").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                "Bundled assets from SpiderHub repository."
            }

            engine.assetStats = stats
            engine.licenseText = license
            engine.assetsReady = true
            engine.publishHud()
        }
    }

    private fun drainPendingMeshes() {
        if (pendingCpuMeshes.isEmpty()) return
        val keys = pendingCpuMeshes.keys.toList()
        for (key in keys) {
            val cpuMesh = pendingCpuMeshes.remove(key) ?: continue
            uploadedMeshes[key] = uploadMesh(cpuMesh)
        }
    }

    private fun uploadMesh(cpu: CpuMesh): GpuMesh {
        val vbo = IntArray(1)
        GLES20.glGenBuffers(1, vbo, 0)
        val fb: FloatBuffer = ByteBuffer.allocateDirect(cpu.vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(cpu.vertices)
        fb.position(0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cpu.vertices.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return GpuMesh(vbo[0], cpu.vertexCount)
    }
}
