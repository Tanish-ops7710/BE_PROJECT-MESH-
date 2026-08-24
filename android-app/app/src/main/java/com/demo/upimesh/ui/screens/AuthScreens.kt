package com.demo.upimesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.demo.upimesh.data.api.NetworkModule
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
                    Text("Change Server IP (Current: ${NetworkModule.baseUrl})", fontSize = 12.sp, color = Color.Gray)
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
    var email by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var bankAccountNumber by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var mpin by remember { mutableStateOf("") }
    var confirmMpin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Register Demo UPI",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = UpiDarkBlue
            )
            Text(
                text = "Secure Simulation registration",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Personal details section
                    Text(
                        text = "1. Personal Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = UpiPrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = vpa,
                        onValueChange = { vpa = it },
                        label = { Text("Desired VPA (e.g. name@demo)") },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Bank info section
                    Text(
                        text = "2. Bank Account Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = UpiPrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bankAccountNumber,
                        onValueChange = { bankAccountNumber = it },
                        label = { Text("Demo Bank Account Number") },
                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Card simulation section
                    Text(
                        text = "3. Card Verification (Simulation)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = UpiPrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { if (it.length <= 16) cardNumber = it },
                        label = { Text("Demo Debit Card Number") },
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = { if (it.length <= 5) expiryDate = it },
                            label = { Text("Expiry (MM/YY)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = cvv,
                            onValueChange = { if (it.length <= 3) cvv = it },
                            label = { Text("CVV") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // PIN setup section
                    Text(
                        text = "4. Setup Secure PIN",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = UpiPrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mpin,
                        onValueChange = { if (it.length <= 4) mpin = it },
                        label = { Text("4-Digit UPI PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmMpin,
                        onValueChange = { if (it.length <= 4) confirmMpin = it },
                        label = { Text("Confirm UPI PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = UpiPrimaryBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = UpiPrimaryBlue)
            } else {
                Button(
                    onClick = {
                        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || vpa.isEmpty() ||
                            bankName.isEmpty() || bankAccountNumber.isEmpty() || cardNumber.isEmpty() ||
                            expiryDate.isEmpty() || cvv.isEmpty() || mpin.isEmpty()) {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (mpin != confirmMpin) {
                            Toast.makeText(context, "UPI PINs do not match", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        viewModel.sendOtp(phone) { success, otp ->
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, "OTP Generated! Code is: $otp", Toast.LENGTH_LONG).show()
                                navController.navigate(
                                    "${Screen.OtpVerification.route}?vpa=$vpa&name=$name&phone=$phone&mpin=$mpin&email=$email&bankName=$bankName&bankAccountNumber=$bankAccountNumber&cardNumber=$cardNumber&expiryDate=$expiryDate&cvv=$cvv"
                                )
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
                    Text("Verify Details via OTP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Already have an account? ")
                TextButton(
                    onClick = { navController.navigate(Screen.Login.route) { popUpTo(0) } },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Login", color = UpiPrimaryBlue, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
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
    mpin: String,
    email: String,
    bankName: String,
    bankAccountNumber: String,
    cardNumber: String,
    expiryDate: String,
    cvv: String
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
                                    viewModel.registerUser(
                                        vpa = vpa,
                                        name = name,
                                        phone = phone,
                                        mpin = mpin,
                                        email = email,
                                        bankName = bankName,
                                        bankAccountNumber = bankAccountNumber,
                                        cardNumber = cardNumber,
                                        expiryDate = expiryDate,
                                        cvv = cvv
                                    ) { registerSuccess, registerMsg ->
                                        isLoading = false
                                        if (registerSuccess) {
                                            Toast.makeText(context, "Registration successful!", Toast.LENGTH_LONG).show()
                                            navController.navigate(Screen.RegistrationSuccess.route) {
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

@Composable
fun RegistrationSuccessScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF0F9D58),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Registration Successful",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF202124)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your demo UPI account has been successfully created and saved in the backend MySQL database.",
                    fontSize = 14.sp,
                    color = Color(0xFF5F6368),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = UpiPrimaryBlue)
                ) {
                    Text("Go to Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
