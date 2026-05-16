package com.wardrive.analyzer.android

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wardrive.analyzer.android.data.model.EvidenceEntity
import com.wardrive.analyzer.android.data.model.ReportEntity
import com.wardrive.analyzer.android.data.model.RunEntity
import com.wardrive.analyzer.android.data.repo.WardriveRepository
import com.wardrive.analyzer.android.sync.DropboxSyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class WardriveViewModel(
    private val app: WardriveApplication
) : ViewModel() {
    private val repo: WardriveRepository = app.repository
    private val prefs = app.getSharedPreferences("dropbox_sync", 0)
    private val dropboxService = DropboxSyncService()

    val evidence: Flow<List<EvidenceEntity>> = repo.evidence
    val runs: Flow<List<RunEntity>> = repo.runs
    val reports: Flow<List<ReportEntity>> = repo.reports
    val evidenceCount = repo.evidenceCount
    val openCount = repo.openCount
    private val _dropboxStatus = MutableStateFlow("Dropbox not synced yet.")
    val dropboxStatus: StateFlow<String> = _dropboxStatus.asStateFlow()

    fun getDropboxToken(): String = prefs.getString("token", "") ?: ""
    fun getDropboxFolder(): String = prefs.getString("folder", "/WardriveAnalyzerSync") ?: "/WardriveAnalyzerSync"

    fun saveDropboxConfig(token: String, folder: String) {
        prefs.edit()
            .putString("token", token.trim())
            .putString("folder", folder.trim().ifEmpty { "/WardriveAnalyzerSync" })
            .apply()
        _dropboxStatus.value = "Dropbox config saved."
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val resolver = app.contentResolver
            val fileName = uri.lastPathSegment ?: "import.csv"
            val lower = fileName.lowercase(Locale.US)
            resolver.openInputStream(uri)?.use { stream ->
                if (lower.endsWith(".pcap") || lower.endsWith(".cap")) {
                    repo.importPcap(fileName, stream)
                } else {
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        repo.importLog(fileName, reader)
                    }
                }
            }
        }
    }

    fun syncFromDropbox() {
        viewModelScope.launch {
            try {
                val token = getDropboxToken()
                val folder = getDropboxFolder()
                val outDir = dropboxService.syncFromDropbox(token, folder, app.filesDir)
                _dropboxStatus.value = "Synced from Dropbox to ${outDir.absolutePath}"
            } catch (e: Exception) {
                _dropboxStatus.value = "Dropbox sync failed: ${e.message}"
            }
        }
    }

    companion object {
        fun factory(app: WardriveApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return WardriveViewModel(app) as T
                }
            }
    }
}
