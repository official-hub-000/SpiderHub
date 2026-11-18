package com.spiderhub.webverse.engine

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.spiderhub.webverse.game.AssetCatalog
import com.spiderhub.webverse.game.Dimension
import com.spiderhub.webverse.game.LoadedAssetInfo
import com.spiderhub.webverse.game.NpcKind
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

    private var programId = 0
    private var uMvpLoc = -1
    private var uModelLoc = -1
    private var uCamPosLoc = -1
    private var uColorLoc = -1
    private var uEmissiveLoc = -1
    private var uFogColorLoc = -1
    private var uInkModeLoc = -1
    private var uUseTexLoc = -1
    private var uTexLoc = -1

    private var texFacade = 0
    private var texAsphalt = 0
    private var texInk = 0
    private var texSuit = 0

    private lateinit var gpuBox: GpuMesh
    private lateinit var gpuGround: GpuMesh
    private lateinit var gpuShardFallback: GpuMesh
    private lateinit var gpuCylinder: GpuMesh

    private val pendingCpuMeshes = ConcurrentHashMap<String, CpuMesh>()
    private val uploadedMeshes = HashMap<String, GpuMesh>()

    private var lastFrameNs = System.nanoTime()
    private val assetLoaderExecutor = Executors.newSingleThreadExecutor()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        programId = createShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpLoc = GLES20.glGetUniformLocation(programId, "uMvp")
        uModelLoc = GLES20.glGetUniformLocation(programId, "uModel")
        uCamPosLoc = GLES20.glGetUniformLocation(programId, "uCamPos")
        uColorLoc = GLES20.glGetUniformLocation(programId, "uColor")
        uEmissiveLoc = GLES20.glGetUniformLocation(programId, "uEmissive")
        uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
        uInkModeLoc = GLES20.glGetUniformLocation(programId, "uInkMode")
        uUseTexLoc = GLES20.glGetUniformLocation(programId, "uUseTex")
        uTexLoc = GLES20.glGetUniformLocation(programId, "uTex")

        texFacade = TextureGenerator.createTowerFacadeTexture()
        texAsphalt = TextureGenerator.createWetAsphaltTexture()
        texInk = TextureGenerator.createInkverseTexture()
        texSuit = TextureGenerator.createSuitWeaveTexture()

        gpuBox = uploadMesh(MeshLoader.createUnitBox())
        gpuGround = uploadMesh(MeshLoader.createGroundQuad(210f, 34f))
        gpuShardFallback = uploadMesh(MeshLoader.createOctahedron(0.85f))
        gpuCylinder = uploadMesh(MeshLoader.createCylinder(10))

        startAsyncAssetLoading()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 66f, aspect, 0.4f, 420f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000.0).toFloat()
        lastFrameNs = now

        drainPendingMeshes()
        engine.update(dt)

        val isInk = engine.dimension == Dimension.INKVERSE
        val fogR = if (isInk) 0.14f else 0.03f
        val fogG = if (isInk) 0.08f else 0.05f
        val fogB = if (isInk) 0.22f else 0.11f

        GLES20.glClearColor(fogR, fogG, fogB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val camDist = if (engine.isSwinging) 10.5f else 8.2f
        val px = engine.playerPos.x
        val py = engine.playerPos.y + 1.8f
        val pz = engine.playerPos.z

        val horizDist = cos(engine.cameraPitch) * camDist
        val camX = px + sin(engine.cameraYaw) * horizDist
        val camY = (py + sin(engine.cameraPitch) * camDist + 1.2f).coerceAtLeast(1.2f)
        val camZ = pz + cos(engine.cameraYaw) * horizDist

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, px, py + 0.6f, pz, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glUseProgram(programId)
        GLES20.glUniform3f(uCamPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(uFogColorLoc, fogR, fogG, fogB)
        GLES20.glUniform1f(uInkModeLoc, if (isInk) 1f else 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uTexLoc, 0)

        // 1. Wet Asphalt Ground
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (isInk) texInk else texAsphalt)
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(gpuGround, 0.85f, 0.90f, 1.0f, 0.05f, useTex = true)

        // 2. Bundled apocalyptic_city.glb skyline quadrants
        val cityMesh = uploadedMeshes["apocalyptic_city"]
        if (cityMesh != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (isInk) texInk else texFacade)
            val offsets = arrayOf(
                floatArrayOf(-92f, -92f, 0f),
                floatArrayOf(92f, -92f, 90f),
                floatArrayOf(-92f, 88f, 180f),
                floatArrayOf(92f, 88f, 270f)
            )
            for (o in offsets) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, o[0], 0f, o[1])
                Matrix.rotateM(modelMatrix, 0, o[2], 0f, 1f, 0f)
                drawMesh(cityMesh, 0.55f, 0.62f, 0.82f, 0.14f, useTex = true)
            }
        }

        // 3. Procedural Cyber Towers & Anchor Spires
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (isInk) texInk else texFacade)
        val spireMesh = uploadedMeshes["spire"]
        for (b in engine.buildings) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, b.x, 0f, b.z)
            Matrix.scaleM(modelMatrix, 0, b.width, b.height, b.depth)
            drawMesh(gpuBox, b.tintR * 3.8f, b.tintG * 3.8f, b.tintB * 3.8f, 0.18f, useTex = true)

            if (b.isSpire && spireMesh != null) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, b.x, b.height, b.z)
                Matrix.scaleM(modelMatrix, 0, 1.2f, 1.2f, 1.2f)
                drawMesh(spireMesh, 0.0f, 0.92f, 1.0f, 0.48f, useTex = false)
            }
        }

        // 4. Rooftop Signal Beacons
        val beaconMesh = uploadedMeshes["beacon"] ?: gpuCylinder
        for (bPos in engine.beacons) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, bPos.x, bPos.y, bPos.z)
            if (beaconMesh === gpuCylinder) Matrix.scaleM(modelMatrix, 0, 1.2f, 4.0f, 1.2f)
            drawMesh(beaconMesh, 1.0f, 0.22f, 0.45f, 0.65f, useTex = false)
        }

        // 5. Chrono-Shards
        val shardMesh = uploadedMeshes["shard"] ?: gpuShardFallback
        for (shard in engine.shards) {
            if (shard.collected) continue
            Matrix.setIdentityM(modelMatrix, 0)
            val bobY = shard.y + sin(shard.bobPhase) * 0.45f
            Matrix.translateM(modelMatrix, 0, shard.x, bobY, shard.z)
            Matrix.rotateM(modelMatrix, 0, engine.elapsedTime * 90f + shard.id * 25f, 0f, 1f, 0f)
            drawMesh(shardMesh, 0.0f, 0.95f, 1.0f, 0.85f, useTex = false)
        }

        // 6. Multiverse Nexus Portal
        val portalMesh = uploadedMeshes["portal"] ?: gpuCylinder
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, engine.portal.x, engine.portal.y, engine.portal.z)
        if (portalMesh === gpuCylinder) Matrix.scaleM(modelMatrix, 0, 6.5f, 9.0f, 1.5f)
        val pColor = if (engine.portal.active) floatArrayOf(0f, 1f, 0.82f, 0.95f) else floatArrayOf(1f, 0.2f, 0.45f, 0.45f)
        drawMesh(portalMesh, pColor[0], pColor[1], pColor[2], pColor[3], useTex = false)

        // 7. NPCs
        val huskMesh = uploadedMeshes["husk"] ?: gpuBox
        val citizenMeshA = uploadedMeshes["citizen"] ?: gpuBox
        val citizenMeshB = uploadedMeshes["citizen_b"] ?: citizenMeshA

        for (npc in engine.npcs) {
            if (!npc.alive) continue
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, npc.x, npc.y, npc.z)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(npc.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
            val mesh = when (npc.kind) {
                NpcKind.HUSK -> huskMesh
                NpcKind.CITIZEN -> if (npc.variantB) citizenMeshB else citizenMeshA
                NpcKind.WARDEN -> spireMesh ?: gpuShardFallback
            }
            if (mesh === gpuBox) Matrix.scaleM(modelMatrix, 0, 0.9f, 1.9f, 0.7f)
            when (npc.kind) {
                NpcKind.HUSK -> drawMesh(mesh, 1.0f, 0.16f, 0.28f, 0.45f, useTex = false)
                NpcKind.CITIZEN -> drawMesh(mesh, 0.68f, 0.78f, 0.95f, 0.12f, useTex = false)
                NpcKind.WARDEN -> drawMesh(mesh, 0.75f, 0.35f, 1.0f, 0.65f, useTex = false)
            }
        }

        // 8. Kage Hero Avatar
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texSuit)
        val kageMesh = uploadedMeshes["kage"] ?: gpuBox
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, engine.playerPos.x, engine.playerPos.y, engine.playerPos.z)
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(engine.playerYaw.toDouble()).toFloat(), 0f, 1f, 0f)
        if (engine.isSwinging) {
            Matrix.rotateM(modelMatrix, 0, -24f, 1f, 0f, 0f)
        }
        if (kageMesh === gpuBox) Matrix.scaleM(modelMatrix, 0, 0.8f, 1.9f, 0.6f)
        val pulse = if (engine.strikePulse > 0f) 0.85f else 0.25f
        drawMesh(kageMesh, 0.96f, 0.22f, 0.42f, pulse, useTex = true)

        // 9. Active 3D Web Tether Line
        if (engine.isSwinging) {
            drawWebLine(
                engine.playerPos.x, engine.playerPos.y + 1.4f, engine.playerPos.z,
                engine.swingTarget.x, engine.swingTarget.y, engine.swingTarget.z
            )
        }
    }

    private fun drawWebLine(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val horiz = sqrt(dx * dx + dz * dz)
        val pitchDeg = Math.toDegrees(atan2(horiz.toDouble(), dy.toDouble())).toFloat()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x0, y0, z0)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.08f, len, 0.08f)
        drawMesh(gpuCylinder, 0.72f, 0.98f, 1.0f, 0.95f, useTex = false)
    }

    private fun drawMesh(
        mesh: GpuMesh,
        r: Float,
        g: Float,
        b: Float,
        emissive: Float,
        useTex: Boolean
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uColorLoc, r, g, b)
        GLES20.glUniform1f(uEmissiveLoc, emissive)
        GLES20.glUniform1f(uUseTexLoc, if (useTex) 1f else 0f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboId)
        val stride = 8 * 4
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, stride, 6 * 4)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun startAsyncAssetLoading() {
        assetLoaderExecutor.execute {
            val stats = ArrayList<LoadedAssetInfo>()
            for (spec in AssetCatalog.models) {
                val path = "models/${spec.fileName}"
                val sizeBytes = MeshLoader.getAssetByteSize(assets, path)
                val mesh = if (spec.format == "GLB") {
                    MeshLoader.loadGlbFromAssets(assets, path, targetHeight = 36f)
                } else {
                    val targetH = when (spec.id) {
                        "spire" -> 16f
                        "portal" -> 10f
                        "beacon" -> 4.5f
                        "shard" -> 1.8f
                        else -> 2.1f
                    }
                    MeshLoader.loadObjFromAssets(assets, path, targetHeight = targetH)
                }

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

    private fun createShaderProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glBindAttribLocation(prog, 0, "aPos")
        GLES20.glBindAttribLocation(prog, 1, "aNorm")
        GLES20.glBindAttribLocation(prog, 2, "aUv")
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
attribute vec3 aPos;
attribute vec3 aNorm;
attribute vec2 aUv;

uniform mat4 uMvp;
uniform mat4 uModel;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUv;

void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vWorldPos = wp.xyz;
    vNormal = (uModel * vec4(aNorm, 0.0)).xyz;
    vUv = aUv;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """
precision mediump float;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec2 vUv;

uniform sampler2D uTex;
uniform vec3 uCamPos;
uniform vec3 uColor;
uniform float uEmissive;
uniform vec3 uFogColor;
uniform float uInkMode;
uniform float uUseTex;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos - vWorldPos);
    vec3 L = normalize(vec3(0.45, 0.82, 0.36));

    vec3 texSample = uUseTex > 0.5 ? texture2D(uTex, vUv * 2.0).rgb : vec3(1.0);
    vec3 albedo = uColor * texSample;

    float ndotl = max(dot(N, L), 0.0);
    if (uInkMode > 0.5) {
        ndotl = ndotl > 0.55 ? 1.0 : (ndotl > 0.2 ? 0.45 : 0.15);
    }

    vec3 H = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), 24.0) * 0.45;
    float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);

    vec3 rimColor = uInkMode > 0.5 ? vec3(1.0, 0.18, 0.39) : vec3(0.0, 0.92, 1.0);
    vec3 lit = albedo * (0.28 + ndotl * 0.88) + vec3(spec) + rimColor * fresnel * 0.45 + albedo * uEmissive;

    lit = lit / (lit + vec3(0.65));

    float dist = length(uCamPos - vWorldPos);
    float fogFactor = clamp((dist - 45.0) / 170.0, 0.0, 0.92);
    vec3 finalCol = mix(lit, uFogColor, fogFactor);

    gl_FragColor = vec4(finalCol, 1.0);
}
"""
    }
}
