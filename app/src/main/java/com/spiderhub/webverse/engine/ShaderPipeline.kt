package com.spiderhub.webverse.engine

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages PBR Normal-Mapped shading, procedural SkyDome rendering, and
 * Multi-pass FBO Bloom + ACES Filmic Tone Mapping + Chromatic Aberration post-processing.
 */
class ShaderPipeline {

    var pbrProgram = 0
        private set
    var skyProgram = 0
        private set
    private var brightProgram = 0
    private var blurProgram = 0
    private var compositeProgram = 0

    // PBR Uniforms
    var uMvp = -1; private set
    var uModel = -1; private set
    var uCamPos = -1; private set
    var uColor = -1; private set
    var uEmissive = -1; private set
    var uFogColor = -1; private set
    var uWorldMode = -1; private set
    var uUvScale = -1; private set
    var uTime = -1; private set
    var uAlbedoTex = -1; private set
    var uNormalTex = -1; private set
    var uPbrTex = -1; private set
    var uPointLight0 = -1; private set
    var uPointColor0 = -1; private set
    var uPointLight1 = -1; private set
    var uPointColor1 = -1; private set

    // Sky Uniforms
    var uSkyVp = -1; private set
    var uSkyCamPos = -1; private set
    var uSkyWorldMode = -1; private set
    var uSkyTime = -1; private set

    // FBOs for HDR-like Bloom
    private var screenW = 1
    private var screenH = 1
    private var bloomW = 1
    private var bloomH = 1
    private var sceneFbo = 0
    private var sceneColorTex = 0
    private var sceneDepthRb = 0
    private var bloomFboA = 0
    private var bloomTexA = 0
    private var bloomFboB = 0
    private var bloomTexB = 0
    private var fboReady = false

    private var quadVbo = 0

    fun init() {
        pbrProgram = createProgram(PBR_VS, PBR_FS)
        uMvp = GLES20.glGetUniformLocation(pbrProgram, "uMvp")
        uModel = GLES20.glGetUniformLocation(pbrProgram, "uModel")
        uCamPos = GLES20.glGetUniformLocation(pbrProgram, "uCamPos")
        uColor = GLES20.glGetUniformLocation(pbrProgram, "uColor")
        uEmissive = GLES20.glGetUniformLocation(pbrProgram, "uEmissive")
        uFogColor = GLES20.glGetUniformLocation(pbrProgram, "uFogColor")
        uWorldMode = GLES20.glGetUniformLocation(pbrProgram, "uWorldMode")
        uUvScale = GLES20.glGetUniformLocation(pbrProgram, "uUvScale")
        uTime = GLES20.glGetUniformLocation(pbrProgram, "uTime")
        uAlbedoTex = GLES20.glGetUniformLocation(pbrProgram, "uAlbedoTex")
        uNormalTex = GLES20.glGetUniformLocation(pbrProgram, "uNormalTex")
        uPbrTex = GLES20.glGetUniformLocation(pbrProgram, "uPbrTex")
        uPointLight0 = GLES20.glGetUniformLocation(pbrProgram, "uPointLight0")
        uPointColor0 = GLES20.glGetUniformLocation(pbrProgram, "uPointColor0")
        uPointLight1 = GLES20.glGetUniformLocation(pbrProgram, "uPointLight1")
        uPointColor1 = GLES20.glGetUniformLocation(pbrProgram, "uPointColor1")

        skyProgram = createProgram(SKY_VS, SKY_FS)
        uSkyVp = GLES20.glGetUniformLocation(skyProgram, "uVp")
        uSkyCamPos = GLES20.glGetUniformLocation(skyProgram, "uCamPos")
        uSkyWorldMode = GLES20.glGetUniformLocation(skyProgram, "uWorldMode")
        uSkyTime = GLES20.glGetUniformLocation(skyProgram, "uTime")

        brightProgram = createProgram(QUAD_VS, BRIGHT_FS)
        blurProgram = createProgram(QUAD_VS, BLUR_FS)
        compositeProgram = createProgram(QUAD_VS, COMPOSITE_FS)

        val quad = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            1f, 1f, 1f, 1f,
            -1f, -1f, 0f, 0f,
            1f, 1f, 1f, 1f,
            -1f, 1f, 0f, 1f
        )
        val quadBuf = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quad)
        quadBuf.position(0)

        val vbos = IntArray(1)
        GLES20.glGenBuffers(1, vbos, 0)
        quadVbo = vbos[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.size * 4, quadBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun resize(width: Int, height: Int) {
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)
        bloomW = (screenW / 3).coerceAtLeast(64)
        bloomH = (screenH / 3).coerceAtLeast(64)
        rebuildFbos()
    }

    private fun rebuildFbos() {
        fboReady = false
        val oldFbos = intArrayOf(sceneFbo, bloomFboA, bloomFboB)
        GLES20.glDeleteFramebuffers(3, oldFbos, 0)
        val oldTexs = intArrayOf(sceneColorTex, bloomTexA, bloomTexB)
        GLES20.glDeleteTextures(3, oldTexs, 0)
        if (sceneDepthRb != 0) {
            GLES20.glDeleteRenderbuffers(1, intArrayOf(sceneDepthRb), 0)
        }

        val fbos = IntArray(3)
        val texs = IntArray(3)
        val rbs = IntArray(1)
        GLES20.glGenFramebuffers(3, fbos, 0)
        GLES20.glGenTextures(3, texs, 0)
        GLES20.glGenRenderbuffers(1, rbs, 0)

        sceneFbo = fbos[0]; sceneColorTex = texs[0]; sceneDepthRb = rbs[0]
        setupColorTexture(sceneColorTex, screenW, screenH)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, sceneDepthRb)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, screenW, screenH)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, sceneColorTex, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, sceneDepthRb)
        val sceneOk = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE

        bloomFboA = fbos[1]; bloomTexA = texs[1]
        setupColorTexture(bloomTexA, bloomW, bloomH)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomFboA)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, bloomTexA, 0)
        val bloomAOk = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE

        bloomFboB = fbos[2]; bloomTexB = texs[2]
        setupColorTexture(bloomTexB, bloomW, bloomH)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomFboB)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, bloomTexB, 0)
        val bloomBOk = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        fboReady = sceneOk && bloomAOk && bloomBOk
    }

    private fun setupColorTexture(texId: Int, w: Int, h: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun beginScenePass(enableBloom: Boolean) {
        if (enableBloom && fboReady && sceneFbo != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFbo)
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
        GLES20.glViewport(0, 0, screenW, screenH)
    }

    fun finishPostProcessPass(enableBloom: Boolean, speedIntensity: Float, worldMode: Float) {
        if (!enableBloom || !fboReady || sceneFbo == 0) return

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // 1. Bright-pass extract into Bloom A
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomFboA)
        GLES20.glViewport(0, 0, bloomW, bloomH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(brightProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneColorTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(brightProgram, "uTex"), 0)
        drawQuad()

        // 2. Horizontal Gaussian blur Bloom A -> Bloom B
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomFboB)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(blurProgram)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTexA)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(blurProgram, "uTex"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(blurProgram, "uStep"), 1.6f / bloomW, 0f)
        drawQuad()

        // 3. Vertical Gaussian blur Bloom B -> Bloom A
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomFboA)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTexB)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(blurProgram, "uStep"), 0f, 1.6f / bloomH)
        drawQuad()

        // 4. Composite Scene + Bloom + ACES Filmic + Chromatic Aberration -> Screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, screenW, screenH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(compositeProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneColorTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "uScene"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTexA)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "uBloom"), 1)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(compositeProgram, "uSpeedFx"), speedIntensity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(compositeProgram, "uWorldMode"), worldMode)
        drawQuad()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 16, 8)
        GLES20.glDisableVertexAttribArray(2)
        GLES20.glDisableVertexAttribArray(3)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        if (vsSrc.contains("aPos")) {
            GLES20.glBindAttribLocation(prog, 0, "aPos")
            GLES20.glBindAttribLocation(prog, 1, "aNorm")
            GLES20.glBindAttribLocation(prog, 2, "aUv")
            GLES20.glBindAttribLocation(prog, 3, "aTangent")
        } else {
            GLES20.glBindAttribLocation(prog, 0, "aQuadPos")
            GLES20.glBindAttribLocation(prog, 1, "aQuadUv")
        }
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("ShaderPipeline", "Shader error: ${GLES20.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    companion object {
        private const val PBR_VS = """
attribute vec3 aPos;
attribute vec3 aNorm;
attribute vec2 aUv;
attribute vec3 aTangent;

uniform mat4 uMvp;
uniform mat4 uModel;
uniform float uUvScale;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec3 vTangent;
varying vec3 vBitangent;
varying vec2 vUv;

void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vWorldPos = wp.xyz;
    vec3 N = normalize((uModel * vec4(aNorm, 0.0)).xyz);
    vec3 T = normalize((uModel * vec4(aTangent, 0.0)).xyz);
    T = normalize(T - dot(T, N) * N);
    vNormal = N;
    vTangent = T;
    vBitangent = cross(N, T);
    vUv = aUv * uUvScale;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
"""

        private const val PBR_FS = """
precision mediump float;

varying vec3 vWorldPos;
varying vec3 vNormal;
varying vec3 vTangent;
varying vec3 vBitangent;
varying vec2 vUv;

uniform sampler2D uAlbedoTex;
uniform sampler2D uNormalTex;
uniform sampler2D uPbrTex;

uniform vec3 uCamPos;
uniform vec3 uColor;
uniform float uEmissive;
uniform vec3 uFogColor;
uniform float uWorldMode; // 0 = Golden Dusk, 1 = Neon-Noir, 2 = Inkverse
uniform float uTime;

uniform vec3 uPointLight0;
uniform vec3 uPointColor0;
uniform vec3 uPointLight1;
uniform vec3 uPointColor1;

vec3 sampleEnvReflection(vec3 R, float rough, float mode) {
    float horizon = clamp(R.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 skyCol;
    vec3 bandCol;
    if (mode < 0.5) {
        skyCol = mix(vec3(1.0, 0.52, 0.24), vec3(0.16, 0.38, 0.78), horizon);
        bandCol = vec3(1.0, 0.76, 0.36);
    } else if (mode < 1.5) {
        skyCol = mix(vec3(0.04, 0.16, 0.32), vec3(0.02, 0.04, 0.10), horizon);
        bandCol = vec3(0.0, 0.75, 1.0);
    } else {
        skyCol = mix(vec3(0.95, 0.18, 0.45), vec3(0.18, 0.06, 0.35), horizon);
        bandCol = vec3(0.0, 0.9, 1.0);
    }
    float neonBand = exp(-abs(R.y) * 8.0) * (1.0 - rough * 0.7);
    return skyCol + bandCol * neonBand * 0.55;
}

float ggxSpecular(vec3 N, vec3 V, vec3 L, float rough) {
    vec3 H = normalize(L + V);
    float ndoth = max(dot(N, H), 0.0);
    float r2 = rough * rough;
    float a2 = r2 * r2;
    float denom = ndoth * ndoth * (a2 - 1.0) + 1.0;
    return a2 / max(3.14159 * denom * denom, 0.001);
}

void main() {
    vec3 albedoSample = texture2D(uAlbedoTex, vUv).rgb;
    vec3 normalSample = texture2D(uNormalTex, vUv).xyz * 2.0 - 1.0;
    vec3 pbrSample = texture2D(uPbrTex, vUv).rgb;

    float roughness = clamp(pbrSample.r, 0.06, 0.98);
    float metallic = clamp(pbrSample.g, 0.0, 1.0);
    float texEmissive = pbrSample.b;

    vec3 N = normalize(vTangent * normalSample.x + vBitangent * normalSample.y + vNormal * normalSample.z);
    vec3 V = normalize(uCamPos - vWorldPos);
    vec3 L = uWorldMode < 0.5
        ? normalize(vec3(0.55, 0.52, -0.65))
        : normalize(vec3(0.45, 0.84, 0.32));

    vec3 baseAlbedo = uColor * albedoSample;
    float ndotl = max(dot(N, L), 0.0);

    if (uWorldMode > 1.5) {
        ndotl = ndotl > 0.60 ? 1.0 : (ndotl > 0.25 ? 0.52 : 0.20);
    }

    float hemi = N.y * 0.5 + 0.5;
    vec3 amb;
    vec3 sunCol;
    vec3 rimCol;
    if (uWorldMode < 0.5) {
        // Golden Sunset Manhattan look: bright sky fill + warm amber sun
        amb = mix(vec3(0.24, 0.22, 0.28), vec3(0.38, 0.50, 0.72), hemi);
        sunCol = vec3(1.22, 0.94, 0.68);
        rimCol = vec3(1.0, 0.72, 0.35);
    } else if (uWorldMode < 1.5) {
        amb = mix(vec3(0.14, 0.06, 0.16), vec3(0.14, 0.24, 0.38), hemi);
        sunCol = vec3(0.85, 0.92, 1.08);
        rimCol = vec3(0.0, 0.92, 1.0);
    } else {
        amb = mix(vec3(0.22, 0.12, 0.26), vec3(0.24, 0.22, 0.42), hemi);
        sunCol = vec3(1.05, 0.95, 1.05);
        rimCol = vec3(1.0, 0.18, 0.44);
    }

    float specD = ggxSpecular(N, V, L, roughness);
    vec3 F0 = mix(vec3(0.04), baseAlbedo, metallic);
    float fresnel = pow(1.0 - max(dot(N, V), 0.0), 4.0);
    vec3 F = F0 + (vec3(1.0) - F0) * fresnel;

    vec3 diffuse = baseAlbedo * (1.0 - metallic * 0.7) * (amb + sunCol * ndotl);
    vec3 specTerm = F * specD * ndotl * sunCol * 0.55;

    vec3 R = reflect(-V, N);
    vec3 envRefl = sampleEnvReflection(R, roughness, uWorldMode) * (1.0 - roughness) * (0.30 + metallic * 0.55);

    vec3 d0 = uPointLight0 - vWorldPos;
    float dist0 = length(d0);
    vec3 l0 = d0 / max(dist0, 0.01);
    float att0 = 1.0 / (1.0 + 0.028 * dist0 + 0.004 * dist0 * dist0);
    float p0diff = max(dot(N, l0), 0.0) * att0;

    vec3 d1 = uPointLight1 - vWorldPos;
    float dist1 = length(d1);
    vec3 l1 = d1 / max(dist1, 0.01);
    float att1 = 1.0 / (1.0 + 0.015 * dist1 + 0.0015 * dist1 * dist1);
    float p1diff = max(dot(N, l1), 0.0) * att1;

    vec3 rimGlow = rimCol * fresnel * (uWorldMode > 1.5 ? 0.90 : 0.34);
    vec3 emissiveGlow = baseAlbedo * (uEmissive + texEmissive * (uWorldMode < 0.5 ? 0.55 : 1.35));

    vec3 lit = diffuse + specTerm + envRefl + baseAlbedo * (uPointColor0 * p0diff + uPointColor1 * p1diff) + rimGlow + emissiveGlow;

    float camDist = length(uCamPos - vWorldPos);
    float heightFade = clamp(1.0 - vWorldPos.y * 0.005, 0.35, 1.0);
    float fogFactor = clamp((camDist - 65.0) / 210.0, 0.0, 0.88) * heightFade;
    vec3 finalCol = mix(lit, uFogColor, fogFactor);

    gl_FragColor = vec4(finalCol, 1.0);
}
"""

        private const val SKY_VS = """
attribute vec3 aPos;
attribute vec3 aNorm;
attribute vec2 aUv;
attribute vec3 aTangent;

uniform mat4 uVp;
uniform vec3 uCamPos;

varying vec3 vDir;

void main() {
    vDir = normalize(aPos);
    vec3 wp = aPos + uCamPos;
    gl_Position = uVp * vec4(wp, 1.0);
}
"""

        private const val SKY_FS = """
precision mediump float;
varying vec3 vDir;
uniform float uWorldMode;
uniform float uTime;

void main() {
    vec3 d = normalize(vDir);
    float h = clamp(d.y, -0.1, 1.0);

    vec3 horizon;
    vec3 zenith;
    vec3 sunDir;
    vec3 sunCol;

    if (uWorldMode < 0.5) {
        // Golden Sunset Manhattan Sky
        horizon = vec3(0.95, 0.50, 0.26);
        zenith = vec3(0.11, 0.28, 0.62);
        sunDir = normalize(vec3(0.55, 0.24, -0.79));
        sunCol = vec3(1.0, 0.72, 0.32);
    } else if (uWorldMode < 1.5) {
        horizon = vec3(0.04, 0.14, 0.28);
        zenith = vec3(0.01, 0.02, 0.08);
        sunDir = normalize(vec3(0.45, 0.42, -0.78));
        sunCol = vec3(0.15, 0.92, 1.0);
    } else {
        horizon = vec3(0.68, 0.12, 0.40);
        zenith = vec3(0.10, 0.04, 0.22);
        sunDir = normalize(vec3(0.45, 0.42, -0.78));
        sunCol = vec3(1.0, 0.25, 0.55);
    }

    vec3 col = mix(horizon, zenith, pow(clamp(h, 0.0, 1.0), 0.55));
    float sunDot = max(dot(d, sunDir), 0.0);
    float sunCore = smoothstep(0.994, 0.997, sunDot);
    float sunHalo = pow(sunDot, 12.0);
    col += sunCol * sunHalo * 0.65 + vec3(1.0, 0.97, 0.92) * sunCore * 1.4;

    // Soft clouds / atmospheric glow bands in Golden Dusk, stars in Night
    if (uWorldMode < 0.5) {
        float cloud = sin(d.x * 9.0 + uTime * 0.04) * cos(d.z * 7.0 - uTime * 0.03);
        float cloudMask = smoothstep(0.15, 0.65, cloud) * smoothstep(0.05, 0.35, h) * (1.0 - smoothstep(0.35, 0.75, h));
        col = mix(col, vec3(1.0, 0.76, 0.52), cloudMask * 0.28);
    } else {
        float starHash = fract(sin(dot(floor(d * 140.0), vec3(12.9898, 78.233, 37.719))) * 43758.5453);
        if (starHash > 0.993 && h > 0.1) {
            float twinkle = 0.6 + 0.4 * sin(uTime * 3.0 + starHash * 100.0);
            col += vec3(0.7, 0.9, 1.0) * twinkle;
        }
    }

    gl_FragColor = vec4(col, 1.0);
}
"""

        private const val QUAD_VS = """
attribute vec2 aQuadPos;
attribute vec2 aQuadUv;
varying vec2 vUv;
void main() {
    vUv = aQuadUv;
    gl_Position = vec4(aQuadPos, 0.0, 1.0);
}
"""

        private const val BRIGHT_FS = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uTex;
void main() {
    vec3 c = texture2D(uTex, vUv).rgb;
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float w = smoothstep(0.72, 1.05, lum);
    gl_FragColor = vec4(c * w, 1.0);
}
"""

        private const val BLUR_FS = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uStep;
void main() {
    vec3 sum = texture2D(uTex, vUv).rgb * 0.227027;
    sum += texture2D(uTex, vUv + uStep * 1.3846).rgb * 0.316216;
    sum += texture2D(uTex, vUv - uStep * 1.3846).rgb * 0.316216;
    sum += texture2D(uTex, vUv + uStep * 3.2307).rgb * 0.070270;
    sum += texture2D(uTex, vUv - uStep * 3.2307).rgb * 0.070270;
    gl_FragColor = vec4(sum, 1.0);
}
"""

        private const val COMPOSITE_FS = """
precision mediump float;
varying vec2 vUv;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uSpeedFx;
uniform float uWorldMode;

vec3 acesToneMap(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec2 dir = vUv - vec2(0.5);
    float dist = length(dir);
    float ca = (0.0008 + uSpeedFx * 0.0075) * dist;

    float r = texture2D(uScene, vUv + dir * ca).r;
    float g = texture2D(uScene, vUv).g;
    float b = texture2D(uScene, vUv - dir * ca).b;
    vec3 sceneCol = vec3(r, g, b);

    vec3 bloomCol = texture2D(uBloom, vUv).rgb;
    float bloomAmt = uWorldMode < 0.5 ? 0.65 : 1.05;
    vec3 hdr = sceneCol + bloomCol * bloomAmt;

    vec3 mapped = acesToneMap(hdr * 1.08);

    float vig = smoothstep(1.05, 0.38, dist);
    mapped *= mix(0.80, 1.0, vig);

    gl_FragColor = vec4(mapped, 1.0);
}
"""
    }
}
