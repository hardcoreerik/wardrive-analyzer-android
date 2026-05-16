package com.wardrive.analyzer.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    onDropboxTokenChange: (String) -> Unit,
    onDropboxFolderChange: (String) -> Unit,
    onSaveDropboxConfig: () -> Unit,
    onSyncFromDropbox: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mission Control", style = MaterialTheme.typography.headlineMedium)
        MetricCard("Evidence Rows", evidenceCount.toString())
        MetricCard("Open Networks", openCount.toString())
        MetricCard("Runs", runCount.toString())
        MetricCard("Reports", reportCount.toString())
        MetricCard("PCAP Packets", totalPcapPackets.toString())
        MetricCard("High Risk Runs", highRiskRuns.toString())
        MetricCard("Last Import", lastImport)
        Text("Dropbox Sync", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = dropboxToken,
            onValueChange = onDropboxTokenChange,
            label = { Text("Dropbox Access Token") },
            singleLine = true
        )
        OutlinedTextField(
            value = dropboxFolder,
            onValueChange = onDropboxFolderChange,
            label = { Text("Dropbox Folder Path") },
            singleLine = true
        )
        Button(onClick = onSaveDropboxConfig) { Text("Save Dropbox Config") }
        Button(onClick = onSyncFromDropbox) { Text("Sync from Dropbox") }
        Text(dropboxStatus, style = MaterialTheme.typography.bodySmall)
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
