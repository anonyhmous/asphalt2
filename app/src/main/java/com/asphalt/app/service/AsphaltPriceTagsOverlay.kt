package com.asphalt.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** نص صغير (سعر الكيلو) بيتكتب فوق زرار سعر معين على الشاشة. */
data class PriceTag(val bounds: Rect, val text: String)

/**
 * طبقة شفافة بتغطي الشاشة ومبتلمسش (الضغطات بتعدّي منها لإن درايف).
 * بترسم سعر الكيلو فوق كل زرار اقتراح سعر، بنفس الشكل: رقم برتقالي صغير فوق السعر.
 * أي خطأ هنا بيتمسك ومبيأثرش على باقي التطبيق.
 */
class AsphaltPriceTagsOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TagsView? = null

    private class TagsView(context: Context) : View(context) {
        var tags: List<PriceTag> = emptyList()

        private val density = context.resources.displayMetrics.density
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF9A2E")
            textSize = 15f * density
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6222222")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val loc = IntArray(2)
            getLocationOnScreen(loc) // لو الطبقة مش بادية من أعلى الشاشة بالظبط، نعدّل الإحداثيات
            for (t in tags) {
                val cx = (t.bounds.centerX() - loc[0]).toFloat()
                val bottom = (t.bounds.top - loc[1]).toFloat() - 2f * density
                val w = textPaint.measureText(t.text) + 14f * density
                val h = textPaint.textSize + 8f * density
                val rect = RectF(cx - w / 2f, bottom - h, cx + w / 2f, bottom)
                canvas.drawRoundRect(rect, 8f * density, 8f * density, bgPaint)
                canvas.drawText(t.text, cx, bottom - 6f * density, textPaint)
            }
        }
    }

    /** الأماكن اللي الطبقة بترسم فيها، عشان الـ OCR يخفيها من لقطته. */
    fun currentRects(): List<Rect> {
        val v = view ?: return emptyList()
        val density = context.resources.displayMetrics.density
        val paint = Paint().apply { textSize = 15f * density; isFakeBoldText = true }
        return v.tags.map { t ->
            val w = (paint.measureText(t.text) + 14f * density).toInt()
            val h = (paint.textSize + 8f * density).toInt()
            Rect(t.bounds.centerX() - w / 2, t.bounds.top - h - (2f * density).toInt(), t.bounds.centerX() + w / 2, t.bounds.top)
        }
    }

    fun show(tags: List<PriceTag>) {
        try {
            if (tags.isEmpty()) {
                clear()
                return
            }
            if (!Settings.canDrawOverlays(context)) return
            val existing = view
            if (existing != null) {
                existing.tags = tags
                existing.invalidate()
                return
            }
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            val v = TagsView(context)
            v.tags = tags
            windowManager.addView(v, lp)
            view = v
        } catch (e: Exception) {
            Log.w(TAG, "price tags overlay failed", e)
            view = null
        }
    }

    fun clear() {
        try {
            view?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        view = null
    }

    companion object {
        private const val TAG = "AsphaltPriceTags"
    }
}
