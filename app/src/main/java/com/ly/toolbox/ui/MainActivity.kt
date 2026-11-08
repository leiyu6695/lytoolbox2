package com.ly.toolbox.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ly.toolbox.R
import com.ly.toolbox.data.Trigger
import com.ly.toolbox.databinding.ActivityMainBinding
import com.ly.toolbox.service.ActionRunner

/**
 * 首页：只负责"工具入口"，具体权限与快捷键配置放在 [SettingsActivity]。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardOverlay.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.cardSkipAd.setOnClickListener { showPlaceholder() }
        binding.cardSlot1.setOnClickListener { showPlaceholder() }
        binding.cardSlot2.setOnClickListener { showPlaceholder() }

        handleTriggerIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTriggerIntent(intent)
    }

    private fun showPlaceholder() {
        Toast.makeText(this, R.string.placeholder_toast, Toast.LENGTH_SHORT).show()
    }

    /**
     * 桌面快捷方式仍直接启动 MainActivity，这里执行动作后立即退出，
     * 不打断用户当前正在使用的 App。
     */
    private fun handleTriggerIntent(intent: Intent?) {
        val triggerId = intent?.getStringExtra(EXTRA_TRIGGER) ?: return
        ActionRunner.run(this, Trigger.fromId(triggerId))
        if (intent.action == Intent.ACTION_VIEW) finish()
    }

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
