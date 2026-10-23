package com.netswitch.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuButton: Button
    private lateinit var currentMode: TextView
    private lateinit var message: TextView
    private lateinit var modeButtons: Map<NetMode, Button>

    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { updateState() } }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { runOnUiThread { updateState() } }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { updateState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        shizukuStatus = requireViewById<TextView>(R.id.shizuku_status)
        shizukuButton = requireViewById<Button>(R.id.shizuku_button)
        currentMode = requireViewById<TextView>(R.id.current_mode)
        message = requireViewById<TextView>(R.id.message)
        modeButtons = mapOf(
            NetMode.G3 to requireViewById<Button>(R.id.set_3g),
            NetMode.G4 to requireViewById<Button>(R.id.set_4g),
            NetMode.G5 to requireViewById<Button>(R.id.set_5g),
        )
        modeButtons.forEach { (mode, button) -> button.setOnClickListener { switchTo(mode) } }
        shizukuButton.setOnClickListener { onShizukuButton() }

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        scope.cancel()
        super.onDestroy()
    }

    private fun updateState() {
        val state = NetController.shizukuState(this)
        shizukuStatus.setText(
            when (state) {
                ShizukuState.NOT_INSTALLED -> R.string.shizuku_not_installed
                ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
                ShizukuState.NO_PERMISSION -> R.string.shizuku_no_permission
                ShizukuState.READY -> R.string.shizuku_ready
            },
        )
        shizukuButton.visibility = if (state == ShizukuState.READY) View.GONE else View.VISIBLE
        shizukuButton.setText(
            when (state) {
                ShizukuState.NOT_INSTALLED -> R.string.action_install
                ShizukuState.NOT_RUNNING -> R.string.action_open_shizuku
                else -> R.string.action_grant
            },
        )
        modeButtons.values.forEach { it.isEnabled = state == ShizukuState.READY }
        showMode(NetController.cachedMode(this))

        if (state == ShizukuState.READY) {
            scope.launch { showMode(NetController.refresh(this@MainActivity)) }
        }
    }

    private fun showMode(mode: NetMode?) {
        currentMode.text = mode?.label ?: "—"
        modeButtons.forEach { (m, button) -> button.isSelected = m == mode }
    }

    private fun onShizukuButton() {
        when (NetController.shizukuState(this)) {
            ShizukuState.NOT_INSTALLED -> startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")),
            )
            ShizukuState.NOT_RUNNING -> packageManager
                .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?.let { startActivity(it) }
            ShizukuState.NO_PERMISSION -> Shizuku.requestPermission(REQUEST_CODE)
            ShizukuState.READY -> Unit
        }
    }

    private fun switchTo(mode: NetMode) {
        message.setText(R.string.status_switching)
        modeButtons.values.forEach { it.isEnabled = false }
        scope.launch {
            try {
                showMode(NetController.switchTo(this@MainActivity, mode))
                message.text = getString(R.string.switched_to, mode.label)
            } catch (e: Throwable) {
                message.text = when (e) {
                    is TimeoutCancellationException -> getString(R.string.error_timeout)
                    else -> e.message ?: e.toString()
                }
            } finally {
                modeButtons.values.forEach {
                    it.isEnabled = NetController.shizukuState(this@MainActivity) == ShizukuState.READY
                }
            }
        }
    }

    private companion object {
        const val REQUEST_CODE = 42
    }
}
