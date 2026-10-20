package com.skripsi.presensi

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Semua gaya tampilan ada di sini. Ubah warna/nama di bagian atas untuk mengganti tema. */
object Ui {
    // ===== UBAH DI SINI =====
    const val APP_NAME = "Presensi BLE"
    val PRIMARY = Color.parseColor("#1565C0")
    val PRIMARY_LIGHT = Color.parseColor("#CFE0FA")
    val BG = Color.parseColor("#F4F6FA")
    val GREEN = Color.parseColor("#2E7D32")
    val RED = Color.parseColor("#C62828")
    val ORANGE = Color.parseColor("#EF6C00")
    val GRAY = Color.parseColor("#90A4AE")
    val TEXT = Color.parseColor("#263238")
    val MUTED = Color.parseColor("#607D8B")
    val BORDER = Color.parseColor("#D0D7E2")
    // ========================

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(c, radius).toFloat()
        if (stroke != null) setStroke(dp(c, 1), stroke)
    }

    fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun lp(c: Context, top: Int = 0) =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(c, top) }

    fun text(c: Context, t: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(c).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun input(c: Context, hint: String, password: Boolean = false, uri: Boolean = false): EditText =
        EditText(c).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(TEXT)
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                uri -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                else -> InputType.TYPE_CLASS_TEXT
            }
            background = rounded(c, BG, 10, BORDER)
            setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12))
        }

    fun button(c: Context, label: String, color: Int): Button = Button(c).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        background = rounded(c, color, 12)
        minHeight = dp(c, 52)
    }

    fun header(c: Context, title: String, subtitle: String): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(PRIMARY)
        setPadding(dp(c, 24), dp(c, 40), dp(c, 24), dp(c, 48))
        addView(text(c, title, 26f, Color.WHITE, true))
        addView(text(c, subtitle, 14f, PRIMARY_LIGHT))
    }

    fun card(c: Context, title: String?, views: List<View>): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c, Color.WHITE, 16)
        elevation = dp(c, 4).toFloat()
        setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 20))
        if (title != null) addView(text(c, title, 16f, TEXT, true))
        views.forEach { addView(it, lp(c, 10)) }
    }

    /** Baris "label ..... nilai". Mengembalikan baris dan TextView nilainya. */
    fun kv(c: Context, label: String, value: String = "-"): Pair<LinearLayout, TextView> {
        val v = text(c, value, 15f, TEXT, true).apply { gravity = Gravity.END }
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(c, 4), 0, dp(c, 4))
            addView(text(c, label, 14f, MUTED), LinearLayout.LayoutParams(0, WRAP, 2f))
            addView(v, LinearLayout.LayoutParams(0, WRAP, 3f))
        }
        return Pair(row, v)
    }

    /** Satu halaman: header + kartu-kartu (kartu pertama sedikit menimpa header). */
    fun page(c: Context, header: View, cards: List<View>): ScrollView {
        val body = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 16), 0, dp(c, 16), dp(c, 24))
        }
        cards.forEachIndexed { i, v -> body.addView(v, lp(c, if (i == 0) -24 else 16)) }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            addView(header, lp(c))
            addView(body, lp(c))
        }
        return ScrollView(c).apply {
            isFillViewport = true
            setBackgroundColor(BG)
            addView(root)
        }
    }
}
