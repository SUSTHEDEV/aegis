package com.susthedev.aegis

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renders an interactive System Overlay prompt window (not RROs) directly on top
 * of the screen when AegisPolicy encounters an unrecorded permission grant request.
 */
object PromptOverlay {

    private fun getSystemContext(): Context? = try {
        val atClass = Class.forName("android.app.ActivityThread")
        val currentAT = atClass.getMethod("currentActivityThread").invoke(null)
        atClass.getMethod("getSystemContext").invoke(currentAT) as? Context
    } catch (t: Throwable) {
        XposedHelper.log("Aegis: failed to obtain system Context", t)
        null
    }

    fun showPrompt(pkg: String, perm: String): AegisPolicy.Decision {
        val context = getSystemContext() ?: run {
            XposedHelper.log("Aegis [PromptOverlay]: system Context unavailable, defaulting to DENIED")
            return AegisPolicy.Decision.DENIED
        }

        val latch = CountDownLatch(1)
        val decision = java.util.concurrent.atomic.AtomicReference(AegisPolicy.Decision.DENIED)
        val handler = Handler(Looper.getMainLooper())
        var overlayView: View? = null

        handler.post {
            try {
                wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val density = context.resources.displayMetrics.density
                val pad = (16 * density).toInt()

                val cardBackground = GradientDrawable().apply {
                    setColor(Color.parseColor("#1F2430"))
                    cornerRadius = 16 * density
                    setStroke((1 * density).toInt(), Color.parseColor("#3B4252"))
                }

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, pad, pad, pad)
                    background = cardBackground
                }

                val titleView = TextView(context).apply {
                    text = "Aegis Development Grant"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                root.addView(titleView)

                val messageView = TextView(context).apply {
                    text = "Application:\n$pkg\n\nRequests Signature Permission:\n$perm"
                    setTextColor(Color.parseColor("#D8DEE9"))
                    textSize = 14f
                    setPadding(0, (12 * density).toInt(), 0, (16 * density).toInt())
                }
                root.addView(messageView)

                val btnContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }

                val btnDeny = Button(context).apply {
                    text = "Deny"
                    setTextColor(Color.parseColor("#BF616A"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        decision.set(AegisPolicy.Decision.DENIED)
                        removeOverlay(wm, root)
                        latch.countDown()
                    }
                }

                val btnApprove = Button(context).apply {
                    text = "Approve"
                    setTextColor(Color.parseColor("#A3BE8C"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        decision.set(AegisPolicy.Decision.APPROVED)
                        removeOverlay(wm, root)
                        latch.countDown()
                    }
                }

                btnContainer.addView(btnDeny)
                btnContainer.addView(btnApprove)
                root.addView(btnContainer)

                overlayView = root

                val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

                val params = WindowManager.LayoutParams(
                    (320 * density).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                wm?.addView(root, params)
                XposedHelper.log("Aegis [PromptOverlay]: prompt window displayed for $pkg / $perm")
            } catch (t: Throwable) {
                XposedHelper.log("Aegis [PromptOverlay]: error rendering overlay window", t)
                latch.countDown()
            }
        }

        // Wait up to 30 seconds for user action; fail-closed on timeout
        val completed = latch.await(30, TimeUnit.SECONDS)
        if (!completed) {
            XposedHelper.log("Aegis [PromptOverlay]: prompt timed out after 30s for $pkg / $perm -> DENIED")
            handler.post {
                overlayView?.let { v -> removeOverlay(wm, v) }
            }
            return AegisPolicy.Decision.DENIED
        }

        XposedHelper.log("Aegis [PromptOverlay]: user decision for $pkg / $perm = ${decision.get()}")
        return decision.get()
    }

    private var wm: WindowManager? = null

    private fun removeOverlay(wm: WindowManager?, view: View) {
        try {
            wm?.removeView(view)
        } catch (t: Throwable) {
            XposedHelper.log("Aegis [PromptOverlay]: error removing overlay view", t)
        }
    }
}
