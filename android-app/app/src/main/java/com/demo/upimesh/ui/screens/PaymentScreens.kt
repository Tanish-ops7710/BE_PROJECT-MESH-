package com.demo.upimesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.demo.upimesh.ui.MainViewModel
import com.demo.upimesh.ui.navigation.Screen
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen
import com.demo.upimesh.util.BiometricAuthManager
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val transferService = viewModel.bluetoothTransferService
    val pendingTransactions by viewModel.localTransactions.collectAsState(initial = emptyList())
    val hasPendingBluetoothTx = pendingTransactions.any { it.status == "Pending Bluetooth" }
    var receiverVpa by remember { mutableStateOf("rahul@demo") }
    var amount by remember { mutableStateOf("500") }
    var note by remember { mutableStateOf("Offline BLE payment") }
    var mpin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Money (Offline Mesh)") },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Payment Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = receiverVpa,
                        onValueChange = { receiverVpa = it },
                        label = { Text("Receiver VPA") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Add Note (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mpin,
                        onValueChange = { mpin = it },
                        label = { Text("Enter 4-Digit MPIN") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (hasPendingBluetoothTx) {
                Button(
                    onClick = {
                        navController.navigate(Screen.BluetoothTransfer.route)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send via Bluetooth", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    if (mpin.isEmpty()) {
                        Toast.makeText(context, "Please enter your MPIN", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        BiometricAuthManager.authenticate(
                            activity = activity,
                            onSuccess = {
                                viewModel.sendMoney(
                                    receiverVpa = receiverVpa,
                                    amountStr = amount,
                                    pin = mpin,
                                    note = note,
                                    onSuccess = {
                                        Toast.makeText(context, "Payment created. Send via Bluetooth when ready.", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onError = {
                                // Fallback to MPIN payment directly
                                viewModel.sendMoney(
                                    receiverVpa = receiverVpa,
                                    amountStr = amount,
                                    pin = mpin,
                                    note = note,
                                    onSuccess = {
                                        Toast.makeText(context, "Payment created. Send via Bluetooth when ready.", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )
                    } else {
                        viewModel.sendMoney(
                            receiverVpa = receiverVpa,
                            amountStr = amount,
                            pin = mpin,
                            note = note,
                            onSuccess = {
                                Toast.makeText(context, "Payment created. Send via Bluetooth when ready.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Authorize & Broadcast Packet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveMoneyScreen(navController: NavController, viewModel: MainViewModel) {
    val currentAccount by viewModel.currentAccount.collectAsState()
    val name = currentAccount?.holderName ?: "Offline User"
    val vpa = currentAccount?.vpa ?: "offline@demo"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Money") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
                    Text("Scan to Pay $name", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(vpa, color = UpiPrimaryBlue, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "QR Code",
                                tint = UpiPrimaryBlue,
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("P2P Offline BLE Mesh Ready", color = UpiSuccessGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan UPI QR Code") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    modifier = Modifier.size(260.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scanning",
                            tint = Color.White,
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Align QR code inside the frame", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Screen.SendMoney.route) },
                    colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                ) {
                    Text("Simulate Scanned QR -> Pay")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(navController: NavController, viewModel: MainViewModel) {
    val currentAccount by viewModel.currentAccount.collectAsState()
    val name = currentAccount?.holderName ?: "User"
    val vpa = currentAccount?.vpa ?: "offline@demo"
    var amount by remember { mutableStateOf("500") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My QR Code Generator") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Set Request Amount (₹)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Pay ₹$amount to $name", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = UpiPrimaryBlue,
                        modifier = Modifier.size(180.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("upi://pay?pa=$vpa&pn=$name&am=$amount", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}
