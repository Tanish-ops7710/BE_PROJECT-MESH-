package com.demo.upimesh.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demo.upimesh.data.api.NetworkModule
import com.demo.upimesh.data.local.AppDatabase
import com.demo.upimesh.data.local.LocalTransactionEntity
import com.demo.upimesh.data.local.NotificationEntity
import com.demo.upimesh.data.local.OfflinePacketEntity
import com.demo.upimesh.data.local.SmsEntity
import com.demo.upimesh.data.repository.UpiRepository
import com.demo.upimesh.model.Account
import com.demo.upimesh.model.MeshPacket
import com.demo.upimesh.model.MeshStateResponse
import com.demo.upimesh.model.TransactionStatus
import com.demo.upimesh.util.BluetoothTransferService
import com.demo.upimesh.util.NotificationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UpiRepository
    private val prefs = application.getSharedPreferences("upi_mesh_prefs", Context.MODE_PRIVATE)

    /** Single shared instance — all screens must use viewModel.bluetoothTransferService */
    val bluetoothTransferService: BluetoothTransferService = BluetoothTransferService(application)

    val offlinePackets: StateFlow<List<OfflinePacketEntity>>
    private val _localTransactions = MutableStateFlow<List<LocalTransactionEntity>>(emptyList())
    val localTransactions: StateFlow<List<LocalTransactionEntity>> = _localTransactions.asStateFlow()
    val smsList: StateFlow<List<SmsEntity>>
    val notificationsList: StateFlow<List<NotificationEntity>>

    private val _meshState = MutableStateFlow<MeshStateResponse?>(null)
    val meshState: StateFlow<MeshStateResponse?> = _meshState.asStateFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _currentAccountBalance = MutableStateFlow(BigDecimal("4500.00"))
    val currentAccountBalance: StateFlow<BigDecimal> = _currentAccountBalance.asStateFlow()

    private val _demoProgress = MutableStateFlow(0f)
    val demoProgress: StateFlow<Float> = _demoProgress.asStateFlow()

    private val _demoStatusMessage = MutableStateFlow("Ready to launch automated demo")
    val demoStatusMessage: StateFlow<String> = _demoStatusMessage.asStateFlow()

    private val _activeHopIndex = MutableStateFlow(-1)
    val activeHopIndex: StateFlow<Int> = _activeHopIndex.asStateFlow()

    fun updateServerUrl(newUrl: String) {
        val normalized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        NetworkModule.baseUrl = normalized
        prefs.edit().putString("server_base_url", normalized).apply()
        val apiService = NetworkModule.createApiService()
        repository.updateApiService(apiService)
        viewModelScope.launch {
            repository.fetchServerPublicKey()
        }
    }

    fun refreshServerKey() {
        viewModelScope.launch {
            repository.fetchServerPublicKey()
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        
        val savedUrl = prefs.getString("server_base_url", null)
        if (!savedUrl.isNullOrBlank()) {
            NetworkModule.baseUrl = if (savedUrl.endsWith("/")) savedUrl else "$savedUrl/"
        }

        val apiService = NetworkModule.createApiService()
        repository = UpiRepository(apiService, database.appDao(), application)

        offlinePackets = repository.allPackets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        viewModelScope.launch {
            repository.allTransactions.collect { transactions ->
                _localTransactions.value = transactions
            }
        }
        smsList = repository.allSms.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        notificationsList = repository.allNotifications.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        refreshMeshState()
        loadSession()
        initBluetoothMesh()
        viewModelScope.launch {
            repository.fetchServerPublicKey()
            repository.uploadPendingPackets()
        }

        // Auto-refresh transaction list and balance when BT service signals DB change
        viewModelScope.launch {
            bluetoothTransferService.needsRefresh.collect {
                refreshTransactions()
                refreshAccountFromServer()
            }
        }

        // When Phone B successfully uploads a received BT packet, sync with the server
        // so the real settled transaction (correct amount + VPA) appears in history.
        viewModelScope.launch {
            bluetoothTransferService.needsSync.collect {
                // refreshTransactions() calls syncTransactionsWithServer() internally,
                // which will insert the real settled transaction from the server.
                refreshTransactions()
                refreshAccountFromServer()
            }
        }
    }

    fun initBluetoothMesh() {
        repository.initBluetoothMesh()
    }

    fun stopBluetoothMesh() {
        repository.stopBluetoothMesh()
    }

    fun uploadPendingPackets(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.uploadPendingPackets()
            refreshAccountFromServer()
            refreshTransactions()
            onComplete?.invoke(res.getOrDefault(0))
        }
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            repository.syncTransactionsWithServer()
            _localTransactions.value = repository.allTransactions.first()
        }
    }

    private fun loadSession() {
        // Do not auto-login. Always require explicit login on app start.
        // Clear any stale session so SplashScreen navigates to Login.
        _currentAccount.value = null
    }

    private fun saveSession(token: String, vpa: String, name: String, balance: BigDecimal) {
        val userBalance = prefs.getString("user_balance_$vpa", balance.toPlainString()) ?: balance.toPlainString()
        prefs.edit().apply {
            putString("session_token", token)
            putString("session_vpa", vpa)
            putString("session_name", name)
            putString("user_balance_$vpa", userBalance)
            apply()
        }
    }

    fun refreshMeshState() {
        viewModelScope.launch {
            val res = repository.getMeshState()
            if (res.isSuccess) {
                _meshState.value = res.getOrNull()
            }
        }
    }

    fun sendMoney(receiverVpa: String, amountStr: String, pin: String, note: String, onSuccess: (MeshPacket) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val amount = amountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            if (amount <= BigDecimal.ZERO) {
                onError("Enter a valid transaction amount")
                return@launch
            }
            val senderVpa = _currentAccount.value?.vpa ?: "user@demo"
            val res = repository.createAndQueuePacket(senderVpa, receiverVpa, amount, pin, note)
            if (res.isSuccess) {
                refreshTransactions()
                val packet = res.getOrNull()!!
                _currentAccountBalance.value = _currentAccountBalance.value.subtract(amount)

                // Cache sender balance locally on THIS device
                _currentAccount.value = _currentAccount.value?.copy(balance = _currentAccountBalance.value)
                prefs.edit().putString("user_balance_$senderVpa", _currentAccountBalance.value.toPlainString()).apply()

                NotificationUtils.sendNotification(
                    getApplication(),
                    "Payment Initiated",
                    "₹$amount pending to $receiverVpa via BLE Mesh."
                )
                onSuccess(packet)
            } else {
                onError("Failed to generate mesh packet: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun triggerGossip() {
        viewModelScope.launch {
            repository.triggerGossip()
            refreshMeshState()
        }
    }

    fun triggerFlushBridge() {
        viewModelScope.launch {
            repository.uploadPendingPackets()
            repository.flushBridgeUploads()
            refreshMeshState()
            refreshTransactions()
            refreshAccountFromServer()
        }
    }

    private fun refreshAccountFromServer() {
        val vpa = _currentAccount.value?.vpa ?: return
        
        // 1. Sync local state first (in case BluetoothTransferService deducted balance offline)
        val localBalStr = prefs.getString("user_balance_$vpa", null)
        if (localBalStr != null) {
            _currentAccountBalance.value = java.math.BigDecimal(localBalStr)
        }

        // 2. Try to fetch the latest truth from the server
        viewModelScope.launch {
            try {
                val resp = repository.fetchAccounts()
                if (resp.isSuccess) {
                    val accounts = resp.getOrNull() ?: emptyList()
                    val myAcc = accounts.find { it.vpa == vpa }
                    if (myAcc != null) {
                        _currentAccount.value = myAcc
                        _currentAccountBalance.value = myAcc.balance
                        prefs.edit().putString("user_balance_$vpa", myAcc.balance.toPlainString()).apply()
                    }
                }
            } catch (_: Exception) { 
                // Offline — keep the local balance that we just loaded
            }
        }
    }

    // Authentication Actions

    fun sendOtp(phoneNumber: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.sendOtp(phoneNumber)
            if (res.isSuccess) {
                val otp = res.getOrNull()?.get("otp")?.toString() ?: ""
                onResult(true, otp)
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to send OTP")
            }
        }
    }

    fun verifyOtp(phoneNumber: String, otp: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.verifyOtp(phoneNumber, otp)
            if (res.isSuccess) {
                onResult(true, "OTP Verified")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Invalid OTP")
            }
        }
    }

    fun registerUser(
        vpa: String,
        name: String,
        phone: String,
        mpin: String,
        email: String,
        bankName: String,
        bankAccountNumber: String,
        cardNumber: String,
        expiryDate: String,
        cvv: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.registerUser(
                vpa = vpa,
                holderName = name,
                phoneNumber = phone,
                mpin = mpin,
                email = email,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                cardNumber = cardNumber,
                expiryDate = expiryDate,
                cvv = cvv
            )
            if (res.isSuccess) {
                onResult(true, "Registration successful")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun loginUser(vpa: String, mpin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.loginUser(vpa, mpin)
            if (res.isSuccess) {
                val loginResponse = res.getOrNull()!!
                // Use server balance (most accurate across devices)
                val serverBal = loginResponse.account.balance
                
                _currentAccount.value = loginResponse.account
                _currentAccountBalance.value = serverBal
                prefs.edit()
                    .putString("user_balance_$vpa", serverBal.toPlainString())
                    .putString("offline_mpin_$vpa", mpin)
                    .putString("offline_name_$vpa", loginResponse.account.holderName)
                    .apply()
                    
                saveSession(loginResponse.token, loginResponse.vpa, loginResponse.account.holderName, serverBal)
                onResult(true, "Login successful")
            } else {
                // Offline fallback
                val storedMpin = prefs.getString("offline_mpin_$vpa", null)
                if (storedMpin != null && storedMpin == mpin) {
                    val name = prefs.getString("offline_name_$vpa", "User") ?: "User"
                    val balStr = prefs.getString("user_balance_$vpa", "0") ?: "0"
                    val token = prefs.getString("session_token", "offline-token") ?: "offline-token"
                    
                    val dummyAccount = com.demo.upimesh.model.Account(
                        vpa = vpa,
                        holderName = name,
                        balance = java.math.BigDecimal(balStr)
                    )
                    _currentAccount.value = dummyAccount
                    _currentAccountBalance.value = java.math.BigDecimal(balStr)
                    saveSession(token, vpa, name, java.math.BigDecimal(balStr))
                    onResult(true, "Offline login successful")
                } else {
                    onResult(false, res.exceptionOrNull()?.message ?: "Login failed")
                }
            }
        }
    }

    fun logoutUser(onResult: (Boolean) -> Unit = {}) {
        val token = prefs.getString("session_token", null)
        if (token != null) {
            viewModelScope.launch {
                repository.logoutUser(token)
            }
        }
        prefs.edit()
            .remove("session_token")
            .remove("session_vpa")
            .remove("session_name")
            .apply()
        _currentAccount.value = null
        onResult(true)
    }

    fun changeMpin(oldMpin: String, newMpin: String, onResult: (Boolean, String) -> Unit) {
        val vpa = _currentAccount.value?.vpa
        if (vpa == null) {
            onResult(false, "No active session")
            return
        }
        viewModelScope.launch {
            val res = repository.changeMpin(vpa, oldMpin, newMpin)
            if (res.isSuccess) {
                onResult(true, "MPIN changed successfully")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to change MPIN")
            }
        }
    }

    fun resetMpin(vpa: String, newMpin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.resetMpin(vpa, newMpin)
            if (res.isSuccess) {
                onResult(true, "MPIN reset successfully")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to reset MPIN")
            }
        }
    }

    fun deleteAccount(vpa: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteAccount(vpa)
            if (res.isSuccess) {
                logoutUser()
                onResult(true, "Account deleted successfully")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to delete account")
            }
        }
    }

    fun runAutomatedDemo(onComplete: () -> Unit) {
        viewModelScope.launch {
            _demoProgress.value = 0.05f
            _demoStatusMessage.value = "1/7: Initializing Offline Session (No Internet)..."
            _activeHopIndex.value = 0
            delay(2000)

            _demoProgress.value = 0.20f
            _demoStatusMessage.value = "2/7: Generating Encrypted Hybrid Packet (RSA-2048 + AES-GCM)..."
            repository.createAndQueuePacket("tanish@demo", "rahul@demo", BigDecimal("500.00"), "1234", "Demo Mesh Payment")
            delay(2500)

            _demoProgress.value = 0.40f
            _demoStatusMessage.value = "3/7: BLE Gossip Broadcast: Tanish -> Rahul (Hop 1)..."
            _activeHopIndex.value = 1
            repository.triggerGossip()
            delay(3000)

            _demoProgress.value = 0.60f
            _demoStatusMessage.value = "4/7: BLE Gossip Broadcast: Rahul -> Priya -> Aman (Hop 2 & 3)..."
            _activeHopIndex.value = 3
            repository.triggerGossip()
            delay(3000)

            _demoProgress.value = 0.80f
            _demoStatusMessage.value = "5/7: Bridge Node Uploading to Bank Server..."
            _activeHopIndex.value = 4
            repository.flushBridgeUploads()
            delay(3000)

            _demoProgress.value = 0.95f
            _demoStatusMessage.value = "6/7: Bank Server Settling Ledger & Deducting Balances..."
            _activeHopIndex.value = 5
            _currentAccountBalance.value = _currentAccountBalance.value.subtract(BigDecimal("500.00"))
            delay(2500)

            _demoProgress.value = 1.0f
            _demoStatusMessage.value = "7/7: Demo Complete! Receipt & SMS Generated Successfully."
            _activeHopIndex.value = -1

            NotificationUtils.sendNotification(
                getApplication(),
                "Demo Execution Complete",
                "Successfully routed ₹500 across 3 BLE hops to Bank Server."
            )
            onComplete()
        }
    }
}
