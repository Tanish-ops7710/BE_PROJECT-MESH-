package com.demo.upimesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.demo.upimesh.ui.MainViewModel
import com.demo.upimesh.ui.navigation.Screen
import com.demo.upimesh.ui.theme.UpiDarkBlue
import com.demo.upimesh.ui.theme.UpiLightBlue
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val currentAccount by viewModel.currentAccount.collectAsState()
    val balance by viewModel.currentAccountBalance.collectAsState()
    val transactions by viewModel.localTransactions.collectAsState(initial = emptyList())
    val packets by viewModel.offlinePackets.collectAsState(initial = emptyList())

    val name = currentAccount?.holderName ?: "Offline User"
    val vpa = currentAccount?.vpa ?: "offline@demo"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            color = UpiLightBlue
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(name.take(1).uppercase(), color = UpiPrimaryBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(vpa, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Demo Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3CD)
                ) {
                    Text(
                        text = "⚠️ DEMO ENVIRONMENT – NO REAL MONEY IS TRANSFERRED.",
                        modifier = Modifier.padding(10.dp),
                        color = Color(0xFF856404),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = UpiPrimaryBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Demo Balance", color = Color.White.copy(alpha = 0.8f))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("BLE Mesh Active", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₹${balance.toPlainString()}",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("UPI ID: $vpa", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }

            // Quick Actions Grid
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickActionButton(Icons.AutoMirrored.Filled.Send, "Send Money", UpiPrimaryBlue) {
                        navController.navigate(Screen.SendMoney.route)
                    }
                    QuickActionButton(Icons.AutoMirrored.Filled.CallReceived, "Receive", UpiDarkBlue) {
                        navController.navigate(Screen.ReceiveMoney.route)
                    }
                    QuickActionButton(Icons.Default.QrCodeScanner, "Scan QR", Color(0xFF0F9D58)) {
                        navController.navigate(Screen.QrScanner.route)
                    }
                    QuickActionButton(Icons.Default.QrCode, "My QR", Color(0xFFAB47BC)) {
                        navController.navigate(Screen.QrGenerator.route)
                    }
                }
            }

            // P2P Offline Mesh Controls Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BLE Gossip Mesh Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = { navController.navigate(Screen.MeshStatus.route) },
                                colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mesh Topology")
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { navController.navigate(Screen.OfflineQueue.route) },
                                colors = ButtonDefaults.buttonColors(containerColor = UpiDarkBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Queue (${packets.size})")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { navController.navigate(Screen.BluetoothTransfer.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = UpiSuccessGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bluetooth Transfer")
                        }
                    }
                }
            }

            // Presentation & Demo Suite Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Slideshow, contentDescription = null, tint = UpiPrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Presentation & Security Suite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.DemoMode.route) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Automated Demo")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.PresentationMode.route) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Presentation")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { navController.navigate(Screen.SecurityClassroom.route) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Classroom")
                            }
                        }
                    }
                }
            }

            // Recent Transactions Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.TransactionHistory.route) }) {
                        Text("View All", color = UpiPrimaryBlue)
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Text("No transactions yet. Send money via BLE mesh to test!", color = Color.Gray)
                }
            } else {
                items(transactions.take(5)) { tx ->
                    val isSent = tx.senderVpa == vpa
                    val iconVector = if (isSent) Icons.AutoMirrored.Filled.Send else Icons.AutoMirrored.Filled.CallReceived
                    val amountPrefix = if (isSent) "-" else "+"
                    val amountColor = if (isSent) Color(0xFFD93025) else UpiSuccessGreen
                    val targetName = if (isSent) "To: ${tx.receiverVpa}" else "From: ${tx.senderVpa}"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(Screen.Receipt.route)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    color = UpiLightBlue
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = UpiPrimaryBlue
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(targetName, fontWeight = FontWeight.Bold)
                                    Text(tx.status, fontSize = 12.sp, color = UpiSuccessGreen)
                                }
                            }
                            Text(
                                text = "$amountPrefix ₹${tx.amount}",
                                fontWeight = FontWeight.Bold,
                                color = amountColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            color = color
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
