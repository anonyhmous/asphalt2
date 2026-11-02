package com.asphalt.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.asphalt.app.data.AsphaltRepository
import com.asphalt.app.model.JudgmentResult
import com.asphalt.app.ui.components.FloatingJudgmentOverlayContent
import com.asphalt.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/**
 * Floating judgment card (SYSTEM_ALERT_WINDOW) shown above Uber / DiDi / inDrive.
 *
 * Why the previous version never showed anything:
 * a ComposeView added straight to WindowManager (outside an Activity) MUST have a
 * ViewTreeLifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner, otherwise
 * addView() throws IllegalStateException — and the old code swallowed that exception silently.
 */
class AsphaltOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val repository = AsphaltRepository.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var overlayView: DraggableFrame? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null

    private var isExpanded = true
    private var currentJudgment: JudgmentResult? = null

    /** Lifecycle owner required by Compose when hosted in a plain window. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        init {
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    /** Frame that lets the card be dragged while still letting inner buttons receive taps. */
    private class DraggableFrame(
        context: Context,
        private val onDrag: (dx: Int, dy: Int) -> Unit,
        private val onDragStart: () -> Unit
    ) : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; dragging = false
                    onDragStart()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging &&
                        (kotlin.math.abs(ev.rawX - downX) > slop || kotlin.math.abs(ev.rawY - downY) > slop)
                    ) dragging = true
                }
            }
            return dragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    onDrag((ev.rawX - downX).toInt(), (ev.rawY - downY).toInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }
    }

    private var startX = 0
    private var startY = 0

    fun startListening() {
        scope.launch {
            combine(repository.latestJudgment, repository.isOverlayVisible) { j, visible -> j to visible }
                .collect { (judgment, visible) ->
                    if (judgment != null && visible) showOverlay(judgment) else hideOverlay()
                }
        }
    }

    fun showOverlay(judgment: JudgmentResult) {
        currentJudgment = judgment
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission (draw over other apps) is not granted")
            return
        }

        if (overlayView != null) {
            render(judgment)
            return
        }

        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 30
                y = 180
            }
            params = lp

            val newOwner = OverlayOwner()
            owner = newOwner

            val frame = DraggableFrame(
                context,
                onDrag = { dx, dy ->
                    params?.let { p ->
                        p.x = startX + dx
                        p.y = startY + dy
                        try { windowManager.updateViewLayout(overlayView, p) } catch (_: Exception) {}
                        overlayView?.post { publishBounds() }
                    }
                },
                onDragStart = {
                    startX = params?.x ?: 0
                    startY = params?.y ?: 0
                }
            )

            val cv = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            }
            // These three lines are what was missing.
            frame.setViewTreeLifecycleOwner(newOwner)
            frame.setViewTreeSavedStateRegistryOwner(newOwner)
            frame.setViewTreeViewModelStoreOwner(newOwner)
            cv.setViewTreeLifecycleOwner(newOwner)
            cv.setViewTreeSavedStateRegistryOwner(newOwner)
            cv.setViewTreeViewModelStoreOwner(newOwner)

            frame.addView(cv)
            composeView = cv
            overlayView = frame
            render(judgment)

            frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publishBounds() }
            windowManager.addView(frame, lp)
            newOwner.resume()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
            overlayView = null
            composeView = null
            owner?.destroy()
            owner = null
        }
    }

    /** بنبلّغ الـ OCR بمكان الكارت على الشاشة عشان يتخفي من اللقطة ومايقراش نفسه. */
    private fun publishBounds() {
        val view = overlayView ?: return
        if (view.width == 0 || view.height == 0) return
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        repository.overlayBounds = android.graphics.Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
    }

    private fun render(judgment: JudgmentResult) {
        val cv = composeView ?: return
        cv.setContent {
            MyApplicationTheme {
                // الكارت عربي دايماً (يمين لشمال) بغض النظر عن لغة الموبايل
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FloatingJudgmentOverlayContent(
                    judgment = judgment,
                    isExpanded = isExpanded,
                    opacity = repository.preferences.rulesFlow.value.overlayOpacity,
                    scale = repository.preferences.rulesFlow.value.overlayScale,
                    onToggleExpand = {
                        isExpanded = !isExpanded
                        currentJudgment?.let { render(it) }
                    },
                    onDismiss = { repository.dismissOverlay(byUser = true) }
                )
                }
            }
        }
    }

    fun hideOverlay() {
        overlayView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        overlayView = null
        composeView = null
        owner?.destroy()
        owner = null
        repository.overlayBounds = null
    }

    fun release() {
        hideOverlay()
        scope.cancel()
    }

    companion object {
        private const val TAG = "AsphaltOverlay"
    }
}
