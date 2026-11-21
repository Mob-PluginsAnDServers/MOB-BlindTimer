package pl.mobps.blindtimer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private companion object {
        const val STATE_ZERO = 0
        const val STATE_RUNNING = 1
        const val STATE_STOPPED = 2
    }

    private val doubleTapMs = 400L

    private var state = STATE_ZERO
    private var startTime = 0L
    private var lastStoppedTap = 0L
    private var stoppedElapsed = 0L

    private lateinit var button: TextView
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var hint: TextView

    private val green = Color.parseColor("#00FFA3")
    private val red = Color.parseColor("#FF4D6D")
    private val bg = Color.parseColor("#0D0D12")

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        status = TextView(this).apply {
            text = "BLIND TIMER"
            setTextColor(Color.parseColor("#8A8A99"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
        }

        result = TextView(this).apply {
            text = ""
            setTextColor(green)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(90)
        }

        button = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(bg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            setOnClickListener { onButton(it) }
        }

        hint = TextView(this).apply {
            setTextColor(Color.parseColor("#8A8A99"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }

        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(result, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24); bottomMargin = dp(24) })
        root.addView(button, LinearLayout.LayoutParams(dp(240), dp(240)))
        root.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(32) })

        setContentView(root)
        renderIdle(first = true)
    }

    private fun onButton(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (state) {
            STATE_ZERO -> {
                startTime = SystemClock.elapsedRealtime()
                state = STATE_RUNNING
                renderRunning()
            }
            STATE_RUNNING -> {
                val now = SystemClock.elapsedRealtime()
                if (lastStoppedTap != 0L && now - lastStoppedTap <= doubleTapMs) {
                    lastStoppedTap = 0L
                    state = STATE_ZERO
                    renderIdle(first = false)
                } else {
                    stoppedElapsed = now - startTime
                    state = STATE_STOPPED
                    lastStoppedTap = 0L
                    renderStopped(stoppedElapsed)
                }
            }
            else -> {
                val now = SystemClock.elapsedRealtime()
                startTime = now - stoppedElapsed
                lastStoppedTap = now
                state = STATE_RUNNING
                renderRunning()
            }
        }
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun renderIdle(first: Boolean) {
        button.text = "START"
        button.background = circle(green)
        status.text = "BLIND TIMER"
        result.text = "0.00 s"
        hint.text = if (first) "Naciśnij START, a potem zatrzymaj we właściwym momencie.\nCzasu nie zobaczysz do końca." else "Naciśnij START, liczba zniknie"
    }

    private fun renderRunning() {
        button.text = "STOP"
        button.background = circle(red)
        status.text = "LICZYSZ…"
        result.text = ""
        hint.text = "Czas jest ukryty"
    }

    private fun renderStopped(ms: Long) {
        button.text = "RESET"
        button.background = circle(Color.parseColor("#FFC24D"))
        status.text = "TWÓJ CZAS"
        result.text = format(ms)
        hint.text = "1 klik: czas znika i liczy dalej\n2 szybkie kliki: wyzeruj do 0"
    }

    private fun format(ms: Long): String {
        val totalCs = ms / 10
        val cs = (totalCs % 100).toInt()
        val totalSec = (totalCs / 100).toInt()
        val sec = totalSec % 60
        val min = totalSec / 60
        return if (min > 0) {
            String.format(Locale.US, "%d:%02d.%02d", min, sec, cs)
        } else {
            String.format(Locale.US, "%d.%02d s", sec, cs)
        }
    }
}
