package com.example.demoapplication.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.demoapplication.viewmodel.AuthViewModel

@Composable
fun HomeScreen(navController: NavController) {
    val authViewModel: AuthViewModel = viewModel()
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val userData by authViewModel.userData.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.loadUserData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Name: ${userData?.fullName ?: currentUser?.displayName ?: "N/A"}",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Email: ${currentUser?.email ?: "N/A"}",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "User ID: ${currentUser?.uid ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                authViewModel.signOut()
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
                Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}