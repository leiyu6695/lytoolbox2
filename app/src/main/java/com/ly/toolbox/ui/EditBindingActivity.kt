package com.ly.toolbox.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ly.toolbox.R
import com.ly.toolbox.data.ActionStep
import com.ly.toolbox.data.ActionType
import com.ly.toolbox.data.Binding
import com.ly.toolbox.data.ConfigStore
import com.ly.toolbox.data.Trigger
import com.ly.toolbox.databinding.ActivityEditBinding
import com.ly.toolbox.databinding.ItemStepBinding
import java.util.UUID

class EditBindingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private var current = Binding()
    private val steps = mutableListOf<ActionStep>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.init(this)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_ID)
        if (id != null) {
            ConfigStore.bindingById(id)?.let { current = it }
        }
        steps.clear()
        steps.addAll(current.steps)

        binding.etName.setText(current.name)
        setupTriggerPicker()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnAddStep.setOnClickListener { pickAction() }
        binding.btnRecord.setOnClickListener { recordVolumeKey() }
        binding.btnDelete.visibility = if (id == null) View.GONE else View.VISIBLE
        binding.btnDelete.setOnClickListener { deleteBinding() }

        renderSteps()
    }

    private fun setupTriggerPicker() {
        val triggers = Trigger.values()
        val labels = triggers.map { it.label }
        binding.actTrigger.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown, labels)
        )
        binding.actTrigger.setText(Trigger.fromId(current.trigger).label, false)
        binding.actTrigger.setOnClickListener { binding.actTrigger.showDropDown() }
        binding.actTrigger.setOnItemClickListener { _, _, position, _ ->
            current.trigger = triggers[position].id
        }
    }

    private fun renderSteps() {
        binding.llSteps.removeAllViews()
        binding.tvStepsEmpty.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
        steps.forEachIndexed { index, step ->
            val row = ItemStepBinding.inflate(layoutInflater, binding.llSteps, false)
            row.tvIndex.text = "${index + 1}"
            row.tvLabel.text = step.type.label
            val argText = if (step.type.needsArg) step.arg else ""
            row.tvArg.text = argText
            row.tvArg.visibility = if (argText.isBlank()) View.GONE else View.VISIBLE
            row.btnRemove.setOnClickListener {
                steps.removeAt(index)
                renderSteps()
            }
            binding.llSteps.addView(row.root)
        }
    }

    private fun pickAction() {
        val types = ActionType.values()
        val labels = types.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_action)
            .setItems(labels) { _, which ->
                val type = types[which]
                if (type.needsArg) askArg(type) else addStep(ActionStep(type))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askArg(type: ActionType) {
        val input = EditText(this).apply {
            hint = type.argHint
            setText(type.defaultArg)
            setSingleLine()
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(type.label)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ ->
                addStep(ActionStep(type, input.text?.toString()?.trim().orEmpty()))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addStep(step: ActionStep) {
        steps.add(step)
        renderSteps()
    }

    private fun recordVolumeKey() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.record_title)
            .setMessage(R.string.record_message)
            .setNegativeButton(R.string.cancel, null)
            .create()

        var lastUp = 0L
        var lastDown = 0L

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
                return@setOnKeyListener false
            }
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
                return@setOnKeyListener true
            }
            val now = SystemClock.uptimeMillis()
            var result: Trigger? = null
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (lastDown > 0 && now - lastDown in 0L..260L) {
                        result = Trigger.VOL_BOTH
                        lastDown = 0
                    } else if (lastUp > 0 && now - lastUp in 0L..450L) {
                        result = Trigger.VOL_UP_DOUBLE
                        lastUp = 0
                    } else {
                        lastUp = now
                    }
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (lastUp > 0 && now - lastUp in 0L..260L) {
                        result = Trigger.VOL_BOTH
                        lastUp = 0
                    } else if (lastDown > 0 && now - lastDown in 0L..450L) {
                        result = Trigger.VOL_DOWN_DOUBLE
                        lastDown = 0
                    } else {
                        lastDown = now
                    }
                }
            }
            val detected = result
            if (detected != null) {
                current.trigger = detected.id
                binding.actTrigger.setText(detected.label, false)
                toast(getString(R.string.record_success, detected.label))
                dialog.dismiss()
            }
            true
        }
        dialog.show()
    }

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            toast(getString(R.string.need_name))
            return
        }
        if (steps.isEmpty()) {
            toast(getString(R.string.need_one_action))
            return
        }
        current.name = name
        current.steps = steps.toMutableList()
        if (current.id.isBlank()) current.id = UUID.randomUUID().toString()

        val list = ConfigStore.bindings().toMutableList()
        val index = list.indexOfFirst { it.id == current.id }
        if (index >= 0) list[index] = current else list.add(current)
        ConfigStore.saveBindings(list)

        toast(getString(R.string.saved))
        finish()
    }

    private fun deleteBinding() {
        if (current.id.isBlank()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(current.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                ConfigStore.saveBindings(ConfigStore.bindings().filterNot { it.id == current.id })
                toast(getString(R.string.deleted))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_ID = "binding_id"

        fun start(context: Context, id: String?) {
            context.startActivity(
                Intent(context, EditBindingActivity::class.java).putExtra(EXTRA_ID, id)
            )
        }
    }
}
