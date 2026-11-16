package com.example.watchguard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

// Warna: tombol terpilih (fokus remote) berwarna KUNING terang dengan teks hitam dan garis putih tebal.
private const val C_BASE = 0xFF2A3A4A.toInt()
private const val C_BASE_LINE = 0xFF5C7A99.toInt()
private const val C_FOCUS = 0xFFFFD600.toInt()
private const val C_PRESS = 0xFF00E676.toInt()
private const val C_DISABLED = 0xFF37474F.toInt()

fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

fun Context.rounded(fill: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dp(8).toFloat()
    setColor(fill)
    if (stroke != null) setStroke(dp(4), stroke)
}

private fun textColors(normal: Int): ColorStateList = ColorStateList(
    arrayOf(
        intArrayOf(-android.R.attr.state_enabled),
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_focused),
        intArrayOf()
    ),
    intArrayOf(Color.GRAY, Color.BLACK, Color.BLACK, normal)
)

fun Context.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(48), dp(32), dp(48), dp(32))
    setBackgroundColor(0xFF101418.toInt())
}

fun Context.label(text: String, size: Float = 20f, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }

fun Context.button(text: String, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        isFocusable = true
        stateListAnimator = null
        background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), rounded(C_DISABLED))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(C_PRESS, Color.WHITE))
            addState(intArrayOf(android.R.attr.state_focused), rounded(C_FOCUS, Color.WHITE))
            addState(intArrayOf(), rounded(C_BASE, C_BASE_LINE))
        }
        setTextColor(textColors(Color.WHITE))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

fun Context.pinField(): EditText = EditText(this).apply {
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    hint = "PIN"
    textSize = 24f
    setTextColor(Color.WHITE)
    setHintTextColor(Color.GRAY)
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(0xFF1B2733.toInt(), C_FOCUS))
        addState(intArrayOf(), rounded(0xFF1B2733.toInt(), C_BASE_LINE))
    }
    setPadding(dp(14), dp(10), dp(14), dp(10))
}

/** Kotak centang: saat dipilih dengan remote, latar jadi kuning dan teks hitam. */
fun CheckBox.highlightOnFocus() {
    isFocusable = true
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), context.rounded(C_FOCUS, Color.WHITE))
        addState(intArrayOf(), context.rounded(Color.TRANSPARENT))
    }
    setTextColor(textColors(Color.WHITE))
    setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
}
