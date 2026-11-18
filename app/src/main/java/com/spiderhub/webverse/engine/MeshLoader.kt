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
 * Interleaved vertex layout (11 floats / 44 bytes per vertex):
 * [px, py, pz, nx, ny, nz, u, v, tx, ty, tz]
 */
class CpuMesh(
    val vertices: FloatArray,
    val vertexCount: Int,
    val triangleCount: Int
)

internal class FloatList(initialCapacity: Int = 2048) {
    private var data = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[size++] = v
    }

    fun addVertex(
        px: Float, py: Float, pz: Float,
        nx: Float, ny: Float, nz: Float,
        u: Float, v: Float,
        tx: Float = 1f, ty: Float = 0f, tz: Float = 0f
    ) {
        add(px); add(py); add(pz)
        add(nx); add(ny); add(nz)
        add(u); add(v)
        add(tx); add(ty); add(tz)
    }

    fun get(index: Int): Float = data[index]

    fun toFloatArray(): FloatArray = data.copyOf(size)
}

object MeshLoader {
    const val FLOATS_PER_VERTEX = 11

    fun loadObjFromAssets(
        assets: AssetManager,
        assetPath: String,
        targetHeight: Float = 2.0f,
        centerBottomOnZero: Boolean = true,
        convertZUpToYUp: Boolean = true
    ): CpuMesh? {
        return try {
            assets.open(assetPath).use { input ->
                loadObjFromStream(input, targetHeight, centerBottomOnZero, convertZUpToYUp)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadObjFromStream(
        input: java.io.InputStream,
        targetHeight: Float = 2.0f,
        centerBottomOnZero: Boolean = true,
        convertZUpToYUp: Boolean = true
    ): CpuMesh? {
        return try {
            val positions = FloatList(2048)
            val normals = FloatList(2048)
            val texCoords = FloatList(2048)
            val outVerts = FloatList(8192)

            BufferedReader(InputStreamReader(input)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        val parts = trimmed.split(Regex("\\s+"))
                        when (parts[0]) {
                            "v" -> if (parts.size >= 4) {
                                val x = parts[1].toFloatOrNull() ?: 0f
                                val y = parts[2].toFloatOrNull() ?: 0f
                                val z = parts[3].toFloatOrNull() ?: 0f
                                if (convertZUpToYUp) {
                                    positions.add(x)
                                    positions.add(z)
                                    positions.add(-y)
                                } else {
                                    positions.add(x)
                                    positions.add(y)
                                    positions.add(z)
                                }
                            }
                            "vn" -> if (parts.size >= 4) {
                                val nx = parts[1].toFloatOrNull() ?: 0f
                                val ny = parts[2].toFloatOrNull() ?: 1f
                                val nz = parts[3].toFloatOrNull() ?: 0f
                                if (convertZUpToYUp) {
                                    normals.add(nx)
                                    normals.add(nz)
                                    normals.add(-ny)
                                } else {
                                    normals.add(nx)
                                    normals.add(ny)
                                    normals.add(nz)
                                }
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

            if (outVerts.size == 0) return null
            val array = outVerts.toFloatArray()
            if (normals.size == 0) {
                computeFlatNormals(array)
            }
            normalizeMesh(array, targetHeight, centerBottomOnZero)
            computeTangents(array)
            val vertCount = array.size / FLOATS_PER_VERTEX
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

        val nx: Float
        val ny: Float
        val nz: Float
        if (vnIdx in 0 until vnCount) {
            nx = normals.get(vnIdx * 3)
            ny = normals.get(vnIdx * 3 + 1)
            nz = normals.get(vnIdx * 3 + 2)
        } else {
            nx = 0f; ny = 1f; nz = 0f
        }

        val u: Float
        val v: Float
        if (vtIdx in 0 until vtCount) {
            u = texCoords.get(vtIdx * 2)
            v = texCoords.get(vtIdx * 2 + 1)
        } else {
            u = px * 0.02f
            v = py * 0.02f
        }

        out.addVertex(px, py, pz, nx, ny, nz, u, v, 1f, 0f, 0f)
    }

    private fun resolveObjIndex(raw: String?, count: Int): Int {
        if (raw.isNullOrEmpty() || count <= 0) return -1
        val parsed = raw.toIntOrNull() ?: return -1
        return if (parsed > 0) parsed - 1 else count + parsed
    }

    fun computeFlatNormals(verts: FloatArray) {
        var i = 0
        val triStride = FLOATS_PER_VERTEX * 3
        while (i + triStride - 1 < verts.size) {
            val ax = verts[i]; val ay = verts[i + 1]; val az = verts[i + 2]
            val bx = verts[i + FLOATS_PER_VERTEX]; val by = verts[i + FLOATS_PER_VERTEX + 1]; val bz = verts[i + FLOATS_PER_VERTEX + 2]
            val cx = verts[i + FLOATS_PER_VERTEX * 2]; val cy = verts[i + FLOATS_PER_VERTEX * 2 + 1]; val cz = verts[i + FLOATS_PER_VERTEX * 2 + 2]

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
                val base = i + k * FLOATS_PER_VERTEX
                verts[base + 3] = nx
                verts[base + 4] = ny
                verts[base + 5] = nz
            }
            i += triStride
        }
    }

    fun computeTangents(verts: FloatArray) {
        var i = 0
        val triStride = FLOATS_PER_VERTEX * 3
        while (i + triStride - 1 < verts.size) {
            val i0 = i
            val i1 = i + FLOATS_PER_VERTEX
            val i2 = i + FLOATS_PER_VERTEX * 2

            val x1 = verts[i1] - verts[i0]
            val y1 = verts[i1 + 1] - verts[i0 + 1]
            val z1 = verts[i1 + 2] - verts[i0 + 2]

            val x2 = verts[i2] - verts[i0]
            val y2 = verts[i2 + 1] - verts[i0 + 1]
            val z2 = verts[i2 + 2] - verts[i0 + 2]

            val u1 = verts[i1 + 6] - verts[i0 + 6]
            val v1 = verts[i1 + 7] - verts[i0 + 7]
            val u2 = verts[i2 + 6] - verts[i0 + 6]
            val v2 = verts[i2 + 7] - verts[i0 + 7]

            val denom = u1 * v2 - u2 * v1
            var tx: Float
            var ty: Float
            var tz: Float
            if (kotlin.math.abs(denom) > 1e-6f) {
                val r = 1f / denom
                tx = (x1 * v2 - x2 * v1) * r
                ty = (y1 * v2 - y2 * v1) * r
                tz = (z1 * v2 - z2 * v1) * r
                val len = sqrt(tx * tx + ty * ty + tz * tz)
                if (len > 1e-5f) {
                    tx /= len; ty /= len; tz /= len
                } else {
                    tx = 1f; ty = 0f; tz = 0f
                }
            } else {
                // Fallback tangent orthogonal to normal
                val nx = verts[i0 + 3]
                val ny = verts[i0 + 4]
                val nz = verts[i0 + 5]
                if (kotlin.math.abs(ny) < 0.9f) {
                    tx = -nz; ty = 0f; tz = nx
                } else {
                    tx = 1f; ty = 0f; tz = 0f
                }
                val len = sqrt(tx * tx + ty * ty + tz * tz)
                if (len > 1e-5f) {
                    tx /= len; ty /= len; tz /= len
                } else {
                    tx = 1f; ty = 0f; tz = 0f
                }
            }

            for (k in 0..2) {
                val base = i + k * FLOATS_PER_VERTEX
                verts[base + 8] = tx
                verts[base + 9] = ty
                verts[base + 10] = tz
            }
            i += triStride
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
            i += FLOATS_PER_VERTEX
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
            i += FLOATS_PER_VERTEX
        }
    }

    fun createGroundQuad(halfExtent: Float, uvScale: Float): CpuMesh {
        val out = FloatList(6 * FLOATS_PER_VERTEX)
        out.addVertex(-halfExtent, 0f, halfExtent, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        out.addVertex(halfExtent, 0f, halfExtent, 0f, 1f, 0f, uvScale, 0f, 1f, 0f, 0f)
        out.addVertex(halfExtent, 0f, -halfExtent, 0f, 1f, 0f, uvScale, uvScale, 1f, 0f, 0f)

        out.addVertex(-halfExtent, 0f, halfExtent, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        out.addVertex(halfExtent, 0f, -halfExtent, 0f, 1f, 0f, uvScale, uvScale, 1f, 0f, 0f)
        out.addVertex(-halfExtent, 0f, -halfExtent, 0f, 1f, 0f, 0f, uvScale, 1f, 0f, 0f)

        val arr = out.toFloatArray()
        return CpuMesh(arr, 6, 2)
    }

    fun createCylinder(segments: Int = 12): CpuMesh {
        val out = FloatList(segments * 6 * FLOATS_PER_VERTEX)
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * (Math.PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segments) * (Math.PI.toFloat() * 2f)
            val x0 = cos(a0) * 0.5f; val z0 = sin(a0) * 0.5f
            val x1 = cos(a1) * 0.5f; val z1 = sin(a1) * 0.5f
            val nx0 = cos(a0); val nz0 = sin(a0)
            val nx1 = cos(a1); val nz1 = sin(a1)
            val u0 = i.toFloat() / segments
            val u1 = (i + 1).toFloat() / segments

            out.addVertex(x0, 0f, z0, nx0, 0f, nz0, u0, 0f)
            out.addVertex(x1, 0f, z1, nx1, 0f, nz1, u1, 0f)
            out.addVertex(x1, 1f, z1, nx1, 0f, nz1, u1, 1f)

            out.addVertex(x0, 0f, z0, nx0, 0f, nz0, u0, 0f)
            out.addVertex(x1, 1f, z1, nx1, 0f, nz1, u1, 1f)
            out.addVertex(x0, 1f, z0, nx0, 0f, nz0, u0, 1f)
        }
        val arr = out.toFloatArray()
        computeTangents(arr)
        return CpuMesh(arr, arr.size / FLOATS_PER_VERTEX, arr.size / (FLOATS_PER_VERTEX * 3))
    }

    fun loadGlbFromAssets(
        assets: AssetManager,
        assetPath: String,
        targetHeight: Float = 2.0f
    ): CpuMesh? {
        return try {
            assets.open(assetPath).use { loadGlbFromStream(it, targetHeight) }
        } catch (_: Exception) {
            null
        }
    }

    fun loadGlbFromStream(
        input: java.io.InputStream,
        targetHeight: Float = 2.0f
    ): CpuMesh? {
        return try {
            val bytes = input.readBytes()
            if (bytes.size < 20) return null

            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buf.getInt(0) != 0x46546C67) return null

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

            val outVerts = FloatList(16384)
            for (m in 0 until meshes.length()) {
                val prims = meshes.getJSONObject(m).optJSONArray("primitives") ?: continue
                for (p in 0 until prims.length()) {
                    val prim = prims.getJSONObject(p)
                    if (prim.optInt("mode", 4) != 4) continue
                    val attrs = prim.optJSONObject("attributes") ?: continue
                    val posAcc = attrs.optInt("POSITION", -1)
                    if (posAcc < 0) continue

                    val posData = readFloatAccessor(accessors, bufferViews, buf, binStart, binEnd, posAcc, 3) ?: continue
                    val normAcc = attrs.optInt("NORMAL", -1)
                    val uvAcc = attrs.optInt("TEXCOORD_0", -1)
                    val normData = if (normAcc >= 0) readFloatAccessor(accessors, bufferViews, buf, binStart, binEnd, normAcc, 3) else null
                    val uvData = if (uvAcc >= 0) readFloatAccessor(accessors, bufferViews, buf, binStart, binEnd, uvAcc, 2) else null

                    val vCount = posData.size / 3
                    val idxAcc = prim.optInt("indices", -1)
                    if (idxAcc >= 0) {
                        val indices = readIndexAccessor(accessors, bufferViews, buf, binStart, binEnd, idxAcc) ?: continue
                        for (idx in indices) {
                            if (idx in 0 until vCount) appendGlbVertex(idx, posData, normData, uvData, outVerts)
                        }
                    } else {
                        for (idx in 0 until vCount) appendGlbVertex(idx, posData, normData, uvData, outVerts)
                    }
                }
            }

            if (outVerts.size == 0) return null
            val array = outVerts.toFloatArray()
            normalizeMesh(array, targetHeight, centerBottomOnZero = true)
            computeTangents(array)
            val vertCount = array.size / FLOATS_PER_VERTEX
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
        val px = pos[idx * 3]; val py = pos[idx * 3 + 1]; val pz = pos[idx * 3 + 2]
        val nx = if (norm != null && idx * 3 + 2 < norm.size) norm[idx * 3] else 0f
        val ny = if (norm != null && idx * 3 + 2 < norm.size) norm[idx * 3 + 1] else 1f
        val nz = if (norm != null && idx * 3 + 2 < norm.size) norm[idx * 3 + 2] else 0f
        val u = if (uv != null && idx * 2 + 1 < uv.size) uv[idx * 2] else px * 0.1f
        val v = if (uv != null && idx * 2 + 1 < uv.size) uv[idx * 2 + 1] else pz * 0.1f
        out.addVertex(px, py, pz, nx, ny, nz, u, v)
    }

    private fun readFloatAccessor(
        accessors: JSONArray, bufferViews: JSONArray, buf: ByteBuffer,
        binStart: Int, binEnd: Int, accIndex: Int, components: Int
    ): FloatArray? {
        val acc = accessors.optJSONObject(accIndex) ?: return null
        val bv = bufferViews.optJSONObject(acc.optInt("bufferView", -1)) ?: return null
        val count = acc.optInt("count", 0)
        val stride = bv.optInt("byteStride", components * 4).coerceAtLeast(components * 4)
        val start = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val result = FloatArray(count * components)
        for (i in 0 until count) {
            val base = start + i * stride
            if (base + components * 4 > binEnd) break
            for (c in 0 until components) result[i * components + c] = buf.getFloat(base + c * 4)
        }
        return result
    }

    private fun readIndexAccessor(
        accessors: JSONArray, bufferViews: JSONArray, buf: ByteBuffer,
        binStart: Int, binEnd: Int, accIndex: Int
    ): IntArray? {
        val acc = accessors.optJSONObject(accIndex) ?: return null
        val bv = bufferViews.optJSONObject(acc.optInt("bufferView", -1)) ?: return null
        val count = acc.optInt("count", 0)
        val compType = acc.optInt("componentType", 5123)
        val start = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val result = IntArray(count)
        when (compType) {
            5121 -> for (i in 0 until count) { if (start + i >= binEnd) break; result[i] = buf.get(start + i).toInt() and 0xFF }
            5123 -> for (i in 0 until count) { if (start + i * 2 + 2 > binEnd) break; result[i] = buf.getShort(start + i * 2).toInt() and 0xFFFF }
            5125 -> for (i in 0 until count) { if (start + i * 4 + 4 > binEnd) break; result[i] = buf.getInt(start + i * 4) }
            else -> return null
        }
        return result
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
