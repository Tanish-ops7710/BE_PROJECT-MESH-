package com.demo.upimesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.demo.upimesh.ui.theme.UpiDarkBlue
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController, viewModel: MainViewModel) {
    val currentAccount by viewModel.currentAccount.collectAsState()
    
    LaunchedEffect(Unit) {
        delay(2000)
        if (currentAccount != null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UpiPrimaryBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = "Logo",
                        tint = UpiPrimaryBlue,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Offline UPI Mesh",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bluetooth Gossip Peer-to-Peer Payments",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    var vpa by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = UpiPrimaryBlue,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF202124)
                )
                Text(
                    text = "Login to your Offline UPI account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5F6368)
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = vpa,
                    onValueChange = { vpa = it },
                    label = { Text("UPI ID (VPA)") },
                    placeholder = { Text("username@demo") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PASSWORD / MPIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = UpiPrimaryBlue)
                } else {
                    Button(
                        onClick = {
                            if (vpa.isEmpty() || pin.isEmpty()) {
                                Toast.makeText(context, "Please enter all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.loginUser(vpa, pin) { success, msg ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                    ) {
                        Text(text = "Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { navController.navigate(Screen.DeveloperMode.route) }) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Change Server IP", fontSize = 12.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { navController.navigate(Screen.ForgotPassword.route) }) {
                        Text("Forgot VPA/PIN?", color = UpiPrimaryBlue)
                    }
                    TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                        Text("Sign Up", color = UpiPrimaryBlue)
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    var vpa by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var mpin by remember { mutableStateOf("") }
    var confirmMpin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Create Account", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = vpa,
                    onValueChange = { vpa = it },
                    label = { Text("Desired VPA (e.g. name@demo)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = mpin,
                    onValueChange = { mpin = it },
                    label = { Text("4-Digit MPIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmMpin,
                    onValueChange = { confirmMpin = it },
                    label = { Text("Confirm MPIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = UpiPrimaryBlue)
                    }
                } else {
                    Button(
                        onClick = {
                            if (vpa.isEmpty() || name.isEmpty() || phone.isEmpty() || mpin.isEmpty()) {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (mpin != confirmMpin) {
                                Toast.makeText(context, "MPIN and Confirm MPIN do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.sendOtp(phone) { success, otp ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "OTP Generated! Code is: $otp", Toast.LENGTH_LONG).show()
                                    navController.navigate("${Screen.OtpVerification.route}?vpa=$vpa&name=$name&phone=$phone&mpin=$mpin")
                                } else {
                                    Toast.makeText(context, otp, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                    ) {
                        Text("Register Account")
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    var vpa by remember { mutableStateOf("") }
    var newMpin by remember { mutableStateOf("") }
    var confirmNewMpin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Reset PIN", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Enter your VPA and new MPIN details.", color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = vpa,
                    onValueChange = { vpa = it },
                    label = { Text("UPI VPA") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newMpin,
                    onValueChange = { newMpin = it },
                    label = { Text("New 4-Digit MPIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmNewMpin,
                    onValueChange = { confirmNewMpin = it },
                    label = { Text("Confirm New MPIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = UpiPrimaryBlue)
                    }
                } else {
                    Button(
                        onClick = {
                            if (vpa.isEmpty() || newMpin.isEmpty() || confirmNewMpin.isEmpty()) {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newMpin != confirmNewMpin) {
                                Toast.makeText(context, "MPINs do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.resetMpin(vpa, newMpin) { success, msg ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "MPIN reset successful", Toast.LENGTH_SHORT).show()
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.ForgotPassword.route) { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                    ) {
                        Text("Reset MPIN")
                    }
                }
            }
        }
    }
}

@Composable
fun OtpVerificationScreen(
    navController: NavController,
    viewModel: MainViewModel,
    vpa: String,
    name: String,
    phone: String,
    mpin: String
) {
    val context = LocalContext.current
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
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
                Text(text = "OTP Verification", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Enter 6-digit OTP sent to $phone.", color = Color.Gray)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = otp,
                    onValueChange = { if (it.length <= 6) otp = it },
                    label = { Text("Enter OTP") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = UpiPrimaryBlue)
                } else {
                    Button(
                        onClick = {
                            if (otp.length < 6) {
                                Toast.makeText(context, "Please enter 6-digit OTP", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            viewModel.verifyOtp(phone, otp) { verifySuccess, verifyMsg ->
                                if (verifySuccess) {
                                    viewModel.registerUser(vpa, name, phone, mpin) { registerSuccess, registerMsg ->
                                        isLoading = false
                                        if (registerSuccess) {
                                            Toast.makeText(context, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                                            navController.navigate(Screen.Login.route) {
                                                popUpTo(Screen.Register.route) { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, registerMsg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, verifyMsg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                    ) {
                        Text("Verify & Register")
                    }
                }
            }
        }
    }
}
