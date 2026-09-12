package com.nepalime.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider

class LogViewerActivity : Activity() {

    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        root.addView(TextView(this).apply {
            text = "Nepali IME — Debug Log"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        })

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refreshBtn = Button(this).apply { text = "Refresh" }
        val shareBtn = Button(this).apply { text = "Share" }
        val clearBtn = Button(this).apply { text = "Clear" }
        buttonRow.addView(refreshBtn)
        buttonRow.addView(shareBtn)
        buttonRow.addView(clearBtn)
        root.addView(buttonRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        logText = TextView(this).apply {
            setTextIsSelectable(true)
            setPadding(8, 24, 8, 8)
        }
        scroll.addView(logText)
        root.addView(scroll)

        setContentView(root)

        refreshBtn.setOnClickListener { refresh() }
        shareBtn.setOnClickListener { share() }
        clearBtn.setOnClickListener { Logger.clear(applicationContext); refresh() }

        refresh()
    }

    private fun refresh() {
        logText.text = Logger.readAll(applicationContext)
    }

    private fun share() {
        val file = Logger.getFile(applicationContext)
        if (!file.exists() || file.length() == 0L) {
            logText.text = "Nothing to share yet — type something with the IME active first."
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Nepali IME log"))
    }
}
