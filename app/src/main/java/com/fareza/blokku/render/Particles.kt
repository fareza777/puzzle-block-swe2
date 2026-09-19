package com.fareza.blokku.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, var age: Float = 0f,
    var size: Float,
    var color: Int,
    var gravity: Float = 0f,
    var shape: Int = 0, // 0=circle 1=square 2=spark
    var rotation: Float = 0f,
    var spin: Float = 0f,
)

class Particles {
    private val list = ArrayList<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    /** Hard cap keeps low-end devices smooth even during multi-clears. */
    private val MAX = 220
    private fun room(n: Int) = minOf(n, (MAX - list.size).coerceAtLeast(0))

    fun burst(
        x: Float, y: Float, color: Int, count: Int = 14,
        speed: Float = 400f, size: Float = 10f, life: Float = 0.7f,
        gravity: Float = 900f, shape: Int = 1,
    ) {
        repeat(room(count)) {
            val a = Random.nextFloat() * 6.283f
            val sp = speed * (0.4f + Random.nextFloat() * 0.9f)
            list.add(
                Particle(
                    x, y,
                    cos(a) * sp, sin(a) * sp - speed * 0.25f,
                    life * (0.6f + Random.nextFloat() * 0.6f),
                    size = size * (0.5f + Random.nextFloat()),
                    color = color, gravity = gravity, shape = shape,
                    rotation = Random.nextFloat() * 360f,
                    spin = (Random.nextFloat() - 0.5f) * 720f,
                )
            )
        }
    }

    fun ring(x: Float, y: Float, color: Int, count: Int = 24, speed: Float = 500f, size: Float = 8f) {
        for (i in 0 until room(count)) {
            val a = i.toFloat() / count * 6.283f
            list.add(
                Particle(
                    x, y, cos(a) * speed, sin(a) * speed,
                    0.55f, size = size, color = color, gravity = 200f, shape = 0,
                )
            )
        }
    }

    fun sparkle(x: Float, y: Float, w: Float, h: Float, color: Int, count: Int = 8) {
        repeat(room(count)) {
            list.add(
                Particle(
                    x + Random.nextFloat() * w, y + Random.nextFloat() * h,
                    (Random.nextFloat() - 0.5f) * 60f, -80f - Random.nextFloat() * 100f,
                    0.9f, size = 5f + Random.nextFloat() * 6f, color = color,
                    gravity = -60f, shape = 2,
                )
            )
        }
    }

    fun update(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) { it.remove(); continue }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.spin * dt
        }
    }

    fun render(c: Canvas) {
        for (p in list) {
            val k = 1f - p.age / p.life
            paint.color = p.color
            paint.alpha = (255 * k).toInt().coerceIn(0, 255)
            val s = p.size * (0.4f + 0.6f * k)
            when (p.shape) {
                0 -> c.drawCircle(p.x, p.y, s / 2f, paint)
                1 -> {
                    c.save()
                    c.rotate(p.rotation, p.x, p.y)
                    tmp.set(p.x - s / 2, p.y - s / 2, p.x + s / 2, p.y + s / 2)
                    c.drawRect(tmp, paint)
                    c.restore()
                }
                else -> {
                    c.drawCircle(p.x, p.y, s / 3f, paint)
                    c.drawRect(p.x - s / 2, p.y - s / 8, p.x + s / 2, p.y + s / 8, paint)
                }
            }
        }
        paint.alpha = 255
    }

    val isActive get() = list.isNotEmpty()
    fun clear() = list.clear()
}
