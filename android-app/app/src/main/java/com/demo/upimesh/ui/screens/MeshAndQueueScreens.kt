package com.demo.upimesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.demo.upimesh.ui.components.BleTopologyCanvas
import com.demo.upimesh.ui.theme.UpiDarkBlue
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen
import com.demo.upimesh.util.PdfGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineQueueScreen(navController: NavController, viewModel: MainViewModel) {
    val packets by viewModel.offlinePackets.collectAsState(initial = emptyList())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Packet Queue (Room DB)") },
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${packets.size} Packet(s) Waiting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        viewModel.triggerFlushBridge()
                        Toast.makeText(context, "Bridge Node Upload Triggered!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UpiSuccessGreen)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Bridge")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (packets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pending packets in Room DB.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(packets) { pkt ->
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
                                    Text("ID: ${pkt.packetId.take(12)}...", fontWeight = FontWeight.Bold)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = UpiPrimaryBlue.copy(alpha = 0.1f)
                                    ) {
                                        Text("TTL: ${pkt.ttl}", color = UpiPrimaryBlue, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Sender: ${pkt.senderVpa} → ${pkt.receiverVpa}", fontSize = 13.sp)
                                Text("Amount: ₹${pkt.amount}", fontWeight = FontWeight.Bold, color = UpiPrimaryBlue)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Ciphertext: ${pkt.ciphertext.take(32)}...", fontSize = 10.sp, color = Color.Gray)
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
fun MeshStatusScreen(navController: NavController, viewModel: MainViewModel) {
    val activeHopIndex by viewModel.activeHopIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Mesh Topology & Gossip Grid") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BleTopologyCanvas(activeHopIndex = activeHopIndex)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { viewModel.triggerGossip() },
                    colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Gossip Round")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { viewModel.triggerFlushBridge() },
                    colors = ButtonDefaults.buttonColors(containerColor = UpiSuccessGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Flush Bridge Nodes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val transactions by viewModel.localTransactions.collectAsState(initial = emptyList())
    val latestTx = transactions.firstOrNull()

    val senderVpa = latestTx?.senderVpa ?: "N/A"
    val receiverVpa = latestTx?.receiverVpa ?: "N/A"
    val amount = latestTx?.amount ?: "0.00"
    val packetId = latestTx?.packetId ?: "N/A"
    val status = latestTx?.status ?: "PENDING"
    val note = latestTx?.note ?: "Offline BLE Payment"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Receipt") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = UpiSuccessGreen,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("₹$amount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Transaction Success / Queued", color = UpiSuccessGreen, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(20.dp))

                    ReceiptDetailRow("Sender VPA", senderVpa)
                    ReceiptDetailRow("Receiver VPA", receiverVpa)
                    ReceiptDetailRow("Packet ID", packetId.take(16))
                    ReceiptDetailRow("Routing", "BLE gossip network relay active")
                    ReceiptDetailRow("Encryption", "RSA-2048 + AES-GCM")
                    ReceiptDetailRow("Settlement Status", status)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val file = PdfGenerator.generateTransactionReceipt(
                        context = context,
                        packetId = packetId,
                        senderVpa = senderVpa,
                        receiverVpa = receiverVpa,
                        amount = amount,
                        status = status,
                        note = note
                    )
                    if (file != null) {
                        Toast.makeText(context, "PDF Saved to ${file.name}!", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download PDF Receipt", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReceiptDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
