package com.aaritlabs.funtoza.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.aaritlabs.funtoza.engine.BaseGameEngine

class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile
    private var isRunning = false
    var engine: BaseGameEngine? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        resumeGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine?.onSurfaceChanged(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pauseGame()
    }

    fun resumeGame() {
        if (!isRunning) {
            isRunning = true
            gameThread = Thread(this, "FuntozaGameThread").apply { start() }
        }
    }

    fun pauseGame() {
        isRunning = false
        try {
            gameThread?.join(500)
        } catch (_: InterruptedException) {}
        gameThread = null
    }

    override fun run() {
        var lastTime = System.nanoTime()

        while (isRunning) {
            val now = System.nanoTime()
            val deltaTime = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
            lastTime = now

            val eng = engine
            if (eng != null && eng.isInitialized) {
                try {
                    eng.update(deltaTime)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        synchronized(holder) {
                            eng.render(canvas)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Target ~60-90 FPS
            val sleepTimeMs = 12L
            try {
                Thread.sleep(sleepTimeMs)
            } catch (_: InterruptedException) {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return engine?.onTouchEvent(event) ?: super.onTouchEvent(event)
    }
}

