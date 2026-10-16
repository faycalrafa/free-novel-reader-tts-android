package com.novelarrow.reader.ui.common

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelarrow.reader.R
import com.novelarrow.reader.util.AppLogger

private const val TAG = "AppLoadingIndicator"

/**
 * Global application loading indicator featuring the custom looping claymorphic animation
 * of the turning book page and glowing sound wave with transparent background.
 *
 * Automatically scales to any container size (from 16.dp button spinners to 72.dp full-screen overlays).
 * On Android 9+ (API 28+), renders hardware-accelerated animated WebP via AnimatedImageDrawable.
 * On API < 28 or preview/test harnesses, smoothly falls back to an animated pulsing brand disc.
 */
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String? = "Loading..."
) {
    val context = LocalContext.current
    val effectiveModifier = if (modifier == Modifier) Modifier.size(size) else modifier

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val animDrawable = remember(context) {
            try {
                val source = ImageDecoder.createSource(context.resources, R.drawable.app_loading_animation)
                val drawable = ImageDecoder.decodeDrawable(source)
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable
                } else {
                    null
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to decode AnimatedImageDrawable, using fallback: ${e.message}", e)
                null
            }
        }

        DisposableEffect(animDrawable) {
            animDrawable?.start()
            onDispose {
                try {
                    animDrawable?.stop()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error stopping AnimatedImageDrawable on dispose: ${e.message}", e)
                }
            }
        }

        if (animDrawable != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(animDrawable)
                    }
                },
                modifier = effectiveModifier
            )
            return
        }
    }

    // Fallback for API < 28, headless tests, or decode anomalies: Smooth pulsing brand badge
    val infiniteTransition = rememberInfiniteTransition(label = "loading_fallback")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = effectiveModifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app_icon_round),
            contentDescription = contentDescription,
            modifier = Modifier.scale(scale)
        )
    }
}
