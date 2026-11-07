package com.ly.toolbox.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ly.toolbox.R
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.data.Trigger
import com.ly.toolbox.databinding.ActivityMainBinding
import com.ly.toolbox.databinding.ItemBindingBinding
import com.ly.toolbox.service.ActionRunner
import com.ly.toolbox.service.LyAccessibilityService
import com.ly.toolbox.service.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateStatus() }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val ballSwitchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        if (!checked) {
            ConfigStore.setBallEnabled(false)
            OverlayService.stop(this)
            return@OnCheckedChangeListener
        }
        if (!Settings.canDrawOverlays(this)) {
            setBallChecked(false)
            openOverlaySettings()
            return@OnCheckedChangeListener
        }
        ConfigStore.setBallEnabled(true)
        OverlayService.start(this)
        binding.root.postDelayed({ updateStatus() }, 400)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAccess.setOnClickListener { openAccessibilitySettings() }
        binding.btnOverlay.setOnClickListener { openOverlaySettings() }
        binding.btnAdd.setOnClickListener { EditBindingActivity.start(this, null) }
        binding.swBall.setOnCheckedChangeListener(ballSwitchListener)

        askNotificationPermission()
        handleTriggerIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTriggerIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        renderBindings()
    }

    private fun handleTriggerIntent(intent: Intent?) {
        val triggerId = intent?.getStringExtra(EXTRA_TRIGGER) ?: return
        ActionRunner.run(this, Trigger.fromId(triggerId))
        // 从桌面快捷方式启动时，执行完立即退出，避免每次闪一下界面
        if (intent.action == Intent.ACTION_VIEW) finish()
    }

    private fun updateStatus() {
        val accessibility = isAccessibilityEnabled()
        binding.tvAccState.text =
            getString(if (accessibility) R.string.state_on else R.string.state_off)
        binding.tvAccState.setTextColor(
            ContextCompat.getColor(this, if (accessibility) R.color.success else R.color.danger)
        )
        binding.btnAccess.text =
            getString(if (accessibility) R.string.settings else R.string.grant)

        val overlay = Settings.canDrawOverlays(this)
        binding.tvOverlayState.text = getString(if (overlay) R.string.state_on else R.string.state_off)
        binding.tvOverlayState.setTextColor(
            ContextCompat.getColor(this, if (overlay) R.color.success else R.color.danger)
        )
        binding.btnOverlay.text = getString(if (overlay) R.string.settings else R.string.grant)

        setBallChecked(OverlayService.isRunning)
        if (overlay && ConfigStore.ballEnabled() && !OverlayService.isRunning) {
            OverlayService.start(this)
            binding.root.postDelayed({ setBallChecked(OverlayService.isRunning) }, 500)
        }
    }

    private fun setBallChecked(checked: Boolean) {
        binding.swBall.setOnCheckedChangeListener(null)
        binding.swBall.isChecked = checked
        binding.swBall.setOnCheckedChangeListener(ballSwitchListener)
    }

    private fun renderBindings() {
        binding.llBindings.removeAllViews()
        val all = ConfigStore.bindings()
        val sorted = all.sortedBy { Trigger.fromId(it.trigger).ordinal }
        binding.tvBindingsEmpty.visibility =
            if (sorted.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        sorted.forEach { item ->
            val row = ItemBindingBinding.inflate(layoutInflater, binding.llBindings, false)
            row.tvName.text = item.name.ifBlank { "未命名快捷键" }
            row.tvTrigger.text = Trigger.fromId(item.trigger).label
            row.tvSummary.text = if (item.steps.isEmpty()) {
                getString(R.string.summary_empty)
            } else {
                item.steps.joinToString("  →  ") { it.summary() }
            }
            row.swEnabled.setOnCheckedChangeListener(null)
            row.swEnabled.isChecked = item.enabled
            row.swEnabled.setOnCheckedChangeListener { _, checked ->
                item.enabled = checked
                ConfigStore.saveBindings(all)
            }
            row.root.setOnClickListener { EditBindingActivity.start(this, item.id) }
            binding.llBindings.addView(row.root)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (LyAccessibilityService.isConnected()) return true
        val expected = ComponentName(this, LyAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayLauncher.launch(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
