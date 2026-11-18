package com.spiderhub.webverse.engine

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interleaved vertex layout: [px, py, pz, nx, ny, nz, u, v] (8 floats per vertex).
 */
class CpuMesh(
    val vertices: FloatArray,
    val vertexCount: Int,
    val triangleCount: Int
)

private class FloatList(initialCapacity: Int = 1024) {
    private var data = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[size++] = v
    }

    fun get(index: Int): Float = data[index]

    fun toFloatArray(): FloatArray = data.copyOf(size)
}

object MeshLoader {

    fun loadObjFromAssets(
        assets: AssetManager,
        assetPath: String,
        targetHeight: Float = 2.0f,
        centerBottomOnZero: Boolean = true
    ): CpuMesh? {
        return try {
            val positions = FloatList(2048)
            val normals = FloatList(2048)
            val texCoords = FloatList(2048)
            val outVerts = FloatList(8192)

            assets.open(assetPath).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        val parts = trimmed.split(Regex("\\s+"))
                        when (parts[0]) {
                            "v" -> if (parts.size >= 4) {
                                positions.add(parts[1].toFloatOrNull() ?: 0f)
                                positions.add(parts[2].toFloatOrNull() ?: 0f)
                                positions.add(parts[3].toFloatOrNull() ?: 0f)
                            }
                            "vn" -> if (parts.size >= 4) {
                                normals.add(parts[1].toFloatOrNull() ?: 0f)
                                normals.add(parts[2].toFloatOrNull() ?: 1f)
                                normals.add(parts[3].toFloatOrNull() ?: 0f)
                            }
                            "vt" -> if (parts.size >= 3) {
                                texCoords.add(parts[1].toFloatOrNull() ?: 0f)
                                texCoords.add(1f - (parts[2].toFloatOrNull() ?: 0f))
                            }
                            "f" -> if (parts.size >= 4) {
                                val faceCount = parts.size - 1
                                for (i in 1 until faceCount - 1) {
                                    emitObjVertex(parts[1], positions, normals, texCoords, outVerts)
                                    emitObjVertex(parts[1 + i], positions, normals, texCoords, outVerts)
                                    emitObjVertex(parts[2 + i], positions, normals, texCoords, outVerts)
                                }
                            }
                        }
                    }
                }
            }

            if (outVerts.size == 0) return null
            val array = outVerts.toFloatArray()
            if (normals.size == 0) {
                computeFlatNormals(array)
            }
            normalizeMesh(array, targetHeight, centerBottomOnZero)
            val vertCount = array.size / 8
            CpuMesh(array, vertCount, vertCount / 3)
        } catch (_: Exception) {
            null
        }
    }

    private fun emitObjVertex(
        token: String,
        positions: FloatList,
        normals: FloatList,
        texCoords: FloatList,
        out: FloatList
    ) {
        val comps = token.split("/")
        val posCount = positions.size / 3
        val vtCount = texCoords.size / 2
        val vnCount = normals.size / 3

        val vIdx = resolveObjIndex(comps.getOrNull(0), posCount)
        val vtIdx = resolveObjIndex(comps.getOrNull(1), vtCount)
        val vnIdx = resolveObjIndex(comps.getOrNull(2), vnCount)

        val px: Float
        val py: Float
        val pz: Float
        if (vIdx in 0 until posCount) {
            px = positions.get(vIdx * 3)
            py = positions.get(vIdx * 3 + 1)
            pz = positions.get(vIdx * 3 + 2)
        } else {
            px = 0f; py = 0f; pz = 0f
        }
        out.add(px); out.add(py); out.add(pz)

        if (vnIdx in 0 until vnCount) {
            out.add(normals.get(vnIdx * 3))
            out.add(normals.get(vnIdx * 3 + 1))
            out.add(normals.get(vnIdx * 3 + 2))
        } else {
            out.add(0f); out.add(1f); out.add(0f)
        }

        if (vtIdx in 0 until vtCount) {
            out.add(texCoords.get(vtIdx * 2))
            out.add(texCoords.get(vtIdx * 2 + 1))
        } else {
            out.add(px * 0.25f)
            out.add(py * 0.25f)
        }
    }

    private fun resolveObjIndex(raw: String?, count: Int): Int {
        if (raw.isNullOrEmpty() || count <= 0) return -1
        val parsed = raw.toIntOrNull() ?: return -1
        return if (parsed > 0) parsed - 1 else count + parsed
    }

    /**
     * Parses a binary glTF 2.0 (.glb) file without allocating boxed Float lists or copying the BIN chunk.
     */
    fun loadGlbFromAssets(
        assets: AssetManager,
        assetPath: String,
        targetHeight: Float = 38.0f
    ): CpuMesh? {
        return try {
            val bytes = assets.open(assetPath).use { it.readBytes() }
            if (bytes.size < 20) return null

            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.getInt(0)
            if (magic != 0x46546C67) return null

            var offset = 12
            var jsonText: String? = null
            var binStart = -1
            var binEnd = -1

            while (offset + 8 <= bytes.size) {
                val chunkLength = buf.getInt(offset)
                val chunkType = buf.getInt(offset + 4)
                val dataStart = offset + 8
                val dataEnd = min(bytes.size, dataStart + chunkLength)
                if (chunkType == 0x4E4F534A) {
                    jsonText = String(bytes, dataStart, dataEnd - dataStart, Charsets.UTF_8)
                } else if (chunkType == 0x004E4942) {
                    binStart = dataStart
                    binEnd = dataEnd
                }
                offset = dataStart + ((chunkLength + 3) and 3.inv())
            }

            if (jsonText == null || binStart < 0) return null
            val root = JSONObject(jsonText)

            val accessors = root.optJSONArray("accessors") ?: return null
            val bufferViews = root.optJSONArray("bufferViews") ?: return null
            val meshes = root.optJSONArray("meshes") ?: return null

            val outVerts = FloatList(32768)

            for (m in 0 until meshes.length()) {
                val meshObj = meshes.getJSONObject(m)
                val primitives = meshObj.optJSONArray("primitives") ?: continue
                for (p in 0 until primitives.length()) {
                    val prim = primitives.getJSONObject(p)
                    val mode = prim.optInt("mode", 4)
                    if (mode != 4) continue
                    val attrs = prim.optJSONObject("attributes") ?: continue
                    val posAccIdx = attrs.optInt("POSITION", -1)
                    if (posAccIdx < 0) continue

                    val normAccIdx = attrs.optInt("NORMAL", -1)
                    val uvAccIdx = attrs.optInt("TEXCOORD_0", -1)
                    val idxAccIdx = prim.optInt("indices", -1)

                    val posData = readFloatAccessor(accessors, bufferViews, buf, binStart, binEnd, posAccIdx, 3) ?: continue
                    val normData = if (normAccIdx >= 0) readFloatAccessor(accessors, bufferViews, buf, binStart, binEnd, normAccIdx, 3) else null
                    val uvData = if (uvAccIdx >= 0) readFloatAccessor(accessors, bufferViews, buf, binStart, binEnd, uvAccIdx, 2) else null

                    val vCount = posData.size / 3
                    if (idxAccIdx >= 0) {
                        val indices = readIndexAccessor(accessors, bufferViews, buf, binStart, binEnd, idxAccIdx) ?: continue
                        for (idx in indices) {
                            if (idx !in 0 until vCount) continue
                            appendGlbVertex(idx, posData, normData, uvData, outVerts)
                        }
                    } else {
                        for (idx in 0 until vCount) {
                            appendGlbVertex(idx, posData, normData, uvData, outVerts)
                        }
                    }
                }
            }

            if (outVerts.size == 0) return null
            val array = outVerts.toFloatArray()
            normalizeMesh(array, targetHeight, centerBottomOnZero = true)
            val vertCount = array.size / 8
            CpuMesh(array, vertCount, vertCount / 3)
        } catch (_: Exception) {
            null
        }
    }

    private fun appendGlbVertex(
        idx: Int,
        pos: FloatArray,
        norm: FloatArray?,
        uv: FloatArray?,
        out: FloatList
    ) {
        out.add(pos[idx * 3])
        out.add(pos[idx * 3 + 1])
        out.add(pos[idx * 3 + 2])
        if (norm != null && idx * 3 + 2 < norm.size) {
            out.add(norm[idx * 3])
            out.add(norm[idx * 3 + 1])
            out.add(norm[idx * 3 + 2])
        } else {
            out.add(0f); out.add(1f); out.add(0f)
        }
        if (uv != null && idx * 2 + 1 < uv.size) {
            out.add(uv[idx * 2])
            out.add(uv[idx * 2 + 1])
        } else {
            out.add(pos[idx * 3] * 0.1f)
            out.add(pos[idx * 3 + 2] * 0.1f)
        }
    }

    private fun readFloatAccessor(
        accessors: JSONArray,
        bufferViews: JSONArray,
        buf: ByteBuffer,
        binStart: Int,
        binEnd: Int,
        accIndex: Int,
        components: Int
    ): FloatArray? {
        val acc = accessors.optJSONObject(accIndex) ?: return null
        val bvIndex = acc.optInt("bufferView", -1)
        if (bvIndex < 0) return null
        val bv = bufferViews.optJSONObject(bvIndex) ?: return null

        val count = acc.optInt("count", 0)
        val accOffset = acc.optInt("byteOffset", 0)
        val bvOffset = bv.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", components * 4).coerceAtLeast(components * 4)
        val start = binStart + bvOffset + accOffset

        val result = FloatArray(count * components)
        for (i in 0 until count) {
            val base = start + i * stride
            if (base + components * 4 > binEnd) break
            for (c in 0 until components) {
                result[i * components + c] = buf.getFloat(base + c * 4)
            }
        }
        return result
    }

    private fun readIndexAccessor(
        accessors: JSONArray,
        bufferViews: JSONArray,
        buf: ByteBuffer,
        binStart: Int,
        binEnd: Int,
        accIndex: Int
    ): IntArray? {
        val acc = accessors.optJSONObject(accIndex) ?: return null
        val bvIndex = acc.optInt("bufferView", -1)
        if (bvIndex < 0) return null
        val bv = bufferViews.optJSONObject(bvIndex) ?: return null

        val count = acc.optInt("count", 0)
        val compType = acc.optInt("componentType", 5123)
        val start = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val result = IntArray(count)

        when (compType) {
            5121 -> {
                for (i in 0 until count) {
                    if (start + i >= binEnd) break
                    result[i] = buf.get(start + i).toInt() and 0xFF
                }
            }
            5123 -> {
                for (i in 0 until count) {
                    if (start + i * 2 + 2 > binEnd) break
                    result[i] = buf.getShort(start + i * 2).toInt() and 0xFFFF
                }
            }
            5125 -> {
                for (i in 0 until count) {
                    if (start + i * 4 + 4 > binEnd) break
                    result[i] = buf.getInt(start + i * 4)
                }
            }
            else -> return null
        }
        return result
    }

    private fun computeFlatNormals(verts: FloatArray) {
        var i = 0
        while (i + 23 < verts.size) {
            val ax = verts[i]; val ay = verts[i + 1]; val az = verts[i + 2]
            val bx = verts[i + 8]; val by = verts[i + 9]; val bz = verts[i + 10]
            val cx = verts[i + 16]; val cy = verts[i + 17]; val cz = verts[i + 18]

            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = cx - ax; val vy = cy - ay; val vz = cz - az

            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-5f) {
                nx /= len; ny /= len; nz /= len
            } else {
                nx = 0f; ny = 1f; nz = 0f
            }

            for (k in 0..2) {
                verts[i + k * 8 + 3] = nx
                verts[i + k * 8 + 4] = ny
                verts[i + k * 8 + 5] = nz
            }
            i += 24
        }
    }

    private fun normalizeMesh(verts: FloatArray, targetHeight: Float, centerBottomOnZero: Boolean) {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var i = 0
        while (i < verts.size) {
            val x = verts[i]; val y = verts[i + 1]; val z = verts[i + 2]
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
            minZ = min(minZ, z); maxZ = max(maxZ, z)
            i += 8
        }

        val cx = (minX + maxX) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val cy = if (centerBottomOnZero) minY else (minY + maxY) * 0.5f
        val height = max(0.001f, maxY - minY)
        val spanXZ = max(maxX - minX, maxZ - minZ)
        val refSize = if (height > 0.01f) height else max(0.001f, spanXZ)
        val scale = targetHeight / refSize

        i = 0
        while (i < verts.size) {
            verts[i] = (verts[i] - cx) * scale
            verts[i + 1] = (verts[i + 1] - cy) * scale
            verts[i + 2] = (verts[i + 2] - cz) * scale
            i += 8
        }
    }

    fun createUnitBox(): CpuMesh {
        val raw = floatArrayOf(
            -0.5f, 0f, 0.5f, 0f, 0f, 1f, 0f, 0f,
            0.5f, 0f, 0.5f, 0f, 0f, 1f, 1f, 0f,
            0.5f, 1f, 0.5f, 0f, 0f, 1f, 1f, 1f,
            -0.5f, 0f, 0.5f, 0f, 0f, 1f, 0f, 0f,
            0.5f, 1f, 0.5f, 0f, 0f, 1f, 1f, 1f,
            -0.5f, 1f, 0.5f, 0f, 0f, 1f, 0f, 1f,

            0.5f, 0f, -0.5f, 0f, 0f, -1f, 0f, 0f,
            -0.5f, 0f, -0.5f, 0f, 0f, -1f, 1f, 0f,
            -0.5f, 1f, -0.5f, 0f, 0f, -1f, 1f, 1f,
            0.5f, 0f, -0.5f, 0f, 0f, -1f, 0f, 0f,
            -0.5f, 1f, -0.5f, 0f, 0f, -1f, 1f, 1f,
            0.5f, 1f, -0.5f, 0f, 0f, -1f, 0f, 1f,

            -0.5f, 0f, -0.5f, -1f, 0f, 0f, 0f, 0f,
            -0.5f, 0f, 0.5f, -1f, 0f, 0f, 1f, 0f,
            -0.5f, 1f, 0.5f, -1f, 0f, 0f, 1f, 1f,
            -0.5f, 0f, -0.5f, -1f, 0f, 0f, 0f, 0f,
            -0.5f, 1f, 0.5f, -1f, 0f, 0f, 1f, 1f,
            -0.5f, 1f, -0.5f, -1f, 0f, 0f, 0f, 1f,

            0.5f, 0f, 0.5f, 1f, 0f, 0f, 0f, 0f,
            0.5f, 0f, -0.5f, 1f, 0f, 0f, 1f, 0f,
            0.5f, 1f, -0.5f, 1f, 0f, 0f, 1f, 1f,
            0.5f, 0f, 0.5f, 1f, 0f, 0f, 0f, 0f,
            0.5f, 1f, -0.5f, 1f, 0f, 0f, 1f, 1f,
            0.5f, 1f, 0.5f, 1f, 0f, 0f, 0f, 1f,

            -0.5f, 1f, 0.5f, 0f, 1f, 0f, 0f, 0f,
            0.5f, 1f, 0.5f, 0f, 1f, 0f, 1f, 0f,
            0.5f, 1f, -0.5f, 0f, 1f, 0f, 1f, 1f,
            -0.5f, 1f, 0.5f, 0f, 1f, 0f, 0f, 0f,
            0.5f, 1f, -0.5f, 0f, 1f, 0f, 1f, 1f,
            -0.5f, 1f, -0.5f, 0f, 1f, 0f, 0f, 1f
        )
        return CpuMesh(raw, raw.size / 8, raw.size / 24)
    }

    fun createGroundQuad(halfExtent: Float, uvScale: Float): CpuMesh {
        val raw = floatArrayOf(
            -halfExtent, 0f, halfExtent, 0f, 1f, 0f, 0f, 0f,
            halfExtent, 0f, halfExtent, 0f, 1f, 0f, uvScale, 0f,
            halfExtent, 0f, -halfExtent, 0f, 1f, 0f, uvScale, uvScale,
            -halfExtent, 0f, halfExtent, 0f, 1f, 0f, 0f, 0f,
            halfExtent, 0f, -halfExtent, 0f, 1f, 0f, uvScale, uvScale,
            -halfExtent, 0f, -halfExtent, 0f, 1f, 0f, 0f, uvScale
        )
        return CpuMesh(raw, 6, 2)
    }

    fun createOctahedron(radius: Float): CpuMesh {
        val top = floatArrayOf(0f, radius, 0f)
        val bot = floatArrayOf(0f, -radius, 0f)
        val ring = Array(4) { i ->
            val a = (i * Math.PI * 0.5).toFloat()
            floatArrayOf(cos(a) * radius, 0f, sin(a) * radius)
        }
        val out = FloatList(8 * 3 * 8)
        for (i in 0 until 4) {
            val next = (i + 1) % 4
            addTri(top, ring[next], ring[i], out)
            addTri(bot, ring[i], ring[next], out)
        }
        val arr = out.toFloatArray()
        computeFlatNormals(arr)
        return CpuMesh(arr, arr.size / 8, arr.size / 24)
    }

    private fun addTri(a: FloatArray, b: FloatArray, c: FloatArray, out: FloatList) {
        out.add(a[0]); out.add(a[1]); out.add(a[2]); out.add(0f); out.add(1f); out.add(0f); out.add(0.5f); out.add(1f)
        out.add(b[0]); out.add(b[1]); out.add(b[2]); out.add(0f); out.add(1f); out.add(0f); out.add(1f); out.add(0f)
        out.add(c[0]); out.add(c[1]); out.add(c[2]); out.add(0f); out.add(1f); out.add(0f); out.add(0f); out.add(0f)
    }

    fun createCylinder(segments: Int = 10): CpuMesh {
        val out = FloatList(segments * 6 * 8)
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * (Math.PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segments) * (Math.PI.toFloat() * 2f)
            val x0 = cos(a0) * 0.5f; val z0 = sin(a0) * 0.5f
            val x1 = cos(a1) * 0.5f; val z1 = sin(a1) * 0.5f
            val u0 = i.toFloat() / segments
            val u1 = (i + 1).toFloat() / segments

            out.add(x0); out.add(0f); out.add(z0); out.add(x0 * 2f); out.add(0f); out.add(z0 * 2f); out.add(u0); out.add(0f)
            out.add(x1); out.add(0f); out.add(z1); out.add(x1 * 2f); out.add(0f); out.add(z1 * 2f); out.add(u1); out.add(0f)
            out.add(x1); out.add(1f); out.add(z1); out.add(x1 * 2f); out.add(0f); out.add(z1 * 2f); out.add(u1); out.add(1f)

            out.add(x0); out.add(0f); out.add(z0); out.add(x0 * 2f); out.add(0f); out.add(z0 * 2f); out.add(u0); out.add(0f)
            out.add(x1); out.add(1f); out.add(z1); out.add(x1 * 2f); out.add(0f); out.add(z1 * 2f); out.add(u1); out.add(1f)
            out.add(x0); out.add(1f); out.add(z0); out.add(x0 * 2f); out.add(0f); out.add(z0 * 2f); out.add(u0); out.add(1f)
        }
        val arr = out.toFloatArray()
        return CpuMesh(arr, arr.size / 8, arr.size / 24)
    }

    fun getAssetByteSize(assets: AssetManager, path: String): Long {
        return try {
            assets.openFd(path).use { it.length }
        } catch (_: Exception) {
            try {
                assets.open(path).use { it.available().toLong() }
            } catch (_: Exception) {
                0L
            }
        }
    }
}
