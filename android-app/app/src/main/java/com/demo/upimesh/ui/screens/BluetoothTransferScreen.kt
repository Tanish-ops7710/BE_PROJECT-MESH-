package com.demo.upimesh.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.demo.upimesh.ui.MainViewModel
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen
import com.demo.upimesh.util.BluetoothTransferService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothTransferScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val transferService = remember(context) { BluetoothTransferService(context) }
    val devices by transferService.discoveredDevices.collectAsState(initial = emptyList())
    val status by transferService.status.collectAsState(initial = "Ready")
    val selectedDevice by transferService.selectedDevice.collectAsState(initial = null)
    val isConnected by transferService.isConnected.collectAsState(initial = false)
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            transferService.retryDiscoveryAfterPermissionGrant()
        } else {
            transferService.updateStatus("Bluetooth permissions are required to scan nearby devices.")
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (transferService.hasRequiredPermissions(context)) {
            transferService.startDiscovery()
        } else {
            showPermissionDialog = true
            permissionLauncher.launch(requiredBluetoothPermissions())
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Bluetooth access required") },
            text = { Text("Please grant Bluetooth permissions so the transfer screen can scan and connect to nearby devices.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    permissionLauncher.launch(requiredBluetoothPermissions())
                }) {
                    Text("Grant access")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    transferService.updateStatus("Permission denied. Bluetooth scanning is unavailable.")
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Transfer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(padding)
                .padding(20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = UpiPrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nearby Android devices", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(status, color = UpiPrimaryBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (selectedDevice != null) {
                        Text("Selected: ${selectedDevice!!.name ?: selectedDevice!!.address}", color = UpiSuccessGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Scanning for nearby Android phones...")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    transferService.connectToDevice(device)
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = UpiPrimaryBlue)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(device.name ?: "Nearby Device", fontWeight = FontWeight.Medium)
                                    Text(device.address, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    transferService.sendPendingTransaction()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && selectedDevice != null,
                colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
            ) {
                Text(if (isConnected && selectedDevice != null) "Send Pending Transaction" else "Connect to a device first")
            }
        }
    }
}

private fun requiredBluetoothPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
