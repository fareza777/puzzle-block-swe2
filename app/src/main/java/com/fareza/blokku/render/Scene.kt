package com.fareza.blokku.render

import android.graphics.Canvas
import android.view.MotionEvent

abstract class Scene {
    lateinit var host: GameView
    var dead = false

    open fun onEnter() {}
    open fun onExit() {}
    open fun update(dt: Float) {}
    abstract fun render(c: Canvas)
    open fun onTouch(e: MotionEvent): Boolean = false
    /** true = consumed (don't leave app) */
    open fun onBack(): Boolean = false
    open fun onResume() {}

    fun finish() { dead = true }

    fun s(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) host.context.getString(resId) else host.context.getString(resId, *args)

    fun scene() = host.scenes
}
