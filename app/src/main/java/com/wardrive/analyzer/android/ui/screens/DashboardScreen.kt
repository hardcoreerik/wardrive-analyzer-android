package com.wardrive.analyzer.android.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrive.analyzer.android.data.model.ProjectProfileEntity
import com.wardrive.analyzer.android.data.model.SyncRecordEntity

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    evidenceCount: Int,
    openCount: Int,
    runCount: Int,
    reportCount: Int,
    totalPcapPackets: Int,
    highRiskRuns: Int,
    lastImport: String,
    dropboxToken: String,
    dropboxFolder: String,
    dropboxStatus: String,
    projectProfiles: List<ProjectProfileEntity>,
    activeProjectSlug: String,
    syncRecords: List<SyncRecordEntity>,
    isSyncingDropbox: Boolean,
    onDropboxTokenChange: (String) -> Unit,
    onDropboxFolderChange: (String) -> Unit,
    onSaveDropboxConfig: () -> Unit,
    onSyncFromDropbox: () -> Unit,
    onRefreshProjects: () -> Unit,
    onSetActiveProject: (String) -> Unit
) {
    val scroll = rememberScrollState()
    val lastPull = syncRecords.firstOrNull { it.direction == "pull" }?.timestamp
    val lastPush = syncRecords.firstOrNull { it.direction == "push" }?.timestamp
    val pendingUploads = syncRecords.count { it.direction == "push" && it.status == "error" }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MISSION CONTROL", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        MetricCard("Evidence Rows", evidenceCount.toString())
        MetricCard("Open Networks", openCount.toString())
        MetricCard("Runs", runCount.toString())
        MetricCard("Reports", reportCount.toString())
        MetricCard("PCAP Packets", totalPcapPackets.toString())
        MetricCard("High Risk Runs", highRiskRuns.toString())
        MetricCard("Last Import", lastImport)
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Project Sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Text("Active project: ${if (activeProjectSlug.isBlank()) "None selected" else activeProjectSlug}")
                Text("Last pull: ${lastPull?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "never"}")
                Text("Last push: ${lastPush?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "never"}")
                Text("Pending uploads: $pendingUploads")
                Button(onClick = onRefreshProjects, modifier = Modifier.fillMaxWidth()) { Text("Refresh Dropbox Projects") }
                if (projectProfiles.isEmpty()) {
                    Text("No projects found in Dropbox root.")
                } else {
                    Column(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        projectProfiles.forEach { profile ->
                            Button(onClick = { onSetActiveProject(profile.slug) }) {
                                val marker = if (profile.slug == activeProjectSlug) "[ACTIVE] " else ""
                                Text("$marker${profile.displayName}")
                            }
                        }
                    }
                }
                if (syncRecords.isNotEmpty()) {
                    Text("Recent sync events:")
                    syncRecords.take(4).forEach {
                        val conflict = if (it.conflictFlag) " conflict" else ""
                        Text("${it.direction} ${it.projectSlug}: ${it.status}$conflict - ${it.path}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dropbox Sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Text("Connect once, then sync selected project between Android, Dropbox, and PC.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = dropboxToken,
                    onValueChange = onDropboxTokenChange,
                    label = { Text("Dropbox Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dropboxFolder,
                    onValueChange = onDropboxFolderChange,
                    label = { Text("Dropbox Projects Root") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onSaveDropboxConfig, modifier = Modifier.fillMaxWidth()) { Text("Save Dropbox Config") }
                Button(
                    onClick = onSyncFromDropbox,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncingDropbox
                ) {
                    Text(if (isSyncingDropbox) "Syncing..." else "Sync from Dropbox")
                }
                if (isSyncingDropbox) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(dropboxStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

