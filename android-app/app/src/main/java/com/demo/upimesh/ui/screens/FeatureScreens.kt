package com.demo.upimesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.demo.upimesh.data.api.NetworkModule
import com.demo.upimesh.ui.MainViewModel
import com.demo.upimesh.ui.components.BleTopologyCanvas
import com.demo.upimesh.ui.navigation.Screen
import com.demo.upimesh.ui.theme.UpiDarkBlue
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen
import com.demo.upimesh.model.TransactionStatus
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScreen(navController: NavController, viewModel: MainViewModel) {
    val smsList by viewModel.smsList.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Banking SMS Inbox (Simulated)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (smsList.isEmpty()) {
                item {
                    Text("No SMS messages received yet.", color = Color.Gray)
                }
            } else {
                items(smsList) { sms ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                  Text(sms.sender, fontWeight = FontWeight.Bold, color = UpiPrimaryBlue)
                                  Text(sms.type, fontSize = 11.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(sms.body, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController, viewModel: MainViewModel) {
    val notifications by viewModel.notificationsList.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Notifications") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notifications) { notif ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(notif.title, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(notif.message, fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoModeScreen(navController: NavController, viewModel: MainViewModel) {
    val progress by viewModel.demoProgress.collectAsState()
    val statusMsg by viewModel.demoStatusMessage.collectAsState()
    val activeHopIndex by viewModel.activeHopIndex.collectAsState()
    var isRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automated 20s Demo Mode") },
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Automated End-to-End Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = UpiPrimaryBlue,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(statusMsg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = UpiPrimaryBlue)
                }
            }

            BleTopologyCanvas(activeHopIndex = activeHopIndex)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    isRunning = true
                    viewModel.runAutomatedDemo {
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpiSuccessGreen)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunning) "Running Demo..." else "▶ Run Complete Demo (20-30s)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationModeScreen(navController: NavController, viewModel: MainViewModel) {
    val activeHopIndex by viewModel.activeHopIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presentation Supervisor Panel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BleTopologyCanvas(activeHopIndex = activeHopIndex)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Live Server & Log Stream", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("[SERVER] Ingestion endpoint /api/bridge/ingest active", color = Color(0xFF38BDF8), fontSize = 11.sp)
                        Text("[IDEMPOTENCY] Cache Window: 86400s (Duplicate Replay Guard Enabled)", color = Color(0xFF4ADE80), fontSize = 11.sp)
                        Text("[CRYPTO] Hybrid AES-256-GCM + RSA-OAEP 2048 verified", color = Color(0xFFFACC15), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityClassroomScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Classroom") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SecurityConceptCard("1. Hybrid Encryption (RSA + AES)", "Payload is encrypted using a random 256-bit AES key. The AES key is then encrypted with the Bank Server's RSA-2048 Public Key.")
            }
            item {
                SecurityConceptCard("2. SHA-256 Integrity & Packet Hash", "Every packet contains a SHA-256 digest of sender, receiver, amount, and timestamp to prevent tampering during gossip hops.")
            }
            item {
                SecurityConceptCard("3. Replay Protection & Idempotency", "The central bank maintains a 24-hour hash window. Duplicate packets arriving from multiple bridge nodes are discarded instantly.")
            }
            item {
                SecurityConceptCard("4. TTL & Hop Limits", "Packets have a Maximum TTL (Time-To-Live = 5). Each gossip transfer decrements TTL, preventing network flooding.")
            }
        }
    }
}

@Composable
fun SecurityConceptCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = UpiPrimaryBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics & Mesh Health") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCard("Total Transactions", "12", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                StatCard("Avg Hop Count", "2.4 Hops", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCard("Settlement Rate", "100%", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                StatCard("Replays Blocked", "4 Packets", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = UpiPrimaryBlue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, viewModel: MainViewModel) {
    val currentAccount by viewModel.currentAccount.collectAsState()
    val balance by viewModel.currentAccountBalance.collectAsState()

    val name = currentAccount?.holderName ?: "Offline User"
    val vpa = currentAccount?.vpa ?: "offline@demo"
    val phone = currentAccount?.phoneNumber ?: "N/A"
    
    val pending = currentAccount?.pendingBalance ?: BigDecimal.ZERO
    val dailyLimit = currentAccount?.dailyLimit ?: BigDecimal("10000.00")
    val monthlyLimit = currentAccount?.monthlyLimit ?: BigDecimal("100000.00")
    val isFrozen = currentAccount?.isFrozen ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile & Wallet") },
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(vpa, color = UpiPrimaryBlue, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Phone: $phone", color = Color.Gray, fontSize = 14.sp)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Wallet Account Limits", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    HorizontalDivider()
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Current Balance:", color = Color.Gray)
                        Text("₹${balance.toPlainString()}", fontWeight = FontWeight.Bold, color = UpiSuccessGreen)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pending Sync Balance:", color = Color.Gray)
                        Text("₹${pending.toPlainString()}", fontWeight = FontWeight.Bold, color = UpiPrimaryBlue)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Daily Spending Limit:", color = Color.Gray)
                        Text("₹${dailyLimit.toPlainString()}", fontWeight = FontWeight.Bold)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monthly Spending Limit:", color = Color.Gray)
                        Text("₹${monthlyLimit.toPlainString()}", fontWeight = FontWeight.Bold)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Wallet Account Status:", color = Color.Gray)
                        Text(
                            text = if (isFrozen) "FROZEN ❄️" else "ACTIVE ✅",
                            fontWeight = FontWeight.Bold,
                            color = if (isFrozen) Color.Red else UpiSuccessGreen
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { navController.navigate(Screen.Profile.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Profile & Limits")
            }

            Button(
                onClick = { navController.navigate(Screen.DeveloperMode.route) },
                colors = ButtonDefaults.buttonColors(containerColor = UpiDarkBlue),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Developer Console")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperModeScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentAccount by viewModel.currentAccount.collectAsState()
    val vpa = currentAccount?.vpa ?: ""
    
    var showChangeMpin by remember { mutableStateOf(false) }
    var oldMpin by remember { mutableStateOf("") }
    var newMpin by remember { mutableStateOf("") }

    var serverIpInput by remember { mutableStateOf<String>(NetworkModule.baseUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Mode & Console") },
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
                .background(Color(0xFF0F172A))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backend Server Base URL", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = serverIpInput,
                        onValueChange = { serverIpInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            viewModel.updateServerUrl(serverIpInput)
                            Toast.makeText(context, "Server URL updated", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                    ) {
                        Text("Apply Server URL")
                    }
                    Text("Current Active: ${NetworkModule.baseUrl}", color = Color.LightGray, fontSize = 12.sp)
                }
            }

            if (showChangeMpin) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Change Security MPIN", fontWeight = FontWeight.Bold, color = Color.White)
                        
                        OutlinedTextField(
                            value = oldMpin,
                            onValueChange = { oldMpin = it },
                            label = { Text("Current MPIN", color = Color.LightGray) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newMpin,
                            onValueChange = { newMpin = it },
                            label = { Text("New 4-Digit MPIN", color = Color.LightGray) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showChangeMpin = false }) {
                                Text("Cancel", color = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (oldMpin.isEmpty() || newMpin.isEmpty()) {
                                        Toast.makeText(context, "Fill in all fields", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.changeMpin(oldMpin, newMpin) { success, msg ->
                                        if (success) {
                                            Toast.makeText(context, "MPIN changed", Toast.LENGTH_SHORT).show()
                                            showChangeMpin = false
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = { showChangeMpin = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                ) {
                    Text("Change Security MPIN")
                }
            }

            Button(
                onClick = {
                    if (vpa.isEmpty()) {
                        Toast.makeText(context, "No user logged in", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.deleteAccount(vpa) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account Permanently")
            }

            Button(
                onClick = {
                    viewModel.logoutUser {
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout / Switch Account")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(navController: NavController, viewModel: MainViewModel) {
    val transactions by viewModel.localTransactions.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        viewModel.refreshTransactions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.uploadPendingPackets() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync with Server")
                    }
                }
            )
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FA))
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FA))
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(transactions) { tx ->
                    val statusColor = when (tx.status) {
                        TransactionStatus.SETTLED, TransactionStatus.COMPLETED -> UpiSuccessGreen
                        TransactionStatus.QUEUED_OFFLINE -> Color(0xFFE65100)
                        TransactionStatus.RELAYING -> Color(0xFF0288D1)
                        TransactionStatus.PENDING_UPLOAD, TransactionStatus.PENDING -> Color(0xFFF57C00)
                        TransactionStatus.FAILED -> Color.Red
                        else -> Color.Gray
                    }
                    val statusBg = statusColor.copy(alpha = 0.12f)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (tx.senderVpa.isNotBlank() && tx.receiverVpa.isNotBlank()) {
                                        "${tx.senderVpa} → ${tx.receiverVpa}"
                                    } else if (tx.receiverVpa.isNotBlank()) {
                                        "To: ${tx.receiverVpa}"
                                    } else {
                                        "Packet: ${tx.packetId.take(8)}"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusBg
                                ) {
                                    Text(
                                        text = tx.status,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("₹${tx.amount}", color = UpiPrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                if (tx.note.isNotBlank()) {
                                    Text(tx.note, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            if (tx.packetId.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("ID: ${tx.packetId.take(16)}...", fontSize = 10.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Rahul (rahul@demo)", fontWeight = FontWeight.Bold)
            Text("Priya (priya@demo)", fontWeight = FontWeight.Bold)
            Text("Aman (aman@demo)", fontWeight = FontWeight.Bold)
        }
    }
}
