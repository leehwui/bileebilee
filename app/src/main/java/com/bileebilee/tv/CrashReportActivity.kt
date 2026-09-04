package com.bileebilee.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences(
            BileebileeApplication.CRASH_PREFERENCES,
            MODE_PRIVATE
        )
        val crash = preferences.getString(BileebileeApplication.LAST_CRASH_KEY, null)
        if (crash == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(40), dp(64), dp(40))
            setBackgroundColor(Color.rgb(16, 17, 20))
        }
        root.addView(TextView(this).apply {
            text = "Bileebilee startup crash"
            setTextColor(Color.rgb(251, 114, 153))
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Photograph this screen for diagnosis. Use the D-pad to scroll if needed."
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, dp(12), 0, dp(16))
        })

        val crashText = TextView(this).apply {
            text = crash
            setTextColor(Color.rgb(220, 220, 224))
            textSize = 15f
            typeface = Typeface.MONOSPACE
            isFocusable = true
            setTextIsSelectable(false)
        }
        root.addView(ScrollView(this).apply {
            isFocusable = true
            addView(crashText)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        root.addView(Button(this).apply {
            text = "Clear report and retry"
            isFocusable = true
            gravity = Gravity.CENTER
            setOnClickListener {
                preferences.edit().remove(BileebileeApplication.LAST_CRASH_KEY).commit()
                startActivity(Intent(this@CrashReportActivity, MainActivity::class.java))
                finish()
            }
        }, LinearLayout.LayoutParams(dp(320), dp(58)).apply {
            topMargin = dp(16)
        })

        setContentView(root)
    }
}
