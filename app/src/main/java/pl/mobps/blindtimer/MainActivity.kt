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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
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

        val footer = TextView(this).apply {
            text = "by BenioYouTube"
            setTextColor(Color.parseColor("#5C6BFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
        }

        val frame = FrameLayout(this).apply { setBackgroundColor(bg) }
        frame.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        frame.addView(footer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { bottomMargin = dp(20) })

        val splash = buildSplash()
        frame.addView(splash, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(frame)
        renderIdle(first = true)

        root.alpha = 0f
        footer.alpha = 0f
        runSplash(splash, root, footer)
    }

    private lateinit var splashLogo: ImageView
    private lateinit var splashTitle: TextView
    private lateinit var splashBy: TextView
    private lateinit var splashSkin: ImageView

    private fun buildSplash(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            isClickable = true
        }

        splashLogo = ImageView(this).apply {
            setImageResource(R.drawable.splash_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.55f
            scaleY = 0.55f
        }
        splashTitle = TextView(this).apply {
            text = "BLIND TIMER"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(18).toFloat()
        }
        splashSkin = ImageView(this).apply {
            setImageResource(R.drawable.skin_figure)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
            translationY = dp(40).toFloat()
        }
        splashBy = TextView(this).apply {
            text = "by BenioYouTube"
            setTextColor(Color.parseColor("#5C6BFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(10).toFloat()
        }

        box.addView(splashLogo, LinearLayout.LayoutParams(dp(190), dp(190)))
        box.addView(splashTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
        box.addView(splashSkin, LinearLayout.LayoutParams(dp(80), dp(160))
            .apply { topMargin = dp(20) })
        box.addView(splashBy, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        return box
    }

    private fun runSplash(splash: View, root: View, footer: View) {
        splashLogo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        splashTitle.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(400).setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        splashSkin.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setStartDelay(750).setDuration(700)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                splashSkin.animate().translationY(-dp(8).toFloat())
                    .setStartDelay(0).setDuration(250)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        splashSkin.animate().translationY(0f)
                            .setDuration(250)
                            .setInterpolator(AccelerateInterpolator())
                            .start()
                    }
                    .start()
            }
            .start()

        splashBy.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(1300).setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        splash.animate()
            .alpha(0f).scaleX(1.08f).scaleY(1.08f)
            .setStartDelay(3200).setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { splash.visibility = View.GONE }
            .start()

        root.animate().alpha(1f).setStartDelay(3200).setDuration(600).start()
        footer.animate().alpha(1f).setStartDelay(3400).setDuration(600).start()
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
