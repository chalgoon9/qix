package com.example.qixpanic

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    // Grid size (logical cells)
    private val cols = 96
    private val rows = 64

    // Cell states
    private val EMPTY = 0
    private val SOLID = 1
    private val TRAIL = 2

    private val grid = Array(cols) { IntArray(rows) { EMPTY } }

    // Player state
    private var px = cols / 2
    private var py = 0 // start on the top border
    private var dirX = 1
    private var dirY = 0
    private var drawing = false
    private var moveAccumulator = 0f
    private val stepPerSecond = 14f
    private val stepDuration = 1f / stepPerSecond

    private var lives = 3
    private var level = 1
    private var score = 0
    private var targetPercent = 75f
    private var levelCleared = false
    private var gameOver = false
    private var paused = false

    // Enemies
    private data class Enemy(var x: Float, var y: Float, var dx: Float, var dy: Float)
    private val enemies = mutableListOf<Enemy>()
    private val baseEnemySpeed = 8f // cells per second

    // Rendering helpers
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // Touch input
    private var touchStartX = 0f
    private var touchStartY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
        initField()
        initLevel()
    }

    private fun initField() {
        // Reset grid
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                grid[x][y] = EMPTY
            }
        }
        // Make borders solid
        for (x in 0 until cols) {
            grid[x][0] = SOLID
            grid[x][rows - 1] = SOLID
        }
        for (y in 0 until rows) {
            grid[0][y] = SOLID
            grid[cols - 1][y] = SOLID
        }
    }

    private fun initLevel() {
        enemies.clear()
        val enemyCount = (2 + (level - 1)).coerceAtMost(6)
        val speed = baseEnemySpeed + (level - 1) * 1.0f
        repeat(enemyCount) {
            val ex = Random.nextInt(cols / 4, cols * 3 / 4).toFloat()
            val ey = Random.nextInt(rows / 4, rows * 3 / 4).toFloat()
            var dx = if (Random.nextBoolean()) 1f else -1f
            var dy = if (Random.nextBoolean()) 1f else -1f
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            dx = dx / len * speed
            dy = dy / len * speed
            enemies += Enemy(ex, ey, dx, dy)
        }

        // Reset player
        px = cols / 2
        py = 0
        dirX = 1
        dirY = 0
        drawing = false
        moveAccumulator = 0f
        levelCleared = false
        gameOver = false
        paused = false
        // Clear any stray trail
        for (x in 0 until cols) for (y in 0 until rows) if (grid[x][y] == TRAIL) grid[x][y] = EMPTY
    }

    fun resume() {
        if (gameThread == null || !gameThread!!.isAlive) {
            gameThread = GameThread(holder, this).apply { running = true }
            gameThread!!.start()
        } else {
            gameThread?.running = true
        }
    }

    fun pause() {
        gameThread?.running = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join(100)
                retry = false
            } catch (e: InterruptedException) {
                // retry
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // UI hit regions
        val pauseRect = getPauseRect()
        val (leftR, rightR, upR, downR) = getDpadRects()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Pause toggle
                if (pointInRect(x, y, pauseRect)) {
                    paused = !paused
                    return true
                }
                // D-Pad
                if (pointInRect(x, y, leftR)) { dirX = -1; dirY = 0; return true }
                if (pointInRect(x, y, rightR)) { dirX = 1; dirY = 0; return true }
                if (pointInRect(x, y, upR)) { dirX = 0; dirY = -1; return true }
                if (pointInRect(x, y, downR)) { dirX = 0; dirY = 1; return true }

                touchStartX = x
                touchStartY = y
            }
            MotionEvent.ACTION_UP -> {
                if (gameOver) {
                    restartGame()
                    return true
                }
                if (levelCleared) {
                    level += 1
                    initLevel()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - touchStartX
                val dy = y - touchStartY
                if (abs(dx) > 16 || abs(dy) > 16) {
                    if (abs(dx) > abs(dy)) {
                        dirX = if (dx > 0) 1 else -1
                        dirY = 0
                    } else {
                        dirX = 0
                        dirY = if (dy > 0) 1 else -1
                    }
                    touchStartX = x
                    touchStartY = y
                }
            }
        }
        return true
    }

    private fun restartGame() {
        level = 1
        score = 0
        lives = 3
        initField()
        initLevel()
    }

    fun update(delta: Float) {
        if (gameOver || levelCleared || paused) return

        moveAccumulator += delta
        while (moveAccumulator >= stepDuration) {
            step()
            moveAccumulator -= stepDuration
        }

        // Enemies move with continuous time
        for (e in enemies) {
            var nx = e.x + e.dx * delta
            var ny = e.y + e.dy * delta

            // Bounce off SOLID walls
            if (isSolid(floor(nx).toInt(), floor(e.y).toInt())) {
                e.dx = -e.dx
                nx = e.x + e.dx * delta
            }
            if (isSolid(floor(e.x).toInt(), floor(ny).toInt())) {
                e.dy = -e.dy
                ny = e.y + e.dy * delta
            }
            e.x = nx
            e.y = ny

            // Trail collision -> lose life
            val cx = floor(e.x).toInt().coerceIn(0, cols - 1)
            val cy = floor(e.y).toInt().coerceIn(0, rows - 1)
            if (drawing && grid[cx][cy] == TRAIL) {
                loseLife()
                break
            }
        }

        // Check win condition
        val pct = claimedPercent()
        if (pct >= targetPercent) {
            levelCleared = true
        }
    }

    private fun step() {
        val nx = (px + dirX).coerceIn(0, cols - 1)
        val ny = (py + dirY).coerceIn(0, rows - 1)

        if (drawing) {
            // Crossing own trail is deadly
            if (grid[nx][ny] == TRAIL) {
                loseLife()
                return
            }
            // Reached border -> close shape
            if (grid[nx][ny] == SOLID) {
                closeTrailAndClaim()
                px = nx
                py = ny
                drawing = false
                return
            }
            // Continue carving trail
            grid[px][py] = TRAIL
            px = nx
            py = ny
        } else {
            // On border movement
            if (grid[nx][ny] == SOLID) {
                px = nx
                py = ny
            } else {
                // Leaving border into empty starts drawing
                drawing = true
                grid[px][py] = TRAIL
                px = nx
                py = ny
            }
        }
    }

    private fun loseLife() {
        lives -= 1
        // Clear any trail back to empty
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if (grid[x][y] == TRAIL) grid[x][y] = EMPTY
            }
        }
        drawing = false
        px = cols / 2
        py = 0
        dirX = 1
        dirY = 0
        if (lives <= 0) {
            gameOver = true
        }
    }

    private fun isSolid(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= cols || y >= rows) return true
        return grid[x][y] == SOLID
    }

    private fun closeTrailAndClaim() {
        // Snapshot empties to count claimed cells later
        var emptyBefore = 0
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if (grid[x][y] == EMPTY) emptyBefore++
            }
        }

        // 1) Flood from all enemies over non-solid, non-trail cells => reachable
        val reachable = Array(cols) { BooleanArray(rows) { false } }
        val qx = IntArray(cols * rows)
        val qy = IntArray(cols * rows)
        var head = 0
        var tail = 0

        fun enqueue(ix: Int, iy: Int) {
            if (ix < 0 || iy < 0 || ix >= cols || iy >= rows) return
            if (reachable[ix][iy]) return
            if (grid[ix][iy] == SOLID || grid[ix][iy] == TRAIL) return
            reachable[ix][iy] = true
            qx[tail] = ix
            qy[tail] = iy
            tail++
        }

        enemies.forEach { e ->
            enqueue(floor(e.x).toInt(), floor(e.y).toInt())
        }

        while (head < tail) {
            val cx = qx[head]
            val cy = qy[head]
            head++
            enqueue(cx + 1, cy)
            enqueue(cx - 1, cy)
            enqueue(cx, cy + 1)
            enqueue(cx, cy - 1)
        }

        // 2) Any non-reachable EMPTY becomes SOLID (claimed)
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if ((grid[x][y] == EMPTY) && !reachable[x][y]) {
                    grid[x][y] = SOLID
                }
            }
        }

        // 3) Trail becomes SOLID
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if (grid[x][y] == TRAIL) grid[x][y] = SOLID
            }
        }

        // Score: cells claimed
        var emptyAfter = 0
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if (grid[x][y] == EMPTY) emptyAfter++
            }
        }
        val claimed = (emptyBefore - emptyAfter).coerceAtLeast(0)
        score += claimed
    }

    private fun claimedPercent(): Float {
        var solid = 0
        var total = 0
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                if (x == 0 || y == 0 || x == cols - 1 || y == rows - 1) continue // ignore borders
                total++
                if (grid[x][y] == SOLID) solid++
            }
        }
        return if (total == 0) 0f else (solid * 100f / total)
    }

    fun drawGame(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        val cellSize = min(width / cols.toFloat(), height / rows.toFloat())
        val offX = (width - cellSize * cols) / 2f
        val offY = (height - cellSize * rows) / 2f

        fun rectForCell(cx: Int, cy: Int, out: RectF) {
            out.set(
                offX + cx * cellSize,
                offY + cy * cellSize,
                offX + (cx + 1) * cellSize,
                offY + (cy + 1) * cellSize
            )
        }

        val r = RectF()
        // Draw grid
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                when (grid[x][y]) {
                    SOLID -> paint.color = Color.rgb(20, 90, 180)
                    TRAIL -> paint.color = Color.RED
                    else -> continue
                }
                rectForCell(x, y, r)
                canvas.drawRect(r, paint)
            }
        }

        // Draw player
        paint.color = Color.GREEN
        val pcx = offX + (px + 0.5f) * cellSize
        val pcy = offY + (py + 0.5f) * cellSize
        canvas.drawCircle(pcx, pcy, max(2f, cellSize * 0.35f), paint)

        // Draw enemies
        paint.color = Color.YELLOW
        for (e in enemies) {
            val ex = offX + (e.x + 0.5f) * cellSize
            val ey = offY + (e.y + 0.5f) * cellSize
            canvas.drawCircle(ex, ey, max(2f, cellSize * 0.35f), paint)
        }

        // UI text
        val pct = claimedPercent()
        canvas.drawText("CLAIMED: ${"%2.0f".format(pct)}%", 16f, 50f, textPaint)
        canvas.drawText("LIVES: $lives", 16f, 95f, textPaint)
        canvas.drawText("LEVEL: $level", 16f, 140f, textPaint)
        canvas.drawText("SCORE: $score", 16f, 185f, textPaint)

        // Pause button
        drawPauseButton(canvas)
        // D-Pad
        drawDpad(canvas)

        if (paused) {
            drawCenteredText(canvas, "PAUSED", Color.LTGRAY)
        } else if (levelCleared) {
            drawCenteredText(canvas, "LEVEL CLEAR! Tap", Color.CYAN)
        } else if (gameOver) {
            drawCenteredText(canvas, "GAME OVER - Tap", Color.RED)
        }
    }

    private fun drawCenteredText(canvas: Canvas, msg: String, color: Int) {
        val p = Paint(textPaint)
        p.color = color
        p.textSize = 64f
        p.textAlign = Paint.Align.CENTER
        val x = width / 2f
        val y = height / 2f
        canvas.drawText(msg, x, y, p)
    }

    private fun getPauseRect(): RectF {
        val w = 140f
        val h = 60f
        return RectF(width - w - 16f, 16f, width - 16f, 16f + h)
    }

    private fun drawPauseButton(canvas: Canvas) {
        val r = getPauseRect()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 60, 60, 60) }
        canvas.drawRoundRect(r, 12f, 12f, p)
        val tp = Paint(textPaint)
        tp.textSize = 32f
        tp.textAlign = Paint.Align.CENTER
        tp.color = Color.WHITE
        canvas.drawText(if (paused) "RESUME" else "PAUSE", r.centerX(), r.centerY() + 12f, tp)
    }

    private data class DpadRects(val left: RectF, val right: RectF, val up: RectF, val down: RectF)

    private fun getDpadRects(): DpadRects {
        val margin = 24f
        val base = max(120f, min(width, height) * 0.16f)
        val cx = margin + base
        val cy = height - margin - base
        val btn = base * 0.9f
        val half = btn / 2f
        val span = base
        val left = RectF(cx - span * 1.5f, cy - half, cx - span * 0.5f, cy + half)
        val right = RectF(cx + span * 0.5f, cy - half, cx + span * 1.5f, cy + half)
        val up = RectF(cx - half, cy - span * 1.5f, cx + half, cy - span * 0.5f)
        val down = RectF(cx - half, cy + span * 0.5f, cx + half, cy + span * 1.5f)
        return DpadRects(left, right, up, down)
    }

    private fun drawDpad(canvas: Canvas) {
        val (left, right, up, down) = getDpadRects()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(100, 255, 255, 255)
        canvas.drawRoundRect(left, 12f, 12f, p)
        canvas.drawRoundRect(right, 12f, 12f, p)
        canvas.drawRoundRect(up, 12f, 12f, p)
        canvas.drawRoundRect(down, 12f, 12f, p)
    }

    private fun pointInRect(x: Float, y: Float, r: RectF): Boolean = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom

    private class GameThread(
        private val surfaceHolder: SurfaceHolder,
        private val gameView: GameView
    ) : Thread() {
        @Volatile var running = false

        override fun run() {
            var last = System.nanoTime()
            val nsPerFrame = 1_000_000_000L / 60L
            while (running) {
                val now = System.nanoTime()
                val delta = ((now - last) / 1_000_000_000.0f).coerceAtMost(0.1f)
                last = now

                gameView.update(delta)

                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        synchronized(surfaceHolder) {
                            gameView.drawGame(canvas)
                        }
                    }
                } finally {
                    if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas)
                }

                val frameTime = System.nanoTime() - now
                val sleepNs = nsPerFrame - frameTime
                if (sleepNs > 0) {
                    try { sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt()) } catch (_: InterruptedException) {}
                }
            }
        }
    }
}
