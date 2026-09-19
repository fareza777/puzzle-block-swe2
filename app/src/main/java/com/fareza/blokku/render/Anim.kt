package com.fareza.blokku.render

import kotlin.math.pow

object Ease {
    val linear: (Float) -> Float = { it }
    val outQuad: (Float) -> Float = { 1f - (1f - it) * (1f - it) }
    val inQuad: (Float) -> Float = { it * it }
    val outCubic: (Float) -> Float = { 1f - (1f - it).pow(3) }
    val outBack: (Float) -> Float = { t ->
        val c = 1.70158f
        val u = t - 1f
        1f + (c + 1f) * u * u * u + c * u * u
    }
    val outElastic: (Float) -> Float = { t ->
        if (t == 0f || t == 1f) t
        else (2.0.pow(-10.0 * t) * kotlin.math.sin((t * 10.0 - 0.75) * (2 * Math.PI / 3)) + 1).toFloat()
    }
}

/** Simple time-based value animation driven by the frame loop. */
class Anim(
    val duration: Float,
    val ease: (Float) -> Float = Ease.outCubic,
    val delay: Float = 0f,
) {
    var t = 0f
    var done = false
        private set

    fun update(dt: Float) {
        if (done) return
        t += dt
        if (t >= delay + duration) { t = delay + duration; done = true }
    }

    /** 0..1 eased progress */
    val v: Float
        get() {
            val x = ((t - delay) / duration).coerceIn(0f, 1f)
            return ease(x)
        }

    val raw: Float get() = ((t - delay) / duration).coerceIn(0f, 1f)

    fun reset() { t = 0f; done = false }

    companion object {
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun lerpInt(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
    }
}
