package com.moonkey.androidagent

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/** MainActivity - Simple UI for entering goal and starting agent. */
class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var goalInput: TextInputEditText
    private lateinit var startButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        goalInput = findViewById(R.id.goalInput)
        startButton = findViewById(R.id.startButton)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        statusText = findViewById(R.id.statusText)

        // Set up status callback
        AgentService.statusCallback = { status -> runOnUiThread { statusText.append("\n$status") } }

        startButton.setOnClickListener {
            val apiKey = apiKeyInput.text?.toString() ?: ""
            val goal = goalInput.text?.toString() ?: ""

            if (apiKey.isBlank()) {
                statusText.text = "❌ Please enter your OpenAI API key"
                return@setOnClickListener
            }

            if (goal.isBlank()) {
                statusText.text = "❌ Please enter a goal"
                return@setOnClickListener
            }

            val service = AgentService.instance
            if (service == null) {
                statusText.text =
                        "❌ Accessibility Service not enabled!\nPlease enable it in Settings."
                return@setOnClickListener
            }

            statusText.text = ""
            service.runAgent(goal, apiKey)
        }

        accessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update status based on service state
        if (AgentService.instance != null) {
            statusText.text = "✅ Accessibility Service is enabled. Ready to run."
        } else {
            statusText.text = "⚠️ Please enable Accessibility Service first."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AgentService.statusCallback = null
    }
}
