package com.mikeyd.bezeltown.render

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.Engine

/**
 * Compose wrapper around a [FilamentHost]. [createScene] runs once; the frame
 * loop only runs while the lifecycle is resumed, to save battery.
 */
@Composable
fun <T : FilamentScene> FilamentSurface(
    createScene: (Engine) -> T,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var host by remember { mutableStateOf<FilamentHost?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { textureView ->
                val engine = FilamentRuntime.engine(context)
                host = FilamentHost(engine, textureView, createScene(engine))
            }
        },
        onRelease = {
            host?.destroy()
            host = null
        },
    )

    DisposableEffect(lifecycleOwner, host) {
        val current = host
        val observer = LifecycleEventObserver { _, _ ->
            current?.setRunning(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            current?.setRunning(false)
        }
    }
}
