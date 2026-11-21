package pl.mobps.blindtimer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToLong
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
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
        const val REQ_SONG = 42
    }

    private val doubleTapMs = 400L

    private val prefs by lazy { getSharedPreferences("blindtimer", MODE_PRIVATE) }
    private var editMode = false
    private var targetMs = 1000L
    private var tolMs = 10L
    private var songUri: String? = null
    private var songName: String? = null

    private var player: MediaPlayer? = null
    private var playerReady = false
    private var playAfterPrepare = false

    private lateinit var gear: TextView
    private lateinit var settingsPanel: FrameLayout
    private lateinit var swEdit: Switch
    private lateinit var etTarget: EditText
    private lateinit var etTol: EditText
    private lateinit var songLabel: TextView
    private val grey = Color.parseColor("#8A8A99")

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
        volumeControlStream = AudioManager.STREAM_MUSIC
        loadPrefs()
        if (editMode) preparePlayer()

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

        gear = TextView(this).apply {
            text = "⚙︎"
            setTextColor(grey)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            setOnClickListener { showSettings() }
        }
        frame.addView(gear, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { topMargin = dp(8); rightMargin = dp(8) })

        settingsPanel = buildSettings()
        frame.addView(settingsPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val splash = buildSplash()
        frame.addView(splash, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(frame)
        renderIdle(first = true)

        root.alpha = 0f
        footer.alpha = 0f
        gear.alpha = 0f
        runSplash(splash, root, footer)
        gear.animate().alpha(1f).setStartDelay(3800).setDuration(600).start()
    }

    private fun loadPrefs() {
        editMode = prefs.getBoolean("edit_mode", false)
        targetMs = prefs.getLong("target_ms", 1000L)
        tolMs = prefs.getLong("tol_ms", 10L)
        songUri = prefs.getString("song_uri", null)
        songName = prefs.getString("song_name", null)
    }

    private fun pill(label: String, fill: Int, textColor: Int): TextView =
        TextView(this).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(fill)
            }
            isClickable = true
        }

    private fun fieldLabel(t: String): TextView =
        TextView(this).apply {
            text = t
            setTextColor(grey)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

    private fun input(initial: String, decimal: Boolean): EditText =
        EditText(this).apply {
            setText(initial)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(bg)
                setStroke(dp(1), Color.parseColor("#2A2A33"))
            }
        }

    private fun buildSettings(): FrameLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#16161F"))
                setStroke(dp(1), Color.parseColor("#2A2A33"))
            }
        }

        val title = TextView(this).apply {
            text = "USTAWIENIA"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.2f
        }
        swEdit = Switch(this).apply {
            text = "Edit mode"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        val desc = fieldLabel("Wpisz czas. Jeśli trafisz w niego z tolerancją, zagra piosenka.")
        etTarget = input("1.00", true)
        etTol = input("10", false)
        songLabel = fieldLabel("")

        val pick = pill("Zmień piosenkę (opcjonalnie)", Color.parseColor("#2A2A33"), Color.WHITE)
        val test = pill("Odtwórz test", Color.parseColor("#2A2A33"), Color.WHITE)
        val save = pill("Zapisz i zamknij", green, bg)
        pick.setOnClickListener { pickSong() }
        test.setOnClickListener { if (saveSettings(false)) playSong() }
        save.setOnClickListener { saveSettings(true) }

        fun lp(top: Int) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

        card.addView(title, lp(0))
        card.addView(swEdit, lp(16))
        card.addView(desc, lp(6))
        card.addView(fieldLabel("Docelowy czas (sekundy)"), lp(16))
        card.addView(etTarget, lp(6))
        card.addView(fieldLabel("Tolerancja (milisekundy, 10 = od 0.99 do 1.01)"), lp(14))
        card.addView(etTol, lp(6))
        card.addView(songLabel, lp(18))
        card.addView(pick, lp(8))
        card.addView(test, lp(8))
        card.addView(save, lp(18))

        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        holder.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(holder, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F2000000"))
            visibility = View.GONE
            isClickable = true
            addView(scroll, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun showSettings() {
        swEdit.isChecked = editMode
        etTarget.setText(String.format(Locale.US, "%.3f", targetMs / 1000.0))
        etTol.setText(tolMs.toString())
        songLabel.text = "Piosenka: " + (songName ?: "Math edit (domyślna)")
        settingsPanel.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(settingsPanel.windowToken, 0)
    }

    private fun saveSettings(close: Boolean): Boolean {
        val sec = etTarget.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (sec == null || sec <= 0.0) {
            Toast.makeText(this, "Wpisz poprawny czas, np. 1.00", Toast.LENGTH_SHORT).show()
            return false
        }
        val tol = etTol.text.toString().trim().toLongOrNull() ?: 10L
        targetMs = (sec * 1000.0).roundToLong()
        tolMs = tol.coerceIn(0L, 1000L)
        editMode = swEdit.isChecked
        prefs.edit()
            .putBoolean("edit_mode", editMode)
            .putLong("target_ms", targetMs)
            .putLong("tol_ms", tolMs)
            .apply()
        if (editMode && player == null) preparePlayer()
        if (close) {
            hideKeyboard()
            settingsPanel.visibility = View.GONE
            when (state) {
                STATE_ZERO -> renderIdle(first = false)
                STATE_STOPPED -> renderStopped(stoppedElapsed)
                else -> {}
            }
        }
        return true
    }

    private fun pickSong() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, REQ_SONG)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SONG || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        songUri = uri.toString()
        songName = queryName(uri)
        prefs.edit().putString("song_uri", songUri).putString("song_name", songName).apply()
        songLabel.text = "Piosenka: " + (songName ?: "wybrano")
        releasePlayer()
        preparePlayer()
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun preparePlayer() {
        val uri = songUri
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            if (uri != null) {
                mp.setDataSource(this, Uri.parse(uri))
            } else {
                val afd = resources.openRawResourceFd(R.raw.hit_song)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
            mp.setOnPreparedListener {
                playerReady = true
                if (playAfterPrepare) {
                    playAfterPrepare = false
                    it.start()
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                playerReady = false
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (_: Exception) {
            player = null
            playerReady = false
        }
    }

    private fun playSong() {
        if (player == null) preparePlayer()
        val mp = player
        if (mp == null) {
            Toast.makeText(this, "Nie można odtworzyć piosenki", Toast.LENGTH_SHORT).show()
            return
        }
        if (playerReady) {
            try {
                mp.seekTo(0)
                mp.start()
            } catch (_: Exception) {}
        } else {
            playAfterPrepare = true
        }
    }

    private fun stopSong() {
        playAfterPrepare = false
        try {
            if (playerReady) {
                player?.pause()
                player?.seekTo(0)
            }
        } catch (_: Exception) {}
    }

    private fun releasePlayer() {
        playerReady = false
        playAfterPrepare = false
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (settingsPanel.visibility == View.VISIBLE) {
            hideKeyboard()
            settingsPanel.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        stopSong()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private lateinit var splashLogo: ImageView
    private lateinit var splashTitle: TextView
    private lateinit var splashBy: TextView
    private lateinit var splashSkin: FrameLayout
    private lateinit var splashArm: ImageView

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
        val skinBody = ImageView(this).apply {
            setImageResource(R.drawable.skin_body)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        splashArm = ImageView(this).apply {
            setImageResource(R.drawable.skin_arm)
            scaleType = ImageView.ScaleType.FIT_CENTER
            pivotX = dp(80) * 0.875f
            pivotY = dp(160) * 0.28f
        }
        splashSkin = FrameLayout(this).apply {
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
            translationY = dp(40).toFloat()
            addView(skinBody, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(splashArm, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
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

    private fun waveHello() {
        val raise = ObjectAnimator.ofFloat(splashArm, "rotation", 0f, -125f).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
        }
        val wave = ObjectAnimator.ofFloat(splashArm, "rotation", -125f, -165f).apply {
            duration = 200
            repeatCount = 5
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val lower = ObjectAnimator.ofFloat(splashArm, "rotation", -125f, 0f).apply {
            duration = 350
            interpolator = AccelerateInterpolator()
        }
        AnimatorSet().apply {
            playSequentially(raise, wave, lower)
            start()
        }
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
            .withEndAction { waveHello() }
            .start()

        splashBy.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(1300).setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        splash.animate()
            .alpha(0f).scaleX(1.08f).scaleY(1.08f)
            .setStartDelay(3600).setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { splash.visibility = View.GONE }
            .start()

        root.animate().alpha(1f).setStartDelay(3600).setDuration(600).start()
        footer.animate().alpha(1f).setStartDelay(3800).setDuration(600).start()
    }

    private fun onButton(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (state) {
            STATE_ZERO -> {
                stopSong()
                startTime = SystemClock.elapsedRealtime()
                state = STATE_RUNNING
                renderRunning()
            }
            STATE_RUNNING -> {
                val now = SystemClock.elapsedRealtime()
                if (lastStoppedTap != 0L && now - lastStoppedTap <= doubleTapMs) {
                    lastStoppedTap = 0L
                    state = STATE_ZERO
                    stopSong()
                    renderIdle(first = false)
                } else {
                    stoppedElapsed = now - startTime
                    state = STATE_STOPPED
                    lastStoppedTap = 0L
                    renderStopped(stoppedElapsed)
                    if (editMode && abs(stoppedElapsed - targetMs) <= tolMs) playSong()
                }
            }
            else -> {
                val now = SystemClock.elapsedRealtime()
                stopSong()
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
        status.setTextColor(grey)
        result.text = if (editMode) "0.000 s" else "0.00 s"
        val base = if (first) "Naciśnij START, a potem zatrzymaj we właściwym momencie.\nCzasu nie zobaczysz do końca." else "Naciśnij START, liczba zniknie"
        hint.text = if (editMode) "EDIT MODE: cel ${formatSec(targetMs)} (±$tolMs ms)\n$base" else base
    }

    private fun renderRunning() {
        button.text = "STOP"
        button.background = circle(red)
        status.text = "LICZYSZ…"
        status.setTextColor(grey)
        result.text = ""
        hint.text = if (editMode) "EDIT MODE: cel ${formatSec(targetMs)} (±$tolMs ms)\nCzas jest ukryty" else "Czas jest ukryty"
    }

    private fun renderStopped(ms: Long) {
        button.text = "RESET"
        button.background = circle(Color.parseColor("#FFC24D"))
        result.text = format(ms)
        val base = "1 klik: czas znika i liczy dalej\n2 szybkie kliki: wyzeruj do 0"
        if (editMode) {
            val diff = ms - targetMs
            val hit = abs(diff) <= tolMs
            status.text = if (hit) "TRAFIONE!" else "TWÓJ CZAS"
            status.setTextColor(if (hit) green else grey)
            val sign = if (diff > 0) "+" else ""
            hint.text = "Cel: ${formatSec(targetMs)} | różnica: $sign$diff ms\n$base"
        } else {
            status.text = "TWÓJ CZAS"
            status.setTextColor(grey)
            hint.text = base
        }
    }

    private fun formatSec(ms: Long): String =
        String.format(Locale.US, "%d.%03d s", ms / 1000, ms % 1000)

    private fun format(ms: Long): String {
        if (editMode) {
            val msPart = (ms % 1000).toInt()
            val totalSec = (ms / 1000).toInt()
            val sec = totalSec % 60
            val min = totalSec / 60
            return if (min > 0) {
                String.format(Locale.US, "%d:%02d.%03d", min, sec, msPart)
            } else {
                String.format(Locale.US, "%d.%03d s", sec, msPart)
            }
        }
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
