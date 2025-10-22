// CatchTheDot - Single-file Kotlin game for Android Studio
// Instructions:
// 1. Create a new Android project (Empty Activity) in Android Studio (Kotlin).
// 2. Replace MainActivity.kt content with this file's content.
// 3. Set minSdk to 21+ and run on an emulator or device.
// No additional resources required — everything is drawn programmatically.

package com.example.catchthedot

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the game view programmatically and set it as the content
        val gameView = GameView(this)
        gameView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(gameView)
    }
}

class GameView(context: Context) : View(context) {
    private val bgPaint = Paint().apply { isAntiAlias = true }
    private val dotPaint = Paint().apply { isAntiAlias = true }
    private val textPaint = Paint().apply { isAntiAlias = true; textSize = 64f }
    private val hintPaint = Paint().apply { isAntiAlias = true; textSize = 36f }

    private var dotX = 0f
    private var dotY = 0f
    private var dotRadius = 80f

    private var viewW = 0
    private var viewH = 0

    private var score = 0
    private var highScore = 0
    private var speed = 1.0f // controls how often the dot teleports (lower is faster)

    private var lastMoveTime = 0L
    private var moveIntervalMs = 1200L // initial interval

    private var gameRunning = false
    private var startTime = 0L
    private var elapsedMs = 0L

    private val prefs: SharedPreferences = context.getSharedPreferences("catch_prefs", Context.MODE_PRIVATE)

    init {
        bgPaint.color = 0xFF0F172A.toInt() // slate-ish
        dotPaint.color = 0xFFE11D48.toInt() // pink/red
        textPaint.color = 0xFFFFFFFF.toInt()
        hintPaint.color = 0xFFBFC6D8.toInt()

        highScore = prefs.getInt("high_score", 0)

        // Start the loop
        post(frameRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w
        viewH = h
        resetDot()
    }

    private fun resetDot() {
        dotRadius = (minOf(viewW, viewH) * 0.08f).coerceAtLeast(40f)
        dotX = Random.nextFloat() * (viewW - dotRadius * 2) + dotRadius
        dotY = Random.nextFloat() * (viewH - dotRadius * 2) + dotRadius
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, viewW.toFloat(), viewH.toFloat(), bgPaint)

        // Dot
        canvas.drawOval(RectF(dotX - dotRadius, dotY - dotRadius, dotX + dotRadius, dotY + dotRadius), dotPaint)

        // Score
        canvas.drawText("Score: $score", 28f, 80f, textPaint)
        canvas.drawText("High: $highScore", 28f, 160f, hintPaint)

        // Timer / Hint
        if (!gameRunning) {
            canvas.drawText("Tap the dot to start!", viewW * 0.5f - 220f, viewH * 0.5f - 40f, textPaint)
            canvas.drawText("Each catch makes it faster.", viewW * 0.5f - 220f, viewH * 0.5f + 20f, hintPaint)
        } else {
            val timeSec = elapsedMs / 1000
            canvas.drawText("Time: ${timeSec}s", viewW - 300f, 80f, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val tx = event.x
            val ty = event.y

            if (!gameRunning) {
                // If user taps the dot to start
                if (isInsideDot(tx, ty)) {
                    startGame()
                } else {
                    // little nudge to remind them to tap the dot
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            } else {
                // Game running: check if tapped the dot
                if (isInsideDot(tx, ty)) {
                    onDotCaught()
                } else {
                    // penalty: reduce score by 1 but not below 0
                    score = (score - 1).coerceAtLeast(0)
                }
            }
            invalidate()
        }
        return true
    }

    private fun isInsideDot(x: Float, y: Float): Boolean {
        val d = hypot((x - dotX), (y - dotY))
        return d <= dotRadius
    }

    private fun onDotCaught() {
        score += 1
        // speed up: reduce interval time
        moveIntervalMs = (moveIntervalMs * 0.90).toLong().coerceAtLeast(200L)
        // grow slightly to feel rewarding (but cap)
        dotRadius = (dotRadius * 0.95f).coerceAtLeast(28f)
        // move immediately
        teleportDot()

        // update high score
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }
    }

    private fun teleportDot() {
        dotX = Random.nextFloat() * (viewW - dotRadius * 2) + dotRadius
        dotY = Random.nextFloat() * (viewH - dotRadius * 2) + dotRadius
        lastMoveTime = SystemClock.uptimeMillis()
    }

    private fun startGame() {
        gameRunning = true
        score = 0
        moveIntervalMs = 1200L
        resetDot()
        dotRadius = (minOf(viewW, viewH) * 0.08f).coerceAtLeast(40f)
        startTime = SystemClock.uptimeMillis()
        lastMoveTime = startTime
    }

    private fun stopGame() {
        gameRunning = false
        elapsedMs = SystemClock.uptimeMillis() - startTime
        // small celebration: enlarge dot then reset
        dotRadius = (minOf(viewW, viewH) * 0.12f).coerceAtMost(160f)
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (gameRunning) {
                elapsedMs = now - startTime
                // every moveIntervalMs, teleport the dot (penalize missed taps)
                if (now - lastMoveTime >= moveIntervalMs) {
                    // when the player is too slow, they lose a point
                    score = (score - 1).coerceAtLeast(0)
                    teleportDot()
                }

                // end condition: if player hasn't scored for long OR if time > 60s
                if (elapsedMs >= 60_000L) {
                    stopGame()
                }
            }

            invalidate()
            // schedule next frame
            postDelayed(this, 16L) // ~60fps redraws, logic runs on time checks
        }