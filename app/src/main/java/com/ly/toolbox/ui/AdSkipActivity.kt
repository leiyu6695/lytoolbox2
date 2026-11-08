package com.ly.toolbox.ui

import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ly.toolbox.R
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.databinding.ActivityAdSkipBinding
import com.ly.toolbox.service.LyAccessibilityService

/**
 * 广告跳过设置页：开关、跳过次数、生效应用白名单。
 * 实际的识别与点击在 [LyAccessibilityService] 中完成。
 */
class AdSkipActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdSkipBinding

    private val adSwitchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        ConfigStore.setAdSkipEnabled(checked)
        if (checked && !LyAccessibilityService.isConnected()) {
            Toast.makeText(this, R.string.adskip_hint_off, Toast.LENGTH_SHORT).show()
        }
        updateHint()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.init(this)
        binding = ActivityAdSkipBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.swAdSkip.setOnCheckedChangeListener(adSwitchListener)
        binding.btnResetCount.setOnClickListener {
            ConfigStore.resetAdSkipCount()
            binding.tvAdSkipCount.text = "0"
        }
        binding.btnPickApps.setOnClickListener { pickApps() }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        binding.swAdSkip.setOnCheckedChangeListener(null)
        binding.swAdSkip.isChecked = ConfigStore.adSkipEnabled()
        binding.swAdSkip.setOnCheckedChangeListener(adSwitchListener)

        binding.tvAdSkipCount.text = ConfigStore.adSkipCount().toString()
        val scope = ConfigStore.adSkipPackages()
        binding.tvScope.text = if (scope.isEmpty()) {
            getString(R.string.adskip_scope_all)
        } else {
            getString(R.string.adskip_scope_selected, scope.size)
        }
        updateHint()
    }

    private fun updateHint() {
        val connected = LyAccessibilityService.isConnected()
        binding.tvAdSkipHint.setText(
            if (connected) R.string.adskip_hint_ready else R.string.adskip_hint_off
        )
        binding.tvAdSkipHint.setTextColor(
            ContextCompat.getColor(this, if (connected) R.color.success else R.color.danger)
        )
    }

    private fun pickApps() {
        val apps = installedApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.placeholder_toast, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = ConfigStore.adSkipPackages()
        val checked = BooleanArray(apps.size) { selected.contains(apps[it].first) }
        val labels = apps.map { "${it.second}（${it.first}）" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.adskip_pick_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.confirm) { _, _ ->
                val chosen = apps.filterIndexed { index, _ -> checked[index] }
                    .map { it.first }
                    .toSet()
                ConfigStore.setAdSkipPackages(chosen)
                refreshUi()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 有桌面图标的用户应用；排除自己。 */
    private fun installedApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    val label = runCatching {
                        info.loadLabel(packageManager).toString()
                    }.getOrDefault(pkg)
                    pkg to label
                }
                .distinctBy { it.first }
                .filter { it.first != packageName }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }
}
