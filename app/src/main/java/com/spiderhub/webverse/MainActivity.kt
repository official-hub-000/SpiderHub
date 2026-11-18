package com.spiderhub.webverse

import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.spiderhub.webverse.audio.SoundSynth
import com.spiderhub.webverse.engine.WebverseEngine
import com.spiderhub.webverse.engine.WebverseRenderer
import com.spiderhub.webverse.game.GameHudState
import com.spiderhub.webverse.ui.AssetsDialog
import com.spiderhub.webverse.ui.GameHud

class MainActivity : ComponentActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var soundSynth: SoundSynth
    private lateinit var engine: WebverseEngine
    private val hudState = mutableStateOf(GameHudState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        soundSynth = SoundSynth()
        engine = WebverseEngine(this, soundSynth)
        engine.setHudListener { snapshot ->
            runOnUiThread {
                hudState.value = snapshot
            }
        }

        val renderer = WebverseRenderer(assets, engine)

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContent {
            val hud by hudState
            var showAssets by remember { mutableStateOf(false) }

            val modelPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val fileName = resolveFileName(uri)
                    renderer.loadCustomModelStream(
                        streamProvider = { contentResolver.openInputStream(uri) },
                        displayName = fileName
                    )
                    showAssets = false
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { glSurfaceView },
                        modifier = Modifier.fillMaxSize()
                    )

                    GameHud(
                        hud = hud,
                        onMoveStick = { x, y ->
                            engine.moveStickX = x
                            engine.moveStickY = y
                        },
                        onLookDrag = { dx, dy ->
                            engine.onLookDrag(dx, dy)
                        },
                        onSwingChanged = { pressed ->
                            engine.onSwingButtonChanged(pressed)
                        },
                        onJump = { engine.triggerJumpOrVault() },
                        onZip = { engine.triggerWebZip() },
                        onStrike = { engine.triggerCombatStrike() },
                        onShiftDimension = { engine.toggleDimension() },
                        onToggleMute = { engine.toggleMute() },
                        onToggleBloom = { engine.toggleBloom() },
                        onOpenAssets = { showAssets = true }
                    )

                    if (showAssets) {
                        AssetsDialog(
                            hud = hud,
                            onImportModel = { modelPicker.launch(arrayOf("*/*")) },
                            onDismiss = { showAssets = false }
                        )
                    }
                }
            }
        }
    }

    private fun resolveFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "model.glb"
            }
        }
        return uri.lastPathSegment ?: "model.glb"
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundSynth.release()
    }
}
