package com.smartautoclicker.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class TargetSelectionActivity : Activity() {
    private lateinit var selector: RegionSelectorView
    private var screenshot: Bitmap? = null
    private var slot: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 14, 31)
        window.navigationBarColor = Color.rgb(5, 14, 31)
        slot = intent.getIntExtra("slot", 0).coerceIn(0, 4)
        val path = intent.getStringExtra("screenshot_path")
        screenshot = path?.let { BitmapFactory.decodeFile(it) }
        val bmp = screenshot
        if (bmp == null) {
            Toast.makeText(this, "تعذّر فتح لقطة الشاشة. جرّب مرة أخرى.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 14, 31))
        }
        root.addView(TextView(this).apply {
            text = "حدد الهدف ${slot + 1} بإصبعك\nاسحب مستطيلاً حول الزر أو الصورة فقط، ثم اضغط حفظ."
            textSize = 15f; setTextColor(Color.WHITE); gravity = android.view.Gravity.CENTER
            setPadding(14, 12, 14, 12)
        }, LinearLayout.LayoutParams(-1, -2))
        selector = RegionSelectorView(this, bmp)
        root.addView(selector, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10, 8, 10, 12) }
        buttons.addView(Button(this).apply {
            text = "حفظ الهدف"; isAllCaps = false
            setOnClickListener { saveSelection() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(Button(this).apply {
            text = "إلغاء"; isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)
        setContentView(root)
    }

    private fun saveSelection() {
        val bmp = screenshot ?: return
        val rect = selector.bitmapSelection()
        if (rect.width() < 8 || rect.height() < 8) {
            Toast.makeText(this, "حدد مساحة أكبر حول الهدف", Toast.LENGTH_SHORT).show(); return
        }
        try {
            val crop = Bitmap.createBitmap(bmp, rect.left, rect.top, rect.width(), rect.height())
            val file = File(filesDir, "target_slot_${slot + 1}.png")
            FileOutputStream(file).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            crop.recycle()
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putString("slot_image_uri_${slot + 1}", android.net.Uri.fromFile(file).toString())
                .putString("slot_image_name_${slot + 1}", "هدف من الشاشة")
                .putString("slot_word_${slot + 1}", "")
                .putBoolean("slot_enabled_${slot + 1}", true)
                .putInt("sequence_index", slot)
                .putInt("target_capture_slot", -1)
                .apply()
            Toast.makeText(this, "تم حفظ الهدف في الخانة ${slot + 1}", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر حفظ الهدف: ${e.message ?: "خطأ"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        screenshot?.recycle(); screenshot = null
        super.onDestroy()
    }

    private class RegionSelectorView(context: android.content.Context, private val bitmap: Bitmap) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f; private var downY = 0f
        private var currentX = 0f; private var currentY = 0f
        private var imageRect = RectF()


        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.BLACK)
            val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dw = bitmap.width * scale; val dh = bitmap.height * scale
            val left = (width - dw) / 2f; val top = (height - dh) / 2f
            imageRect.set(left, top, left + dw, top + dh)
            canvas.drawBitmap(bitmap, null, imageRect, paint)
            if (downX != currentX || downY != currentY) {
                paint.color = 0x5500B7FF
                paint.style = Paint.Style.FILL
                canvas.drawRect(selectionRect(), paint)
                paint.color = Color.YELLOW; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                canvas.drawRect(selectionRect(), paint)
                paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; currentX = event.x; currentY = event.y; invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { currentX = event.x; currentY = event.y; invalidate(); return true }
                MotionEvent.ACTION_UP -> { currentX = event.x; currentY = event.y; invalidate(); return true }
            }
            return true
        }

        private fun selectionRect() = RectF(min(downX, currentX), min(downY, currentY), max(downX, currentX), max(downY, currentY))

        fun bitmapSelection(): android.graphics.Rect {
            val r = selectionRect()
            val sx = bitmap.width / imageRect.width().coerceAtLeast(1f)
            val sy = bitmap.height / imageRect.height().coerceAtLeast(1f)
            val left = ((r.left - imageRect.left) * sx).toInt().coerceIn(0, bitmap.width - 1)
            val top = ((r.top - imageRect.top) * sy).toInt().coerceIn(0, bitmap.height - 1)
            val right = ((r.right - imageRect.left) * sx).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = ((r.bottom - imageRect.top) * sy).toInt().coerceIn(top + 1, bitmap.height)
            return android.graphics.Rect(left, top, right, bottom)
        }

    }
}
