package com.novelarrow.reader.ui.browser.delegate

import com.novelarrow.reader.browser.engine.WebScriptInjector
import com.novelarrow.reader.data.model.BrowserNavbarShortcut
import com.novelarrow.reader.data.model.CustomToolbarButton
import com.novelarrow.reader.data.model.NextChapterSelectorRule
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.data.userscript.UserscriptEntity
import com.novelarrow.reader.data.userscript.UserscriptMetadataParser
import com.novelarrow.reader.data.userscript.UserscriptRepository
import com.novelarrow.reader.ui.browser.BrowserUiState
import com.novelarrow.reader.ui.browser.ClickableElement
import com.novelarrow.reader.ui.browser.LongPressedClickableInfo
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class BrowserUserscriptDelegate(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val userscriptRepository: UserscriptRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchEvaluateJs: (String, ((String?) -> Unit)?) -> Unit
) {
    companion object {
        private const val TAG = "BrowserUserscriptDelegate"
    }

    fun openExtensionsSheet() {
        val curUrl = uiState.value.currentUrl
        val active = uiState.value.allUserscripts.filter { script ->
            script.isEnabled && script.matchPatterns.any { pattern ->
                WebScriptInjector.matchesPattern(curUrl, pattern)
            }
        }
        uiState.update { it.copy(showExtensionsSheet = true, activeUserscripts = active) }
    }

    fun dismissExtensionsSheet() {
        uiState.update { it.copy(showExtensionsSheet = false) }
    }

    fun openUserscriptManager() {
        uiState.update { it.copy(showUserscriptManager = true, showExtensionsSheet = false) }
    }

    fun dismissUserscriptManager() {
        uiState.update { it.copy(showUserscriptManager = false) }
    }

    fun openCodeMirrorEditor(script: UserscriptEntity? = null) {
        scope.launch {
            if (script != null) {
                val code = userscriptRepository.getScriptCode(script)
                uiState.update {
                    it.copy(
                        showCodeMirrorEditor = true,
                        editingUserscript = script,
                        editorPrefilledCode = code,
                        editorPrefilledTitle = script.name,
                        showExtensionsSheet = false,
                        showUserscriptManager = false
                    )
                }
            } else {
                val currentUrl = uiState.value.currentUrl
                val template = UserscriptMetadataParser.generateDefaultTemplate(currentUrl, "New Userscript")
                uiState.update {
                    it.copy(
                        showCodeMirrorEditor = true,
                        editingUserscript = null,
                        editorPrefilledCode = template,
                        editorPrefilledTitle = "New Userscript",
                        showExtensionsSheet = false,
                        showUserscriptManager = false
                    )
                }
            }
        }
    }

    fun dismissCodeMirrorEditor() {
        uiState.update { it.copy(showCodeMirrorEditor = false, editingUserscript = null) }
    }

    fun saveUserscriptFromEditor(code: String) {
        scope.launch {
            try {
                val existing = uiState.value.editingUserscript
                val saved = userscriptRepository.saveScript(code, preferredName = existing?.name, existingId = existing?.id)
                uiState.update {
                    it.copy(
                        showCodeMirrorEditor = false,
                        editingUserscript = null,
                        toastMessage = "Saved ${saved.name} to Downloads/BookReader_Scripts"
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(toastMessage = "Failed to save script: ${e.message}") }
            }
        }
    }

    fun toggleUserscript(id: String, isEnabled: Boolean) {
        scope.launch {
            userscriptRepository.toggleScriptEnabled(id, isEnabled)
        }
    }

    fun deleteUserscript(id: String) {
        scope.launch {
            val success = userscriptRepository.deleteScript(id)
            if (success) {
                uiState.update { it.copy(toastMessage = "Deleted script and physical file") }
            }
        }
    }

    fun renameUserscript(id: String, newName: String) {
        scope.launch {
            val success = userscriptRepository.renameScript(id, newName)
            if (success) {
                uiState.update { it.copy(toastMessage = "Renamed script to $newName") }
            } else {
                uiState.update { it.copy(toastMessage = "Failed to rename script") }
            }
        }
    }

    fun syncUserscriptsFromDisk() {
        scope.launch {
            val list = userscriptRepository.syncFromDisk()
            uiState.update { it.copy(toastMessage = "Synced ${list.size} scripts from Downloads") }
        }
    }

    fun openDocumentationDialog() {
        uiState.update { it.copy(showDocumentationDialog = true, showExtensionsSheet = false) }
    }

    fun dismissDocumentationDialog() {
        uiState.update { it.copy(showDocumentationDialog = false) }
    }

    fun onLongPressClickable(selector: String, text: String, hostname: String) {
        uiState.update {
            it.copy(
                longPressedClickable = LongPressedClickableInfo(selector, text, hostname),
                showAddNextSelectorDialog = true
            )
        }
    }

    fun dismissAddNextSelectorDialog() {
        uiState.update {
            it.copy(
                longPressedClickable = null,
                showAddNextSelectorDialog = false
            )
        }
    }

    fun confirmAddNextSelector(ruleName: String, selector: String, hostname: String) {
        scope.launch {
            val rule = NextChapterSelectorRule(
                name = ruleName.ifBlank { "Next Chapter ($hostname)" },
                selector = selector,
                domain = hostname,
                enabled = true
            )
            settingsRepository.addNextChapterSelectorRule(rule)
            uiState.update {
                it.copy(
                    longPressedClickable = null,
                    showAddNextSelectorDialog = false,
                    toastMessage = "Added Next Chapter selector for $hostname"
                )
            }
        }
    }

    fun addNextChapterSelectorRule(rule: NextChapterSelectorRule) {
        scope.launch {
            settingsRepository.addNextChapterSelectorRule(rule)
        }
    }

    fun updateNextChapterSelectorRule(rule: NextChapterSelectorRule) {
        scope.launch {
            settingsRepository.updateNextChapterSelectorRule(rule)
        }
    }

    fun toggleNextChapterSelectorRule(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.toggleNextChapterSelectorRule(id, enabled)
        }
    }

    fun deleteNextChapterSelectorRule(id: String) {
        scope.launch {
            settingsRepository.removeNextChapterSelectorRule(id)
        }
    }

    fun resetNextChapterSelectorRules() {
        scope.launch {
            settingsRepository.resetNextChapterSelectorRules()
        }
    }

    fun inspectPageClickables() {
        val script = WebScriptInjector.buildClickableInspectorScript()
        dispatchEvaluateJs(script) { result ->
            val list = mutableListOf<ClickableElement>()
            if (!result.isNullOrBlank() && result != "null") {
                try {
                    val rawJson = if (result.startsWith("\"") && result.endsWith("\"")) {
                        JSONTokener(result).nextValue().toString()
                    } else result
                    val array = JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val selector = obj.optString("selector", "")
                        val text = obj.optString("text", "")
                        val tag = obj.optString("tag", "")
                        val href = obj.optString("href", "")
                        if (selector.isNotBlank()) {
                            list.add(
                                ClickableElement(
                                    selector = selector,
                                    text = text,
                                    tagName = tag,
                                    href = href
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed parsing scanned clickables: ${e.message}", e)
                }
            }
            uiState.update {
                it.copy(
                    scannedClickables = list,
                    showClickablesInspector = true
                )
            }
        }
    }

    fun dismissClickablesInspector() {
        uiState.update { it.copy(showClickablesInspector = false) }
    }

    fun openShortcutsManager() {
        uiState.update { it.copy(showShortcutsManager = true, showClickablesInspector = false) }
    }

    fun dismissShortcutsManager() {
        uiState.update { it.copy(showShortcutsManager = false) }
    }

    fun addNavbarShortcut(shortcut: BrowserNavbarShortcut) {
        scope.launch {
            settingsRepository.addNavbarShortcut(shortcut)
            uiState.update { it.copy(toastMessage = "Added shortcut: ${shortcut.name}") }
        }
    }

    fun toggleNavbarShortcut(id: String, enabled: Boolean) {
        scope.launch {
            settingsRepository.toggleNavbarShortcut(id, enabled)
        }
    }

    fun deleteNavbarShortcut(id: String) {
        scope.launch {
            settingsRepository.removeNavbarShortcut(id)
        }
    }

    fun clickElementBySelector(selector: String) {
        val script = WebScriptInjector.buildClickElementScript(selector)
        dispatchEvaluateJs(script, null)
    }

    fun registerCustomToolbarButton(payload: String) {
        try {
            val obj = JSONObject(payload)
            val id = obj.optString("id", "")
            if (id.isBlank()) return
            val title = obj.optString("title", "Button")
            val icon = obj.optString("icon", "Extension")
            val badge = obj.optString("badge", "")
            val btn = CustomToolbarButton(id = id, title = title, icon = icon, badge = badge)
            uiState.update { state ->
                val filtered = state.customToolbarButtons.filterNot { it.id == id }
                state.copy(customToolbarButtons = filtered + btn)
            }
            AppLogger.d(TAG, "Registered custom toolbar button: $id ($title)")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse custom toolbar button payload: ${e.message}", e)
        }
    }

    fun onCustomToolbarButtonClicked(buttonId: String) {
        AppLogger.i(TAG, "Custom toolbar button clicked: $buttonId")
        dispatchEvaluateJs("window.__triggerCustomToolbarButton('$buttonId');", null)
    }

    fun updateToolbarButtonOrder(order: List<String>) {
        scope.launch {
            settingsRepository.updateToolbarButtonOrder(order)
        }
    }
}
