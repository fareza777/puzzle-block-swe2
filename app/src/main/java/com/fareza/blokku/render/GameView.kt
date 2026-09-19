package com.fareza.blokku.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.fareza.blokku.data.Save
import com.fareza.blokku.data.Themes
import kotlin.math.abs
import kotlin.math.max

/**
 * Single custom view hosting the whole game. Runs a Choreographer loop only
 * while something is animating — at rest it sleeps, so it's battery friendly.
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    val scenes = SceneStack(this)
    var densityPx = 1f
    var lastDt = 0.016f
    private var lastNs = 0L
    private var looping = false
    private var keepAlive = false // game scene keeps ticking for idle anims
    var touchSlopPx = 8f
    val safeTop = D.dp(18f)
    var globalTime = 0f

    init {
        D.density = resources.displayMetrics.density
        D.scaledDensity = resources.displayMetrics.scaledDensity
        densityPx = D.density
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        isFocusable = true
    }

    fun startLoop() {
        if (!looping) {
            looping = true
            lastNs = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun requestKeepAlive(v: Boolean) { keepAlive = v }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        if (lastNs == 0L) lastNs = frameTimeNanos
        val dt = ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = frameTimeNanos
        lastDt = dt
        globalTime += dt
        scenes.update(dt)
        invalidate()
        if (scenes.needsFrame || keepAlive || scenes.transitioning) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            looping = false
        }
    }

    fun wake() {
        scenes.needsFrame = true
        startLoop()
    }

    override fun onDraw(canvas: Canvas) {
        scenes.render(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scenes.dispatchTouch(event)
        if (event.action == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) wake()
        return true
    }

    fun onHostBack(): Boolean = scenes.back()

    /** Convert board-agnostic layout helper. */
    fun boardArea(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        val side = minOf(w - D.dp(24f), h * 0.52f)
        val left = (w - side) / 2f
        val top = safeTop + D.dp(96f)
        return RectF(left, top, left + side, top + side)
    }
}

class SceneStack(private val view: GameView) {
    private val stack = ArrayList<Scene>()
    var needsFrame = false
    var transitioning = false

    // transition state
    private var inAnim: Anim? = null
    private var prevScene: Scene? = null

    val top: Scene? get() = stack.lastOrNull()

    fun push(s: Scene, animate: Boolean = true) {
        s.host = view
        if (animate && stack.isNotEmpty()) {
            prevScene = stack.last()
            inAnim = Anim(0.28f, Ease.outCubic)
            transitioning = true
        }
        stack.add(s)
        s.onEnter()
        view.wake()
    }

    fun replace(s: Scene) {
        prevScene = null
        while (stack.isNotEmpty()) stack.removeAt(stack.size - 1).onExit()
        s.host = view
        stack.add(s)
        s.onEnter()
        view.wake()
    }

    fun pop(animate: Boolean = true) {
        if (stack.size <= 1) return
        val s = stack.removeAt(stack.size - 1)
        s.onExit()
        if (animate) {
            prevScene = s
            inAnim = Anim(0.24f, Ease.outCubic)
            transitioning = true
        }
        top?.onResume()
        view.wake()
    }

    fun swapTo(scene: Scene) {
        // pop current and push new without keeping anything below top
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1).onExit()
        push(scene, animate = false)
    }

    fun update(dt: Float) {
        if (inAnim != null) {
            inAnim!!.update(dt)
            if (inAnim!!.done) { inAnim = null; prevScene = null; transitioning = false }
        }
        var nf = false
        for (s in stack) {
            s.update(dt)
            if (s is FrameActive && s.wantsFrame()) nf = true
        }
        if (prevScene is FrameActive && (prevScene as FrameActive).wantsFrame()) nf = true
        needsFrame = nf || transitioning
    }

    fun render(c: Canvas) {
        val anim = inAnim
        if (anim != null && prevScene != null) {
            val t = anim.v
            // previous scene slides out to the left, current slides in from right
            c.save()
            c.translate(-view.width * 0.25f * t, 0f)
            c.saveLayerAlpha(0f, 0f, view.width.toFloat(), view.height.toFloat(), (255 * (1f - t * 0.6f)).toInt())
            prevScene!!.render(c)
            c.restore()
            c.restore()
            c.save()
            c.translate(view.width * (1f - t), 0f)
            top?.render(c)
            c.restore()
        } else {
            top?.render(c)
        }
    }

    fun dispatchTouch(e: MotionEvent) {
        if (transitioning) return
        top?.onTouch(e)
        if (top?.dead == true) pop()
    }

    fun back(): Boolean {
        val t = top ?: return false
        if (t.onBack()) return true
        if (stack.size > 1) { pop(); return true }
        return false
    }

    val size get() = stack.size
}

/** Implemented by scenes that need continuous frames (particles, idle animation). */
interface FrameActive {
    fun wantsFrame(): Boolean
}
