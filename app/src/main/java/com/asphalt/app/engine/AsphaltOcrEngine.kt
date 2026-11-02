package com.asphalt.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** رقم اتقرا من الشاشة بالـ OCR ومعاه حجمه ومكانه (الرقم الأكبر غالباً هو السعر). */
data class OcrNumber(
    val value: Double,
    val text: String,
    val heightPx: Int,
    val topPx: Int
)

/** لون فقاعة المسافة على الخريطة: أزرق = استلام الكابتن للراكب، أخضر/ليموني = مسافة الرحلة نفسها. */
enum class BubbleColor { BLUE, GREEN }

/** فقاعة مسافة على الخريطة (دقايق + كم) اتحدد نوعها من لون خلفيتها. */
data class OcrBubble(
    val color: BubbleColor,
    val km: Double,
    val minutes: Int?
)

/** ناتج الـ OCR: النص الكامل + الأرقام لوحدها + فقاعات المسافة الملوّنة. */
data class OcrOutput(
    val text: String,
    val numbers: List<OcrNumber>,
    val bubbles: List<OcrBubble> = emptyList()
)

/**
 * قارئ الـ OCR (Google ML Kit، بيشتغل على الموبايل نفسه من غير نت).
 * الصورة بتتقرا في الذاكرة بس وبتتمسح، ومبتتحفظش في أي مكان.
 * ملحوظة: النموذج ده بيقرا الأرقام والحروف اللاتينية كويس، لكن مش الحروف العربية،
 * عشان كده الاعتماد الأساسي على حجم الأرقام ومكانها ولون خلفيتها مش على الكلمات.
 */
class AsphaltOcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val ocrScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    private class ColoredNumber(
        val value: Double,
        val hasDecimal: Boolean,
        val box: Rect,
        val color: BubbleColor
    )

    fun recognize(bitmap: Bitmap, onDone: (OcrOutput?) -> Unit) {
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    // تنفيذ فحص البكسلات والألوان على خلفية المعالج لمنع أي تهنيج بالواجهة
                    ocrScope.launch {
                        val numbers = mutableListOf<OcrNumber>()
                        val colored = mutableListOf<ColoredNumber>()
                        try {
                            for (block in result.textBlocks) {
                                for (line in block.lines) {
                                    for (element in line.elements) {
                                        val box = element.boundingBox ?: continue
                                        val raw = element.text.trim()

                                        val clean = raw.replace(',', '.')
                                        if (NUMBER.matches(clean)) {
                                            clean.toDoubleOrNull()?.let {
                                                numbers.add(OcrNumber(it, clean, box.height(), box.top))
                                            }
                                        }

                                        val m = BUBBLE_NUMBER.matchEntire(raw)
                                        if (m != null) {
                                            val numText = m.groupValues[1].replace(',', '.').replace('٫', '.')
                                            val v = numText.toDoubleOrNull()
                                            if (v != null && v >= 0.1 && v <= 300.0) {
                                                val c = bubbleColorAround(bitmap, box)
                                                if (c != null) {
                                                    colored.add(ColoredNumber(v, numText.contains('.'), box, c))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "number extraction failed", e)
                        }
                        val bubbles = try {
                            buildBubbles(colored, bitmap.height)
                        } catch (e: Exception) {
                            Log.w(TAG, "bubble build failed", e)
                            emptyList()
                        }
                        onDone(OcrOutput(result.text, numbers, bubbles))
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "OCR failed", e)
                    onDone(null)
                }
        } catch (e: Exception) {
            Log.w(TAG, "OCR could not start", e)
            onDone(null)
        }
    }

    /** بيبص على البكسلات اللي حوالين الرقم (من برّه مربعه) ويحدد خلفيته زرقا ولا خضرا. */
    private fun bubbleColorAround(bmp: Bitmap, box: Rect): BubbleColor? {
        val h = max(box.height(), 8)
        val padX = max(4, (h * 0.5f).toInt())
        val padY = max(3, (h * 0.3f).toInt())
        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = minOf(bmp.width - 1, box.right + padX)
        val bottom = minOf(bmp.height - 1, box.bottom + padY)
        if (right <= left || bottom <= top) return null

        val step = max(2, h / 10)
        val hsv = FloatArray(3)
        var blue = 0
        var green = 0
        var total = 0
        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                val insideText = x >= box.left && x <= box.right && y >= box.top && y <= box.bottom
                if (!insideText) {
                    total++
                    val kind = pixelKind(bmp.getPixel(x, y), hsv)
                    if (kind == 1) blue++ else if (kind == 2) green++
                }
                x += step
            }
            y += step
        }
        if (total < 8) return null
        val bf = blue.toFloat() / total
        val gf = green.toFloat() / total
        return when {
            bf >= MIN_FRACTION && bf > gf * 2f -> BubbleColor.BLUE
            gf >= MIN_FRACTION && gf > bf * 2f -> BubbleColor.GREEN
            else -> null
        }
    }

    /** 1 = أزرق مشبع وفاتح، 2 = أخضر/ليموني مشبع وفاتح، 0 = غير كده (الخريطة الغامقة بتطلع 0). */
    private fun pixelKind(pixel: Int, hsv: FloatArray): Int {
        Color.colorToHSV(pixel, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        if (sat < 0.45f || value < 0.55f) return 0
        if (hue >= 195f && hue <= 255f) return 1
        if (hue >= 55f && hue <= 150f) return 2
        return 0
    }

    /**
     * الفقاعة = رقمين فوق بعض بنفس اللون: فوق الدقايق، وتحت الكيلومترات.
     * لو الدقايق ما اتقرتش، بنقبل الرقم العشري لوحده (بس في الجزء اللي فيه الخريطة).
     */
    private fun buildBubbles(items: List<ColoredNumber>, imageHeight: Int): List<OcrBubble> {
        val out = mutableListOf<OcrBubble>()
        for (color in BubbleColor.values()) {
            val list = items.filter { it.color == color }.sortedBy { it.box.top }
            var chosen: OcrBubble? = null

            var i = 0
            while (i < list.size && chosen == null) {
                var j = i + 1
                while (j < list.size && chosen == null) {
                    val up = list[i]
                    val low = list[j]
                    val h = max(up.box.height(), low.box.height()).toFloat()
                    val dy = (low.box.top - up.box.top).toFloat()
                    val dx = abs(up.box.centerX() - low.box.centerX()).toFloat()
                    if (dy >= h * 0.6f && dy <= h * 3.2f && dx <= h * 2.5f && !up.hasDecimal) {
                        chosen = OcrBubble(color, low.value, up.value.toInt())
                    }
                    j++
                }
                i++
            }

            if (chosen == null) {
                val single = list.lastOrNull { it.hasDecimal && it.box.top < imageHeight * 0.5f }
                if (single != null) chosen = OcrBubble(color, single.value, null)
            }

            if (chosen != null) out.add(chosen)
        }
        return out
    }

    fun close() {
        ocrScope.cancel()
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "AsphaltOcr"
        private const val MIN_FRACTION = 0.35f
        private val NUMBER = Regex("^[0-9]+(?:\\.[0-9]{1,2})?$")
        private val BUBBLE_NUMBER = Regex("^[~≈]?\\s*([0-9]{1,3}(?:[.,٫][0-9]{1,2})?)$")
    }
}
