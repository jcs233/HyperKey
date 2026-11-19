package io.github.hyperkey.ui

import android.content.Context
import android.provider.Settings
import io.github.hyperkey.core.*
import org.json.JSONObject
import java.util.UUID

internal enum class ActionKind { APP, FEATURE, PAGE, SHELL, ROOT }
internal val featureActions = listOf(Settings.ACTION_SETTINGS, Settings.ACTION_WIFI_SETTINGS,
    Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS)
internal data class LocalAction(val kind: ActionKind, val value: String) {
    fun valid() = when (kind) {
        ActionKind.APP -> AppProfiles.validPackage(value) || value == "android"
        ActionKind.FEATURE -> value in featureActions
        ActionKind.PAGE -> android.content.ComponentName.unflattenFromString(value)?.let { AppProfiles.validPackage(it.packageName) && it.className.isNotBlank() } == true
        ActionKind.SHELL, ActionKind.ROOT -> value.isNotBlank() && value.length <= 2048 && '\u0000' !in value
    }
}

/** Secure Settings contains the trigger only. Bind private payloads to one immutable rule reference. */
internal object LocalActions {
    private fun prefs(c: Context) = c.getSharedPreferences("shortcut_actions", Context.MODE_PRIVATE)
    fun get(c: Context, rule: KeyMappings.Rule): LocalAction? = runCatching {
        if (!KeyMappings.localId(rule.action())) return null
        val data = JSONObject(prefs(c).getString(rule.action(), null) ?: return null)
        if (data.getString("binding") != rule.id()) return null
        LocalAction(ActionKind.valueOf(data.getString("kind")), data.getString("value")).takeIf { it.valid() }
    }.getOrNull()

    @Synchronized fun save(c: Context, owner: String, key: Int, mods: Int, dst: Int, dm: Int, action: String, local: LocalAction?): KeyMappings {
        require(owner == KeyMappings.GLOBAL || ProfileStore.read(c).entries.containsKey(owner))
        val previous = MappingStore.read(c)
        val ref = if (local == null) action else "local_" + UUID.randomUUID().toString().replace("-", "")
        val rule = KeyMappings.Rule(owner, key, mods, dst, dm, ref)
        val next = previous.put(rule)
        if (local != null) {
            require(local.valid())
            val data = JSONObject().put("binding", rule.id()).put("kind", local.kind.name).put("value", local.value)
            check(prefs(c).edit().putString(ref, data.toString()).commit())
        }
        try { MappingStore.write(c, next) } catch (e: Exception) {
            if (local != null) prefs(c).edit().remove(ref).apply()
            throw e
        }
        forget(c, previous.rules.filter { it.id() == rule.id() })
        return next
    }
    @Synchronized fun remove(c: Context, predicate: (KeyMappings.Rule) -> Boolean): KeyMappings {
        val current = MappingStore.read(c)
        val removed = current.rules.filter(predicate)
        val next = KeyMappings(current.rules - removed.toSet())
        if (removed.isNotEmpty()) { MappingStore.write(c, next); forget(c, removed) }
        return next
    }
    private fun forget(c: Context, rules: List<KeyMappings.Rule>) {
        val edit = prefs(c).edit(); rules.filter { it.localAction() }.forEach { edit.remove(it.action()) }; edit.apply()
    }
}
