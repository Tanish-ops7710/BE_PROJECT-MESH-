package com.demo.upimesh.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.demo.upimesh.ui.MainViewModel
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothTransferScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val transferService = viewModel.bluetoothTransferService
    val devices by transferService.discoveredDevices.collectAsState(initial = emptyList())
    val status by transferService.status.collectAsState(initial = "Ready")
    val selectedDevice by transferService.selectedDevice.collectAsState(initial = null)
    val isConnected by transferService.isConnected.collectAsState(initial = false)
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Payment form state — inline on this screen to keep socket alive
    var receiverVpa by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var mpin by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Derive connecting state from status string (set by connectToDevice fix)
    val isConnecting = status.startsWith("Connecting to")

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            transferService.retryDiscoveryAfterPermissionGrant()
        } else {
            transferService.updateStatus("Bluetooth permissions denied. Cannot scan for devices.")
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        // Reset stale connection state from previous session (singleton persists across navigations)
        transferService.resetForNewSession()
        // Prevent background mesh manager from holding the RFCOMM port while on the dedicated transfer screen
        viewModel.stopBluetoothMesh()
        // Silently refresh the server public key in the background (needed after a server restart).
        // This is a no-op if the phone has no internet; it won't block anything.
        viewModel.refreshServerKey()
        if (transferService.hasRequiredPermissions(context)) {
            transferService.startDiscovery()
        } else {
            showPermissionDialog = true
            permissionLauncher.launch(requiredBluetoothPermissions())
        }
    }

    // Removed DisposableEffect that restarted the background mesh.
    // Restarting the mesh here was killing the active RFCOMM socket needed for the actual transaction.
    val incomingRequestDevice by transferService.incomingRequestDevice.collectAsState(initial = null)

    // ── Incoming Connection Request Dialog on Phone B ──
    incomingRequestDevice?.let { requestingDevice ->
        val reqName = try {
            requestingDevice.name?.takeIf { it.isNotBlank() } ?: requestingDevice.address
        } catch (_: SecurityException) {
            requestingDevice.address
        }
        AlertDialog(
            onDismissRequest = { transferService.rejectIncomingConnection() },
            icon = {
                Icon(
                    Icons.Default.BluetoothConnected,
                    contentDescription = null,
                    tint = UpiPrimaryBlue,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Incoming Connection Request",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Phone \"$reqName\" wants to connect with your device via Bluetooth. Do you accept?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { transferService.acceptIncomingConnection() },
                    colors = ButtonDefaults.buttonColors(containerColor = UpiSuccessGreen)
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { transferService.rejectIncomingConnection() }
                ) {
                    Text("Decline")
                }
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Bluetooth access required") },
            text = {
                Text(
                    "Please grant Bluetooth and Location permissions so this screen can scan " +
                    "and connect to nearby devices. Without them, discovery cannot start."
                )
            },
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
                    transferService.updateStatus("Permission denied — Bluetooth scanning unavailable.")
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Discovery") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UpiPrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F4F8))
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {

            // ── Status / connection card ────────────────────────────────────
            AnimatedContent(
                targetState = isConnected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "connection_card"
            ) { connected ->
                if (connected && selectedDevice != null) {
                    // ✅ Connected: show inline payment form — no navigation needed
                    val deviceName = try {
                        selectedDevice!!.name?.takeIf { it.isNotBlank() } ?: selectedDevice!!.address
                    } catch (_: SecurityException) { selectedDevice!!.address }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Connection header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = UpiSuccessGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Connected to $deviceName",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                    // Live step-by-step status
                                    Text(
                                        status,
                                        fontSize = 12.sp,
                                        color = Color(0xFF388E3C),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFA5D6A7))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Receiver VPA
                            OutlinedTextField(
                                value = receiverVpa,
                                onValueChange = { receiverVpa = it },
                                label = { Text("Receiver UPI ID (VPA)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isSending,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Amount
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text("Amount (₹)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isSending,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // MPIN
                            OutlinedTextField(
                                value = mpin,
                                onValueChange = { mpin = it },
                                label = { Text("MPIN") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isSending,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Send button
                            Button(
                                onClick = {
                                    if (receiverVpa.isBlank()) {
                                        Toast.makeText(context, "Enter receiver UPI ID", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (amount.toBigDecimalOrNull() == null || amount.toBigDecimalOrNull()!!.signum() <= 0) {
                                        Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (mpin.isBlank()) {
                                        Toast.makeText(context, "Enter your MPIN", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSending = true
                                    // Pass mpin so buildPacket can encrypt to server's public key
                                    transferService.sendDemoTransaction(
                                        senderVpa = viewModel.currentAccount.value?.vpa ?: "",
                                        receiverVpa = receiverVpa,
                                        amount = amount,
                                        note = "BT Payment",
                                        pin = mpin
                                    )
                                    isSending = false
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue),
                                enabled = !isSending
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send Payment", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Scanning / connecting status card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isConnecting) Icons.Default.BluetoothConnected
                                    else Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = UpiPrimaryBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isConnecting) "Connecting…" else "Nearby Devices",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                if (isConnecting) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = UpiPrimaryBlue
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status,
                                color = when {
                                    status.startsWith("Failed") -> MaterialTheme.colorScheme.error
                                    isConnecting -> UpiPrimaryBlue
                                    else -> Color(0xFF546E7A)
                                },
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Only show device list + rescan when not connected
            if (!isConnected) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (devices.isEmpty()) "Scanning for nearby devices…"
                           else "${devices.size} device(s) found",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF546E7A),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = UpiPrimaryBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Scanning for Bluetooth devices…\nMake sure the other phone's Bluetooth is visible.",
                                textAlign = TextAlign.Center,
                                color = Color(0xFF78909C),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(devices, key = { it.address }) { device ->
                            val isThisSelected = selectedDevice?.address == device.address
                            val itemAlpha = if (isConnecting && !isThisSelected) 0.45f else 1f
                            val deviceName = try {
                                device.name?.takeIf { it.isNotBlank() } ?: "Unknown Device"
                            } catch (_: SecurityException) { "Unknown Device" }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .alpha(itemAlpha)
                                    .clickable(enabled = !isConnecting && !isConnected) {
                                        transferService.connectToDevice(device)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isThisSelected) Color(0xFFE3F2FD) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isThisSelected) 4.dp else 1.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isThisSelected) UpiPrimaryBlue else Color(0xFFECEFF1),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                tint = if (isThisSelected) Color.White else Color(0xFF546E7A),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            deviceName,
                                            fontWeight = if (isThisSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            device.address,
                                            color = Color(0xFF90A4AE),
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (isThisSelected && isConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = UpiPrimaryBlue
                                        )
                                    } else if (isThisSelected && isConnected) {
                                        Icon(
                                            Icons.Default.BluetoothConnected,
                                            contentDescription = "Connected",
                                            tint = UpiSuccessGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { transferService.startDiscovery() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UpiPrimaryBlue)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-scan for Devices")
                }
            } else {
                // Connected: the payment card above is shown; add small spacer at bottom
                Spacer(modifier = Modifier.weight(1f))
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
