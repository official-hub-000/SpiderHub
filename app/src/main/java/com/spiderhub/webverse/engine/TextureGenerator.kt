package com.spiderhub.webverse.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class PbrTextureSet(
    val albedoTex: Int,
    val normalTex: Int,
    val pbrTex: Int // R=Roughness, G=Metallic, B=Emissive
)

object TextureGenerator {

    fun createFlatNormalTexture(): Int {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(128, 128, 255))
        return uploadTexture(bmp, repeat = true)
    }

    fun createDefaultPbrTexture(roughness: Float = 0.5f, metallic: Float = 0.1f, emissive: Float = 0f): Int {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(
            Color.rgb(
                (roughness * 255f).toInt().coerceIn(0, 255),
                (metallic * 255f).toInt().coerceIn(0, 255),
                (emissive * 255f).toInt().coerceIn(0, 255)
            )
        )
        return uploadTexture(bmp, repeat = true)
    }

    /**
     * Sleek Manhattan glass-and-steel skyscraper curtain wall with selective warm/cool lit interiors.
     */
    fun createSkyscraperPbrTextures(): PbrTextureSet {
        val size = 512
        val albedo = IntArray(size * size)
        val height = FloatArray(size * size)
        val pbr = IntArray(size * size)
        val rng = Random(4201)

        val cols = 8
        val rows = 12
        val cellW = size / cols
        val cellH = size / rows

        val cellLit = BooleanArray(cols * rows)
        val cellHue = IntArray(cols * rows)
        for (i in cellLit.indices) {
            cellLit[i] = rng.nextFloat() > 0.68f
            cellHue[i] = when {
                rng.nextFloat() > 0.85f -> 1 // cool cyan office screen
                else -> 0 // warm golden interior light
            }
        }

        for (y in 0 until size) {
            val row = (y / cellH).coerceAtMost(rows - 1)
            val ly = y % cellH
            for (x in 0 until size) {
                val col = (x / cellW).coerceAtMost(cols - 1)
                val lx = x % cellW
                val idx = y * size + x

                val isMullion = lx < 4 || lx >= cellW - 3
                val isSpandrel = ly < 8 || ly >= cellH - 5

                if (isMullion || isSpandrel) {
                    val grain = (sin(x * 0.4f) * 4f).toInt()
                    val base = (48 + grain).coerceIn(30, 68)
                    albedo[idx] = Color.rgb(base, base + 4, base + 12)
                    height[idx] = if (isSpandrel) 0.94f else 0.86f
                    pbr[idx] = Color.rgb(105, 195, 0)
                } else {
                    val cellIdx = row * cols + col
                    val lit = cellLit[cellIdx]
                    val ny = (ly - 8).toFloat() / (cellH - 13).coerceAtLeast(1)
                    val nx = (lx - 4).toFloat() / (cellW - 7).coerceAtLeast(1)
                    val blind = if (((ly / 4) % 2 == 0) && ny < 0.30f) 0.72f else 1.0f

                    if (lit) {
                        val intensity = (0.58f + 0.42f * (1f - abs(nx - 0.5f))) * blind
                        val (r, g, b) = if (cellHue[cellIdx] == 1) {
                            Triple((95 * intensity).toInt(), (215 * intensity).toInt(), (245 * intensity).toInt())
                        } else {
                            Triple((245 * intensity).toInt(), (205 * intensity).toInt(), (145 * intensity).toInt())
                        }
                        albedo[idx] = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                        height[idx] = 0.25f
                        pbr[idx] = Color.rgb(24, 95, (145 * intensity).toInt().coerceIn(0, 255))
                    } else {
                        val skyGrad = (46 + ny * 38).toInt()
                        albedo[idx] = Color.rgb(skyGrad, skyGrad + 14, skyGrad + 34)
                        height[idx] = 0.25f
                        pbr[idx] = Color.rgb(18, 175, 0) // crisp reflective glass
                    }
                }
            }
        }

        return PbrTextureSet(
            albedoTex = uploadTexture(Bitmap.createBitmap(albedo, size, size, Bitmap.Config.ARGB_8888), repeat = true),
            normalTex = uploadTexture(buildNormalMap(height, size, strength = 4.2f), repeat = true),
            pbrTex = uploadTexture(Bitmap.createBitmap(pbr, size, size, Bitmap.Config.ARGB_8888), repeat = true)
        )
    }

    /**
     * Urban asphalt avenue with yellow dashed divider, white lane lines, sidewalks, and subtle rainwater puddles.
     */
    fun createWetAsphaltPbrTextures(): PbrTextureSet {
        val size = 512
        val albedo = IntArray(size * size)
        val height = FloatArray(size * size)
        val pbr = IntArray(size * size)
        val rng = Random(1099)

        for (y in 0 until size) {
            val v = y.toFloat() / size
            for (x in 0 until size) {
                val u = x.toFloat() / size
                val idx = y * size + x

                val wave = sin(u * 12.56f + cos(v * 6.28f) * 1.4f) * cos(v * 12.56f - sin(u * 6.28f))
                val puddle = ((wave + 0.15f) * 1.1f).coerceIn(0f, 1f)
                val grit = (rng.nextFloat() - 0.5f) * (1f - puddle * 0.8f)

                var r = (46 + grit * 22).toInt()
                var g = (48 + grit * 22).toInt()
                var b = (56 + grit * 24).toInt()
                var emissive = 0

                val isCenterDash = abs(u - 0.5f) < 0.012f && (y % 64 < 34)
                val isLaneEdge = abs(u - 0.16f) < 0.010f || abs(u - 0.84f) < 0.010f
                val isCrosswalk = abs(v - 0.5f) < 0.045f && (x % 32 < 16) && u > 0.18f && u < 0.82f

                if (isCenterDash) {
                    r = 235; g = 180; b = 42
                    emissive = 20
                } else if (isLaneEdge || isCrosswalk) {
                    r = 215; g = 220; b = 228
                    emissive = 15
                } else if (puddle > 0.65f) {
                    r = (r * 0.72f).toInt()
                    g = (g * 0.74f).toInt()
                    b = (b * 0.82f).toInt()
                }

                albedo[idx] = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                height[idx] = 0.5f + grit * 0.42f - puddle * 0.2f

                val rough = ((1f - puddle * 0.86f) * 210f + 16f).toInt().coerceIn(14, 240)
                val metal = (puddle * 120f + 15f).toInt().coerceIn(0, 255)
                pbr[idx] = Color.rgb(rough, metal, emissive)
            }
        }

        return PbrTextureSet(
            albedoTex = uploadTexture(Bitmap.createBitmap(albedo, size, size, Bitmap.Config.ARGB_8888), repeat = true),
            normalTex = uploadTexture(buildNormalMap(height, size, strength = 2.6f), repeat = true),
            pbrTex = uploadTexture(Bitmap.createBitmap(pbr, size, size, Bitmap.Config.ARGB_8888), repeat = true)
        )
    }

    /**
     * Iconic Spider-Hero Crimson Red suit texture with raised black webbing & hex carbon micro-weave.
     */
    fun createHeroRedSuitPbrTextures(): PbrTextureSet {
        val size = 512
        val albedo = IntArray(size * size)
        val height = FloatArray(size * size)
        val pbr = IntArray(size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                val hx = (x / 6) % 2
                val hy = (y / 6) % 2
                val weave = if ((hx xor hy) == 0) 1f else 0.82f

                val isWebHoriz = (y % 48) < 3
                val isWebVert = (x % 48) < 3
                if (isWebHoriz || isWebVert) {
                    albedo[idx] = Color.rgb(24, 24, 30)
                    height[idx] = 0.92f
                    pbr[idx] = Color.rgb(75, 175, 0) // glossy dark web lines
                } else {
                    val r = (225 * weave).toInt()
                    val g = (28 * weave).toInt()
                    val b = (42 * weave).toInt()
                    albedo[idx] = Color.rgb(r, g, b)
                    height[idx] = 0.45f + weave * 0.2f
                    pbr[idx] = Color.rgb(if (weave > 0.9f) 95 else 130, 45, 0)
                }
            }
        }

        return PbrTextureSet(
            albedoTex = uploadTexture(Bitmap.createBitmap(albedo, size, size, Bitmap.Config.ARGB_8888), repeat = true),
            normalTex = uploadTexture(buildNormalMap(height, size, strength = 3.2f), repeat = true),
            pbrTex = uploadTexture(Bitmap.createBitmap(pbr, size, size, Bitmap.Config.ARGB_8888), repeat = true)
        )
    }

    /**
     * Cobalt-Blue tactical ballistic fabric texture for Spider-Hero suit panels.
     */
    fun createHeroBlueSuitPbrTextures(): PbrTextureSet {
        val size = 256
        val albedo = IntArray(size * size)
        val height = FloatArray(size * size)
        val pbr = IntArray(size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                val hx = (x / 6) % 2
                val hy = (y / 6) % 2
                val weave = if ((hx xor hy) == 0) 1f else 0.78f
                val isSeam = (x % 64 == 0) || (y % 64 == 0)

                if (isSeam) {
                    albedo[idx] = Color.rgb(18, 28, 56)
                    height[idx] = 0.25f
                    pbr[idx] = Color.rgb(145, 50, 0)
                } else {
                    albedo[idx] = Color.rgb((26 * weave).toInt(), (66 * weave).toInt(), (165 * weave).toInt())
                    height[idx] = 0.5f + weave * 0.2f
                    pbr[idx] = Color.rgb(115, 65, 0)
                }
            }
        }

        return PbrTextureSet(
            albedoTex = uploadTexture(Bitmap.createBitmap(albedo, size, size, Bitmap.Config.ARGB_8888), repeat = true),
            normalTex = uploadTexture(buildNormalMap(height, size, strength = 2.8f), repeat = true),
            pbrTex = uploadTexture(Bitmap.createBitmap(pbr, size, size, Bitmap.Config.ARGB_8888), repeat = true)
        )
    }

    fun createHuskArmorPbrTextures(): PbrTextureSet {
        val size = 256
        val albedo = IntArray(size * size)
        val height = FloatArray(size * size)
        val pbr = IntArray(size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                val vein = sin(x * 0.08f + sin(y * 0.06f) * 2.5f) * cos(y * 0.07f)
                val panel = (x % 32 == 0) || (y % 32 == 0)
                if (vein > 0.74f) {
                    albedo[idx] = Color.rgb(255, 40, 75)
                    height[idx] = 0.85f
                    pbr[idx] = Color.rgb(65, 90, 210)
                } else if (panel) {
                    albedo[idx] = Color.rgb(22, 24, 32)
                    height[idx] = 0.2f
                    pbr[idx] = Color.rgb(140, 190, 0)
                } else {
                    albedo[idx] = Color.rgb(62, 66, 78)
                    height[idx] = 0.65f
                    pbr[idx] = Color.rgb(95, 200, 0)
                }
            }
        }

        return PbrTextureSet(
            albedoTex = uploadTexture(Bitmap.createBitmap(albedo, size, size, Bitmap.Config.ARGB_8888), repeat = true),
            normalTex = uploadTexture(buildNormalMap(height, size, strength = 3.5f), repeat = true),
            pbrTex = uploadTexture(Bitmap.createBitmap(pbr, size, size, Bitmap.Config.ARGB_8888), repeat = true)
        )
    }

    fun createInkversePbrTextures(): PbrTextureSet {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(232, 224, 246))

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 12, 36)
            strokeWidth = 3.2f
        }
        for (i in -size until size * 2 step 18) {
            canvas.drawLine(i.toFloat(), 0f, (i + size).toFloat(), size.toFloat(), linePaint)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 46, 99) }
        for (y in 12 until size step 28) {
            for (x in 12 until size step 28) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), 4.2f, dotPaint)
            }
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 225, 255) }
        for (y in 26 until size step 56) {
            for (x in 26 until size step 56) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), 3.2f, cyanPaint)
            }
        }

        return PbrTextureSet(
            albedoTex = uploadTexture(bmp, repeat = true),
            normalTex = createFlatNormalTexture(),
            pbrTex = createDefaultPbrTexture(roughness = 0.65f, metallic = 0.1f, emissive = 0.12f)
        )
    }

    fun createBillboardTexture(variant: Int): Int {
        val w = 512
        val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bg = Paint()
        bg.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            if (variant == 0) Color.rgb(10, 18, 42) else Color.rgb(34, 8, 28),
            Color.rgb(6, 8, 16),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = when (variant % 3) {
                0 -> Color.rgb(0, 240, 255)
                1 -> Color.rgb(255, 46, 99)
                else -> Color.rgb(255, 196, 64)
            }
        }
        canvas.drawRect(10f, 10f, w - 10f, h - 10f, border)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border.color
            textSize = 70f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }

        val (title, sub) = when (variant % 3) {
            0 -> "NEXUS-9" to "METROPOLIS // SKY-TRANSIT"
            1 -> "WEBVERSE" to "MULTIVERSE CONVERGENCE"
            else -> "OSCORP TECH" to "QUANTUM ENERGY TOWER"
        }
        canvas.drawText(title, w * 0.5f, h * 0.52f, titlePaint)
        canvas.drawText(sub, w * 0.5f, h * 0.76f, subPaint)

        return uploadTexture(bmp, repeat = false)
    }

    private fun buildNormalMap(height: FloatArray, size: Int, strength: Float): Bitmap {
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val ym = (y - 1 + size) % size
            val yp = (y + 1) % size
            for (x in 0 until size) {
                val xm = (x - 1 + size) % size
                val xp = (x + 1) % size

                val dX = (height[y * size + xp] - height[y * size + xm]) * strength
                val dY = (height[yp * size + x] - height[ym * size + x]) * strength
                var nx = -dX
                var ny = -dY
                var nz = 1.0f
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
                nx /= len; ny /= len; nz /= len

                val r = ((nx * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val g = ((ny * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val b = ((nz * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                pixels[y * size + x] = Color.rgb(r, g, b)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun uploadTexture(bitmap: Bitmap, repeat: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)

        val wrap = if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bitmap.recycle()
        return texId
    }
}
