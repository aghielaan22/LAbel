package com.aghielaan22.label

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp

private const val LENS = """
    uniform shader backdrop;
    uniform float2 bounds;
    uniform float radius;
    uniform float frost;
    half4 main(float2 p) {
        float2 center = bounds * 0.5;
        float2 q = abs(p - center) - (center - radius);
        float distance = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        float rim = 1.0 - smoothstep(0.0, radius, -distance);
        float2 normal = sign(p - center) * max(q, 0.001);
        float2 sampleAt = p - (normal / max(length(normal), 0.001)) * rim * radius * 0.55;
        float2 a = float2(frost, 0.0);
        float2 b = a.yx;
        return (backdrop.eval(sampleAt) * 4.0 +
            backdrop.eval(sampleAt + a) + backdrop.eval(sampleAt - a) +
            backdrop.eval(sampleAt + b) + backdrop.eval(sampleAt - b)) * 0.125;
    }
"""

@RequiresApi(33)
internal fun Modifier.refractedBackdrop(state: GlassBackdrop, blur: Dp, transparency: Float): Modifier = composed {
    val panel = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(LENS) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    onGloballyPositioned { origin = it.positionInWindow() }.drawWithCache {
        if (size.minDimension <= 0f) return@drawWithCache onDrawWithContent { drawContent() }
        shader.setFloatUniform("bounds", size.width, size.height)
        shader.setFloatUniform("radius", minOf(size.minDimension / 2f, 24 * density))
        shader.setFloatUniform("frost", blur.toPx() * (1f - transparency) * 0.22f)
        panel.renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
        onDrawWithContent {
            val delta = state.origin - origin
            panel.record {
                translate(delta.x, delta.y) { drawLayer(state.layer) }
            }
            drawLayer(panel)
            drawContent()
        }
    }
}