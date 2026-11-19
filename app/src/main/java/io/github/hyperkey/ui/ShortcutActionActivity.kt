package io.github.hyperkey.ui

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hyperkey.R
import io.github.hyperkey.core.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun resolve(c: Context, ref: String, binding: String): LocalAction? {
    val guard = c.getSystemService(KeyguardManager::class.java) ?: return null
    if (!KeyMappings.localId(ref) || guard.isKeyguardLocked || guard.isDeviceLocked || ConfigStore.read(c).blockAll) return null
    val rule = MappingStore.read(c).rules.singleOrNull { it.action() == ref && it.id() == binding } ?: return null
    if (rule.owner() != KeyMappings.GLOBAL && ProfileStore.read(c).entries[rule.owner()]?.enabled != true) return null
    return LocalActions.get(c, rule)
}

class ShortcutActionActivity : ThemedActivity() {
    class Execution(app: Application) : AndroidViewModel(app) {
        companion object { private val busy = AtomicBoolean(); private val worker = Executors.newSingleThreadExecutor() }
        @Volatile private var abandoned = false
        override fun onCleared() { abandoned = true }
        var started = false
        var result by mutableStateOf<CommandRunner.Result?>(null)
        internal fun run(action: LocalAction, ref: String, binding: String) {
            if (started) return
            started = true
            if (!busy.compareAndSet(false, true)) { result = CommandRunner.Result(CommandRunner.Status.CANCELED, -1); return }
            worker.execute {
                val outcome = try { CommandRunner.run(action.kind == ActionKind.ROOT, action.value) {
                    runCatching { !abandoned && resolve(getApplication(), ref, binding) == action }.getOrDefault(false)
                } } catch (_: RuntimeException) { CommandRunner.Result(CommandRunner.Status.FAILED, -1) }
                finally { busy.set(false) }
                android.os.Handler(android.os.Looper.getMainLooper()).post { result = outcome }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val model = ViewModelProvider(this)[Execution::class.java]
        val ref = intent.getStringExtra("ref").orEmpty(); val binding = intent.getStringExtra("binding").orEmpty()
        val action = runCatching { resolve(this, ref, binding) }.getOrNull()
        if (action == null || (savedInstanceState?.getBoolean("started") == true && !model.started)) { finish(); return }
        if (action.kind in listOf(ActionKind.APP,ActionKind.FEATURE,ActionKind.PAGE)) {
            runCatching {
                val target = when (action.kind) {
                    ActionKind.APP -> packageManager.getLaunchIntentForPackage(action.value)
                    ActionKind.PAGE -> {
                        val component = requireNotNull(android.content.ComponentName.unflattenFromString(action.value))
                        check(accessiblePage(this, component)); Intent().setComponent(component)
                    }
                    else -> Intent(action.value)
                }
                startActivity(requireNotNull(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { toast(R.string.action_unavailable) }
            finish(); return
        }
        if (action.kind == ActionKind.SHELL) model.run(action, ref, binding)
        setContent { HyperTheme {
            var running by remember { mutableStateOf(model.started) }
            val result = model.result
            LaunchedEffect(result) { if (result != null) {
                val message = when (result.status()) {
                    CommandRunner.Status.OK -> getString(R.string.command_ok)
                    CommandRunner.Status.ROOT_DENIED -> getString(R.string.root_denied)
                    CommandRunner.Status.TIMEOUT -> getString(R.string.command_timeout)
                    CommandRunner.Status.CANCELED -> getString(R.string.command_canceled)
                    else -> getString(R.string.command_failed, result.exit())
                }
                Toast.makeText(this@ShortcutActionActivity, message, Toast.LENGTH_LONG).show(); finish()
            } }
            EditorPage(stringResource(if (action.kind == ActionKind.ROOT) R.string.root_command else R.string.user_command), ::finish) {
                if (!running) {
                    Text(stringResource(R.string.root_explanation)); Text(action.value)
                    TextButton(text = stringResource(R.string.run_root), onClick = { running = true; model.run(action, ref, binding) })
                    TextButton(text = tr("cancel"), onClick = ::finish)
                } else Text(stringResource(R.string.command_running))
            }
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("started", ViewModelProvider(this)[Execution::class.java].started); super.onSaveInstanceState(outState)
    }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()
}
