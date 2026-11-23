package com.aaritlabs.funtoza.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Universal Safe Area utility for Funtoza.
 * Ensures all screens extend edge-to-edge behind system bars and display cutouts (notches/punch holes),
 * while keeping all interactive controls, text, buttons, and HUD elements safely inset.
 */
object SafeAreaUtils {

    /**
     * Configures the activity window for edge-to-edge drawing extending across
     * cutouts, status bar, and navigation bar, with transient immersive behavior.
     */
    fun setupEdgeToEdge(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity.window.attributes.layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            hideSystemBars(activity)
        } catch (_: Exception) {}
    }

    /**
     * Hides system bars with transient swipe behavior.
     */
    fun hideSystemBars(activity: Activity) {
        try {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {}
    }

    /**
     * Calculates the safe area insets (taking maximum of visible insets, ignored insets,
     * and display cutout insets) so views never collide with camera notches, status bars,
     * or gesture navigation bars.
     */
    fun getSafeAreaInsets(windowInsets: WindowInsetsCompat): InsetsValues {
        val visibleInsets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ignoredInsets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        return InsetsValues(
            left = maxOf(visibleInsets.left, ignoredInsets.left),
            top = maxOf(visibleInsets.top, ignoredInsets.top),
            right = maxOf(visibleInsets.right, ignoredInsets.right),
            bottom = maxOf(visibleInsets.bottom, ignoredInsets.bottom)
        )
    }

    data class InsetsValues(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    /**
     * Applies safe area insets as padding on the target view, adding to the view's initial padding.
     */
    fun applySafeAreaPadding(
        targetView: View,
        padLeft: Boolean = true,
        padTop: Boolean = true,
        padRight: Boolean = true,
        padBottom: Boolean = true
    ) {
        val initialLeft = targetView.paddingLeft
        val initialTop = targetView.paddingTop
        val initialRight = targetView.paddingRight
        val initialBottom = targetView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(targetView) { v, windowInsets ->
            val insets = getSafeAreaInsets(windowInsets)
            v.setPadding(
                if (padLeft) initialLeft + insets.left else initialLeft,
                if (padTop) initialTop + insets.top else initialTop,
                if (padRight) initialRight + insets.right else initialRight,
                if (padBottom) initialBottom + insets.bottom else initialBottom
            )
            windowInsets
        }
        triggerInsets(targetView)
    }

    /**
     * Custom insets listener callback for multi-view or margin adjustments (e.g. GameActivity).
     */
    fun applySafeAreaCallback(
        rootView: View,
        callback: (insets: InsetsValues) -> Unit
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = getSafeAreaInsets(windowInsets)
            callback(insets)
            windowInsets
        }
        triggerInsets(rootView)
    }

    private fun triggerInsets(view: View) {
        if (view.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(view)
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }
}
