package com.novelarrow.reader.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.novelarrow.reader.R
import io.noties.markwon.Markwon

class DocumentationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rawMarkdownString = try {
            resources.openRawResource(R.raw.api_docs).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            "# API Documentation\n\nFailed to load api_docs.md from res/raw."
        }

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top Header Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val backButton = Button(this).apply {
            text = "← Back"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        topBar.addView(backButton)

        val titleView = TextView(this).apply {
            text = "Userscript Documentation"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 16
            }
        }
        topBar.addView(titleView)

        val topCopyButton = Button(this).apply {
            text = "Copy"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#388E3C"))
            setOnClickListener { copyMarkdownToClipboard(rawMarkdownString) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        topBar.addView(topCopyButton)

        contentLayout.addView(topBar)

        // ScrollView containing TextView
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            clipToPadding = false
            setPadding(36, 24, 36, 180)
        }

        val textView = TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 15f
            setLineSpacing(8f, 1.15f)
        }
        scrollView.addView(textView)
        contentLayout.addView(scrollView)
        rootLayout.addView(contentLayout)

        // Floating Action Button for Copy as Markdown
        val fab = Button(this).apply {
            text = "📋 Copy Markdown"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(40, 24, 40, 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 48, 64)
            }
            setOnClickListener {
                copyMarkdownToClipboard(rawMarkdownString)
            }
        }
        rootLayout.addView(fab)

        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentLayout.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        // Render Markdown using Markwon
        val markwon = Markwon.create(this)
        markwon.setMarkdown(textView, rawMarkdownString)
    }

    private fun copyMarkdownToClipboard(rawMarkdownString: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("API Docs", rawMarkdownString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
