package com.demo.upimesh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.demo.upimesh.ui.MainViewModel
import com.demo.upimesh.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController, viewModel: MainViewModel) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) { SplashScreen(navController, viewModel) }
        composable(Screen.Login.route) { LoginScreen(navController, viewModel) }
        composable(Screen.Register.route) { RegisterScreen(navController, viewModel) }
        composable(Screen.RegistrationSuccess.route) { RegistrationSuccessScreen(navController) }
        composable(Screen.ForgotPassword.route) { ForgotPasswordScreen(navController, viewModel) }
        composable(
            route = "${Screen.OtpVerification.route}?vpa={vpa}&name={name}&phone={phone}&mpin={mpin}&email={email}&bankName={bankName}&bankAccountNumber={bankAccountNumber}&cardNumber={cardNumber}&expiryDate={expiryDate}&cvv={cvv}",
            arguments = listOf(
                navArgument("vpa") { defaultValue = "" },
                navArgument("name") { defaultValue = "" },
                navArgument("phone") { defaultValue = "" },
                navArgument("mpin") { defaultValue = "" },
                navArgument("email") { defaultValue = "" },
                navArgument("bankName") { defaultValue = "" },
                navArgument("bankAccountNumber") { defaultValue = "" },
                navArgument("cardNumber") { defaultValue = "" },
                navArgument("expiryDate") { defaultValue = "" },
                navArgument("cvv") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val vpa = backStackEntry.arguments?.getString("vpa") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val mpin = backStackEntry.arguments?.getString("mpin") ?: ""
            val email = backStackEntry.arguments?.getString("email") ?: ""
            val bankName = backStackEntry.arguments?.getString("bankName") ?: ""
            val bankAccountNumber = backStackEntry.arguments?.getString("bankAccountNumber") ?: ""
            val cardNumber = backStackEntry.arguments?.getString("cardNumber") ?: ""
            val expiryDate = backStackEntry.arguments?.getString("expiryDate") ?: ""
            val cvv = backStackEntry.arguments?.getString("cvv") ?: ""
            OtpVerificationScreen(
                navController = navController,
                viewModel = viewModel,
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
            )
        }
        composable(Screen.Home.route) { HomeScreen(navController, viewModel) }
        composable(Screen.Contacts.route) { ContactsScreen(navController) }
        composable(Screen.SendMoney.route) { SendMoneyScreen(navController, viewModel) }
        composable(Screen.ReceiveMoney.route) { ReceiveMoneyScreen(navController, viewModel) }
        composable(Screen.QrScanner.route) { QrScannerScreen(navController) }
        composable(Screen.QrGenerator.route) { QrGeneratorScreen(navController, viewModel) }
        composable(Screen.OfflineQueue.route) { OfflineQueueScreen(navController, viewModel) }
        composable(Screen.MeshStatus.route) { MeshStatusScreen(navController, viewModel) }
        composable(Screen.BluetoothTransfer.route) { BluetoothTransferScreen(navController, viewModel) }
        composable(Screen.Receipt.route) { ReceiptScreen(navController, viewModel) }
        composable(Screen.SmsInbox.route) { SmsInboxScreen(navController, viewModel) }
        composable(Screen.Notifications.route) { NotificationsScreen(navController, viewModel) }
        composable(Screen.TransactionHistory.route) { TransactionHistoryScreen(navController, viewModel) }
        composable(Screen.Analytics.route) { AnalyticsScreen(navController) }
        composable(Screen.Profile.route) { ProfileScreen(navController, viewModel) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.PresentationMode.route) { PresentationModeScreen(navController, viewModel) }
        composable(Screen.DemoMode.route) { DemoModeScreen(navController, viewModel) }
        composable(Screen.SecurityClassroom.route) { SecurityClassroomScreen(navController) }
        composable(Screen.DeveloperMode.route) { DeveloperModeScreen(navController, viewModel) }
    }
}
