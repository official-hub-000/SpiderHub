package com.spiderhub.webverse.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates smooth-shaded, UV-mapped, tangent-computed 3D meshes for characters,
 * cyber-architecture, vehicles, and props.
 */
object RealisticMeshFactory {

    /**
     * Smooth lathed limb/torso segment oriented from y=0 to y=height.
     */
    fun createSculptedSegment(
        rBottom: Float,
        rMid: Float,
        rTop: Float,
        height: Float,
        zScale: Float = 0.85f,
        segments: Int = 14,
        rings: Int = 6
    ): CpuMesh {
        val out = FloatList(segments * rings * 6 * MeshLoader.FLOATS_PER_VERTEX)
        fun radiusAt(t: Float): Float {
            return if (t < 0.5f) {
                val k = t * 2f
                val s = k * k * (3f - 2f * k)
                rBottom + (rMid - rBottom) * s
            } else {
                val k = (t - 0.5f) * 2f
                val s = k * k * (3f - 2f * k)
                rMid + (rTop - rMid) * s
            }
        }

        for (r in 0 until rings) {
            val t0 = r.toFloat() / rings
            val t1 = (r + 1).toFloat() / rings
            val y0 = t0 * height
            val y1 = t1 * height
            val rad0 = radiusAt(t0)
            val rad1 = radiusAt(t1)

            for (s in 0 until segments) {
                val a0 = (s.toFloat() / segments) * (PI.toFloat() * 2f)
                val a1 = ((s + 1).toFloat() / segments) * (PI.toFloat() * 2f)
                val u0 = s.toFloat() / segments
                val u1 = (s + 1).toFloat() / segments

                val c0 = cos(a0); val s0 = sin(a0)
                val c1 = cos(a1); val s1 = sin(a1)

                val p00x = c0 * rad0; val p00z = s0 * rad0 * zScale
                val p10x = c1 * rad0; val p10z = s1 * rad0 * zScale
                val p01x = c0 * rad1; val p01z = s0 * rad1 * zScale
                val p11x = c1 * rad1; val p11z = s1 * rad1 * zScale

                val slope = (rad0 - rad1) / (height / rings)
                fun norm(ca: Float, sa: Float): FloatArray {
                    val nx = ca
                    val nz = sa / zScale
                    val ny = slope * 0.65f
                    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
                    return floatArrayOf(nx / len, ny / len, nz / len)
                }

                val n0 = norm(c0, s0)
                val n1 = norm(c1, s1)

                out.addVertex(p00x, y0, p00z, n0[0], n0[1], n0[2], u0, t0)
                out.addVertex(p10x, y0, p10z, n1[0], n1[1], n1[2], u1, t0)
                out.addVertex(p11x, y1, p11z, n1[0], n1[1], n1[2], u1, t1)

                out.addVertex(p00x, y0, p00z, n0[0], n0[1], n0[2], u0, t0)
                out.addVertex(p11x, y1, p11z, n1[0], n1[1], n1[2], u1, t1)
                out.addVertex(p01x, y1, p01z, n0[0], n0[1], n0[2], u0, t1)
            }
        }
        val arr = out.toFloatArray()
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Sculpted human/masked head with subtle jawline taper, centered bottom at y=0.
     */
    fun createSculptedHead(rx: Float = 0.145f, ry: Float = 0.18f, rz: Float = 0.165f): CpuMesh {
        val segs = 16
        val rings = 12
        val out = FloatList(segs * rings * 6 * MeshLoader.FLOATS_PER_VERTEX)
        for (r in 0 until rings) {
            val phi0 = -PI.toFloat() * 0.5f + (r.toFloat() / rings) * PI.toFloat()
            val phi1 = -PI.toFloat() * 0.5f + ((r + 1).toFloat() / rings) * PI.toFloat()
            val v0 = r.toFloat() / rings
            val v1 = (r + 1).toFloat() / rings

            for (s in 0 until segs) {
                val th0 = (s.toFloat() / segs) * PI.toFloat() * 2f
                val th1 = ((s + 1).toFloat() / segs) * PI.toFloat() * 2f
                val u0 = s.toFloat() / segs
                val u1 = (s + 1).toFloat() / segs

                fun pt(phi: Float, th: Float): FloatArray {
                    val cp = cos(phi); val sp = sin(phi)
                    val ct = cos(th); val st = sin(th)
                    // Subtle chin taper on lower front
                    val jaw = if (sp < 0f) 1f + sp * 0.22f else 1f
                    val px = cp * ct * rx * jaw
                    val py = (sp + 1f) * ry
                    val pz = cp * st * rz * (if (st < 0f && sp < 0.1f) 1.08f else 1f)
                    val nx = cp * ct
                    val ny = sp
                    val nz = cp * st
                    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
                    return floatArrayOf(px, py, pz, nx / len, ny / len, nz / len)
                }

                val a = pt(phi0, th0)
                val b = pt(phi0, th1)
                val c = pt(phi1, th1)
                val d = pt(phi1, th0)

                out.addVertex(a[0], a[1], a[2], a[3], a[4], a[5], u0, v0)
                out.addVertex(b[0], b[1], b[2], b[3], b[4], b[5], u1, v0)
                out.addVertex(c[0], c[1], c[2], c[3], c[4], c[5], u1, v1)

                out.addVertex(a[0], a[1], a[2], a[3], a[4], a[5], u0, v0)
                out.addVertex(c[0], c[1], c[2], c[3], c[4], c[5], u1, v1)
                out.addVertex(d[0], d[1], d[2], d[3], d[4], d[5], u0, v1)
            }
        }
        val arr = out.toFloatArray()
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Expressive angular Spider-style visor lenses facing -Z.
     */
    fun createHeroVisor(): CpuMesh {
        val out = FloatList(12 * 3 * MeshLoader.FLOATS_PER_VERTEX)
        fun addEye(sign: Float) {
            val innerX = sign * 0.025f
            val outerX = sign * 0.118f
            val midX = sign * 0.072f
            val yBot = 0.145f
            val yTop = 0.235f
            val yMid = 0.185f
            val zFront = -0.172f
            val zSide = -0.125f

            fun tri(p0: FloatArray, p1: FloatArray, p2: FloatArray) {
                out.addVertex(p0[0], p0[1], p0[2], 0f, 0.1f, -1f, 0f, 0f)
                out.addVertex(p1[0], p1[1], p1[2], 0f, 0.1f, -1f, 1f, 0f)
                out.addVertex(p2[0], p2[1], p2[2], 0f, 0.1f, -1f, 0.5f, 1f)
            }
            val pInner = floatArrayOf(innerX, yBot + 0.01f, zFront)
            val pTop = floatArrayOf(outerX, yTop, zSide)
            val pOuter = floatArrayOf(outerX * 0.94f, yMid, zSide)
            val pMid = floatArrayOf(midX, yBot, zFront * 0.96f)

            if (sign > 0f) {
                tri(pInner, pMid, pTop)
                tri(pMid, pOuter, pTop)
            } else {
                tri(pInner, pTop, pMid)
                tri(pMid, pTop, pOuter)
            }
        }
        addEye(-1f)
        addEye(1f)
        val arr = out.toFloatArray()
        MeshLoader.computeFlatNormals(arr)
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Beveled Unit Building Box with proper per-face UVs scaled cleanly.
     */
    fun createDetailedBuildingBox(): CpuMesh {
        val out = FloatList(36 * MeshLoader.FLOATS_PER_VERTEX)
        fun quad(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dx: Float, dy: Float, dz: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            out.addVertex(ax, ay, az, nx, ny, nz, 0f, 0f)
            out.addVertex(bx, by, bz, nx, ny, nz, 1f, 0f)
            out.addVertex(cx, cy, cz, nx, ny, nz, 1f, 1f)
            out.addVertex(ax, ay, az, nx, ny, nz, 0f, 0f)
            out.addVertex(cx, cy, cz, nx, ny, nz, 1f, 1f)
            out.addVertex(dx, dy, dz, nx, ny, nz, 0f, 1f)
        }
        // Front (+Z)
        quad(-0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f, 0.5f, 1f, 0.5f, -0.5f, 1f, 0.5f, 0f, 0f, 1f)
        // Back (-Z)
        quad(0.5f, 0f, -0.5f, -0.5f, 0f, -0.5f, -0.5f, 1f, -0.5f, 0.5f, 1f, -0.5f, 0f, 0f, -1f)
        // Left (-X)
        quad(-0.5f, 0f, -0.5f, -0.5f, 0f, 0.5f, -0.5f, 1f, 0.5f, -0.5f, 1f, -0.5f, -1f, 0f, 0f)
        // Right (+X)
        quad(0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 1f, -0.5f, 0.5f, 1f, 0.5f, 1f, 0f, 0f)
        // Top (+Y)
        quad(-0.5f, 1f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 1f, -0.5f, -0.5f, 1f, -0.5f, 0f, 1f, 0f)

        val arr = out.toFloatArray()
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Sleek cyberpunk hover-cruiser mesh facing -Z.
     */
    fun createHoverCar(): CpuMesh {
        val out = FloatList(256 * MeshLoader.FLOATS_PER_VERTEX)
        fun addBox(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float) {
            val hx = sx * 0.5f; val hy = sy * 0.5f; val hz = sz * 0.5f
            fun q(
                ax: Float, ay: Float, az: Float,
                bx: Float, by: Float, bz: Float,
                ex: Float, ey: Float, ez: Float,
                dx: Float, dy: Float, dz: Float,
                nx: Float, ny: Float, nz: Float
            ) {
                out.addVertex(cx + ax, cy + ay, cz + az, nx, ny, nz, 0f, 0f)
                out.addVertex(cx + bx, cy + by, cz + bz, nx, ny, nz, 1f, 0f)
                out.addVertex(cx + ex, cy + ey, cz + ez, nx, ny, nz, 1f, 1f)
                out.addVertex(cx + ax, cy + ay, cz + az, nx, ny, nz, 0f, 0f)
                out.addVertex(cx + ex, cy + ey, cz + ez, nx, ny, nz, 1f, 1f)
                out.addVertex(cx + dx, cy + dy, cz + dz, nx, ny, nz, 0f, 1f)
            }
            q(-hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz, 0f, 0f, 1f)
            q(hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, 0f, 0f, -1f)
            q(-hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz, -1f, 0f, 0f)
            q(hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, 1f, 0f, 0f)
            q(-hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz, 0f, 1f, 0f)
            q(-hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, 0f, -1f, 0f)
        }
        // Main aerodynamic fuselage
        addBox(0f, 0f, 0f, 1.9f, 0.65f, 4.6f)
        // Sleek cabin canopy
        addBox(0f, 0.52f, 0.2f, 1.35f, 0.48f, 2.1f)
        // Left & Right thruster nacelles
        addBox(-1.15f, -0.05f, 0.5f, 0.45f, 0.42f, 3.2f)
        addBox(1.15f, -0.05f, 0.5f, 0.45f, 0.42f, 3.2f)
        // Rear spoiler wing
        addBox(0f, 0.55f, 2.0f, 2.4f, 0.12f, 0.55f)

        val arr = out.toFloatArray()
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Multi-faceted beveled energy crystal for Chrono-Shards.
     */
    fun createCrystalShard(radius: Float = 0.68f, height: Float = 1.6f): CpuMesh {
        val segs = 6
        val out = FloatList(segs * 6 * 3 * MeshLoader.FLOATS_PER_VERTEX)
        val topY = height * 0.5f
        val botY = -height * 0.5f
        val midTopY = height * 0.18f
        val midBotY = -height * 0.18f

        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * (PI.toFloat() * 2f)
            val a1 = ((i + 1).toFloat() / segs) * (PI.toFloat() * 2f)
            val x0 = cos(a0) * radius; val z0 = sin(a0) * radius
            val x1 = cos(a1) * radius; val z1 = sin(a1) * radius
            val x0s = x0 * 0.65f; val z0s = z0 * 0.65f
            val x1s = x1 * 0.65f; val z1s = z1 * 0.65f

            // Top pyramid facet
            out.addVertex(0f, topY, 0f, 0f, 1f, 0f, 0.5f, 1f)
            out.addVertex(x1s, midTopY, z1s, cos(a1), 0.4f, sin(a1), 1f, 0.5f)
            out.addVertex(x0s, midTopY, z0s, cos(a0), 0.4f, sin(a0), 0f, 0.5f)

            // Center bevel belt
            out.addVertex(x0s, midTopY, z0s, cos(a0), 0.2f, sin(a0), 0f, 0.7f)
            out.addVertex(x1s, midTopY, z1s, cos(a1), 0.2f, sin(a1), 1f, 0.7f)
            out.addVertex(x1, midBotY, z1, cos(a1), -0.2f, sin(a1), 1f, 0.3f)

            out.addVertex(x0s, midTopY, z0s, cos(a0), 0.2f, sin(a0), 0f, 0.7f)
            out.addVertex(x1, midBotY, z1, cos(a1), -0.2f, sin(a1), 1f, 0.3f)
            out.addVertex(x0, midBotY, z0, cos(a0), -0.2f, sin(a0), 0f, 0.3f)

            // Bottom inverted pyramid facet
            out.addVertex(x0, midBotY, z0, cos(a0), -0.4f, sin(a0), 0f, 0.5f)
            out.addVertex(x1, midBotY, z1, cos(a1), -0.4f, sin(a1), 1f, 0.5f)
            out.addVertex(0f, botY, 0f, 0f, -1f, 0f, 0.5f, 0f)
        }
        val arr = out.toFloatArray()
        MeshLoader.computeFlatNormals(arr)
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Torus ring used for Stargate Portal, Chrono-Shard gyro rings, and Warden shields.
     */
    fun createTorusRing(majorR: Float = 1f, minorR: Float = 0.09f, majorSegs: Int = 32, minorSegs: Int = 10): CpuMesh {
        val out = FloatList(majorSegs * minorSegs * 6 * MeshLoader.FLOATS_PER_VERTEX)
        for (i in 0 until majorSegs) {
            val u0 = i.toFloat() / majorSegs
            val u1 = (i + 1).toFloat() / majorSegs
            val a0 = u0 * PI.toFloat() * 2f
            val a1 = u1 * PI.toFloat() * 2f

            for (j in 0 until minorSegs) {
                val v0 = j.toFloat() / minorSegs
                val v1 = (j + 1).toFloat() / minorSegs
                val b0 = v0 * PI.toFloat() * 2f
                val b1 = v1 * PI.toFloat() * 2f

                fun pt(a: Float, b: Float): FloatArray {
                    val ca = cos(a); val sa = sin(a)
                    val cb = cos(b); val sb = sin(b)
                    val r = majorR + minorR * cb
                    val px = r * ca
                    val py = r * sa
                    val pz = minorR * sb
                    val nx = cb * ca
                    val ny = cb * sa
                    val nz = sb
                    return floatArrayOf(px, py, pz, nx, ny, nz)
                }

                val p00 = pt(a0, b0)
                val p10 = pt(a1, b0)
                val p11 = pt(a1, b1)
                val p01 = pt(a0, b1)

                out.addVertex(p00[0], p00[1], p00[2], p00[3], p00[4], p00[5], u0, v0)
                out.addVertex(p10[0], p10[1], p10[2], p10[3], p10[4], p10[5], u1, v0)
                out.addVertex(p11[0], p11[1], p11[2], p11[3], p11[4], p11[5], u1, v1)

                out.addVertex(p00[0], p00[1], p00[2], p00[3], p00[4], p00[5], u0, v0)
                out.addVertex(p11[0], p11[1], p11[2], p11[3], p11[4], p11[5], u1, v1)
                out.addVertex(p01[0], p01[1], p01[2], p01[3], p01[4], p01[5], u0, v1)
            }
        }
        val arr = out.toFloatArray()
        MeshLoader.computeTangents(arr)
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Inverted sky dome sphere for atmospheric gradient & horizon.
     */
    fun createSkyDome(radius: Float = 320f): CpuMesh {
        val segs = 24
        val rings = 12
        val out = FloatList(segs * rings * 6 * MeshLoader.FLOATS_PER_VERTEX)
        for (r in 0 until rings) {
            val phi0 = -0.15f + (r.toFloat() / rings) * (PI.toFloat() * 0.5f + 0.15f)
            val phi1 = -0.15f + ((r + 1).toFloat() / rings) * (PI.toFloat() * 0.5f + 0.15f)
            val v0 = r.toFloat() / rings
            val v1 = (r + 1).toFloat() / rings

            for (s in 0 until segs) {
                val th0 = (s.toFloat() / segs) * PI.toFloat() * 2f
                val th1 = ((s + 1).toFloat() / segs) * PI.toFloat() * 2f
                val u0 = s.toFloat() / segs
                val u1 = (s + 1).toFloat() / segs

                fun pt(phi: Float, th: Float): FloatArray {
                    val cp = cos(phi); val sp = sin(phi)
                    val ct = cos(th); val st = sin(th)
                    return floatArrayOf(cp * ct * radius, sp * radius, cp * st * radius, -cp * ct, -sp, -cp * st)
                }

                val a = pt(phi0, th0); val b = pt(phi0, th1); val c = pt(phi1, th1); val d = pt(phi1, th0)
                // Winding inward
                out.addVertex(a[0], a[1], a[2], a[3], a[4], a[5], u0, v0)
                out.addVertex(c[0], c[1], c[2], c[3], c[4], c[5], u1, v1)
                out.addVertex(b[0], b[1], b[2], b[3], b[4], b[5], u1, v0)

                out.addVertex(a[0], a[1], a[2], a[3], a[4], a[5], u0, v0)
                out.addVertex(d[0], d[1], d[2], d[3], d[4], d[5], u0, v1)
                out.addVertex(c[0], c[1], c[2], c[3], c[4], c[5], u1, v1)
            }
        }
        val arr = out.toFloatArray()
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }

    /**
     * Flat disc (for portal vortex, contact shadows, holographic pads).
     */
    fun createDisc(segments: Int = 28): CpuMesh {
        val out = FloatList(segments * 3 * MeshLoader.FLOATS_PER_VERTEX)
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * PI.toFloat() * 2f
            val a1 = ((i + 1).toFloat() / segments) * PI.toFloat() * 2f
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            out.addVertex(0f, 0f, 0f, 0f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0f)
            out.addVertex(c0, s0, 0f, 0f, 0f, 1f, 0.5f + c0 * 0.5f, 0.5f + s0 * 0.5f, 1f, 0f, 0f)
            out.addVertex(c1, s1, 0f, 0f, 0f, 1f, 0.5f + c1 * 0.5f, 0.5f + s1 * 0.5f, 1f, 0f, 0f)
        }
        val arr = out.toFloatArray()
        return CpuMesh(arr, arr.size / MeshLoader.FLOATS_PER_VERTEX, arr.size / (MeshLoader.FLOATS_PER_VERTEX * 3))
    }
}
