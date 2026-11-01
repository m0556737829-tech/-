package com.mtech.mp4compat

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.effect.Presentation
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var chooseButton: Button
    private lateinit var convertButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var shareButton: Button
    private lateinit var profileSpinner: Spinner
    private val profiles = listOf("Canon — תאימות רחבה · 1080p", "Android רגיל · 1080p", "Android ישן / תאימות מרבית · 720p", "קובץ קטן יותר · 720p")
    private var selectedUri: Uri? = null
    private var selectedName: String = "video"
    private var working = false
    private var lastOutput: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(22))
            setBackgroundColor(0xFF0B1020.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(TextView(this).apply {
            text = "ממיר MP4"
            textSize = 30f
            setTextColor(0xFFF2F5FF.toInt())
            gravity = Gravity.RIGHT
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "המרת סרטונים לתאימות רחבה עם מצלמות Canon ונגני Android"
            textSize = 16f
            setTextColor(0xFFB7C4DD.toInt())
            gravity = Gravity.RIGHT
            setPadding(0, dp(8), 0, dp(22))
        }, matchWrap())
        chooseButton = Button(this).apply {
            text = "1. בחירת סרטון"
            setOnClickListener { openPicker() }
        }
        content.addView(chooseButton, matchWrap())
        convertButton = Button(this).apply {
            text = "2. המרה ל‑MP4 · 1080p"
            isEnabled = false
            setOnClickListener { startConversion() }
        }
        content.addView(convertButton, matchWrap().apply { topMargin = dp(10) })
        content.addView(TextView(this).apply {
            text = "3. בחירת יעד וקידוד"
            textSize = 16f
            setTextColor(0xFFF2F5FF.toInt())
            gravity = Gravity.RIGHT
            setPadding(0, dp(18), 0, dp(6))
        }, matchWrap())
        profileSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, profiles)
            setSelection(0)
        }
        content.addView(profileSpinner, matchWrap())
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
        }
        content.addView(progress, matchWrap().apply { topMargin = dp(14) })
        status = TextView(this).apply {
            text = "בחר סרטון כדי להתחיל. הסרטון המקורי לא ישתנה."
            textSize = 15f
            setTextColor(0xFFF2F5FF.toInt())
            gravity = Gravity.RIGHT
            setPadding(0, dp(16), 0, dp(16))
        }
        content.addView(status, matchWrap())
        content.addView(TextView(this).apply {
            text = "הפרופילים משתמשים בקובץ MP4 עם וידאו H.264/AVC ושמע AAC. בחירת Canon מכוונת לתאימות רחבה; פרופיל 720p מיועד לנגנים ישנים יותר או לקובץ קטן יותר. לא ניתן להבטיח תאימות לכל דגם בלי מספר הדגם.\n\nההמרה מתבצעת במכשיר והסרטון המקורי נשאר ללא שינוי."
            textSize = 14f
            setTextColor(0xFFB7C4DD.toInt())
            gravity = Gravity.RIGHT
            setPadding(0, dp(8), 0, dp(18))
        }, matchWrap())
        shareButton = Button(this).apply {
            text = "שיתוף הסרטון שהומר"
            isEnabled = false
            setOnClickListener { lastOutput?.let { shareOutput(it) } }
        }
        content.addView(shareButton, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    @Deprecated("Use Activity Result API in future updates")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri
            selectedName = queryDisplayName(uri) ?: "video"
            convertButton.isEnabled = true
            progress.progress = 0
            status.text = "נבחר: $selectedName\nלחץ על כפתור ההמרה כדי ליצור קובץ חדש."
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    private fun startConversion() {
        val uri = selectedUri ?: return
        if (working) return
        working = true
        convertButton.isEnabled = false
        chooseButton.isEnabled = false
        shareButton.isEnabled = false
        progress.isIndeterminate = true
        status.text = "מכין את ההמרה…\nייתכן שיידרשו כמה דקות."
        val temp = File(cacheDir, "converted_${System.currentTimeMillis()}.mp4")
        try {
            val selectedProfile = profileSpinner.selectedItemPosition
            val targetHeight = if (selectedProfile >= 2) 720 else 1080
            val effects = Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight)))
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(effects)
                .build()
            val transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        runOnUiThread {
                            try {
                                val saved = copyToMovies(temp, selectedName)
                                lastOutput = saved
                                progress.isIndeterminate = false
                                progress.progress = 100
                                status.text = "ההמרה הושלמה!\nפרופיל: ${profiles[profileSpinner.selectedItemPosition]}\nקידוד: MP4 / H.264 + AAC\nהקובץ נשמר בתיקיית Movies/MP4 Converter."
                                shareButton.isEnabled = true
                                Toast.makeText(this@MainActivity, "הסרטון הומר ונשמר", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                status.text = "ההמרה הצליחה, אך שמירת הקובץ נכשלה: ${e.message}"
                            } finally {
                                working = false
                                convertButton.isEnabled = selectedUri != null
                                chooseButton.isEnabled = true
                                temp.delete()
                            }
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        runOnUiThread {
                            status.text = "ההמרה נכשלה. ייתכן שהקובץ פגום או שהקידוד אינו נתמך.\nפרטים: ${exportException.message ?: "שגיאה לא ידועה"}"
                            progress.isIndeterminate = false
                            working = false
                            convertButton.isEnabled = selectedUri != null
                            chooseButton.isEnabled = true
                            temp.delete()
                        }
                    }
                })
                .build()
            transformer.start(edited, temp.absolutePath)
        } catch (e: Exception) {
            working = false
            convertButton.isEnabled = selectedUri != null
            chooseButton.isEnabled = true
            progress.isIndeterminate = false
            status.text = "לא ניתן להתחיל את ההמרה: ${e.message}"
            temp.delete()
        }
    }

    private fun copyToMovies(temp: File, originalName: String): Uri {
        val base = originalName.substringBeforeLast('.', originalName)
            .replace(Regex("[^A-Za-z0-9א-ת_-]"), "_").take(60).ifBlank { "video" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "${base}_compatible_${timestamp}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MP4 Converter")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("לא ניתן ליצור קובץ במדיה")
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(temp).use { it.copyTo(out) }
                } ?: throw IllegalStateException("לא ניתן לפתוח קובץ יעד")
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
                return uri
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val folder = File(dir, "MP4 Converter").apply { mkdirs() }
        val outFile = File(folder, displayName)
        FileInputStream(temp).use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
        return Uri.fromFile(outFile)
    }

    private fun shareOutput(uri: Uri) {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "הקובץ נשמר באחסון האפליקציה במכשיר זה.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "שיתוף סרטון MP4"))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)

    companion object { private const val REQUEST_VIDEO = 7001 }
}
