package com.spiderhub.webverse.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.random.Random

object TextureGenerator {

    fun createTowerFacadeTexture(): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rng = Random(42)

        canvas.drawColor(Color.rgb(16, 20, 34))

        val panelPaint = Paint()
        for (y in 0 until size step 32) {
            for (x in 0 until size step 32) {
                val shade = rng.nextInt(14, 28)
                panelPaint.color = Color.rgb(shade, shade + 4, shade + 14)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 30).toFloat(), (y + 30).toFloat(), panelPaint)

                if (rng.nextFloat() > 0.36f) {
                    val isMagenta = rng.nextFloat() > 0.72f
                    panelPaint.color = if (isMagenta) {
                        Color.rgb(255, 65, 130)
                    } else {
                        Color.rgb(45, 225, 255)
                    }
                    canvas.drawRect((x + 5).toFloat(), (y + 10).toFloat(), (x + 25).toFloat(), (y + 16).toFloat(), panelPaint)
                }
            }
        }
        return uploadTexture(bmp, repeat = true)
    }

    fun createWetAsphaltTexture(): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rng = Random(109)

        canvas.drawColor(Color.rgb(12, 14, 22))
        val paint = Paint()

        for (i in 0 until 2400) {
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            val r = rng.nextFloat() * 4f + 1f
            val v = rng.nextInt(18, 36)
            paint.color = Color.rgb(v, v + 3, v + 12)
            canvas.drawCircle(x, y, r, paint)
        }

        paint.color = Color.argb(140, 0, 229, 255)
        canvas.drawRect(246f, 0f, 266f, size.toFloat(), paint)
        paint.color = Color.argb(90, 255, 46, 99)
        canvas.drawRect(0f, 248f, size.toFloat(), 264f, paint)

        return uploadTexture(bmp, repeat = true)
    }

    fun createInkverseTexture(): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(225, 218, 240))

        val linePaint = Paint().apply {
            color = Color.rgb(24, 14, 38)
            strokeWidth = 3f
        }
        for (i in -size until size * 2 step 16) {
            canvas.drawLine(i.toFloat(), 0f, (i + size).toFloat(), size.toFloat(), linePaint)
        }
        val accentPaint = Paint().apply { color = Color.rgb(255, 46, 99) }
        for (y in 8 until size step 32) {
            for (x in 8 until size step 32) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), 3.5f, accentPaint)
            }
        }
        return uploadTexture(bmp, repeat = true)
    }

    fun createSuitWeaveTexture(): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(22, 26, 42))

        val p = Paint()
        for (y in 0 until size step 16) {
            for (x in 0 until size step 16) {
                p.color = if ((x + y) % 32 == 0) Color.rgb(34, 42, 68) else Color.rgb(18, 20, 32)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 14).toFloat(), (y + 14).toFloat(), p)
            }
        }
        p.color = Color.rgb(0, 240, 255)
        p.strokeWidth = 2f
        canvas.drawLine(0f, 128f, size.toFloat(), 128f, p)
        return uploadTexture(bmp, repeat = true)
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
