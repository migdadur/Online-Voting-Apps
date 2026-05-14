package com.example.demoapplication.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.demoapplication.viewmodel.AuthViewModel

@Composable
fun LoginScreen(navController: NavController) {
    val authViewModel: AuthViewModel = viewModel()
    val context = LocalContext.current
    val loginState by authViewModel.loginState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Handle login result
    LaunchedEffect(loginState) {
        when (loginState) {
            is AuthViewModel.AuthState.Success -> {
                Toast.makeText(context, "Login Successful!", Toast.LENGTH_LONG).show()
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            }
            is AuthViewModel.AuthState.Error -> {
                Toast.makeText(
                    context,
                    (loginState as AuthViewModel.AuthState.Error).message,
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    email.isEmpty() -> Toast.makeText(context, "Please enter email", Toast.LENGTH_SHORT).show()
                    password.isEmpty() -> Toast.makeText(context, "Please enter password", Toast.LENGTH_SHORT).show()
                    else -> authViewModel.login(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = loginState !is AuthViewModel.AuthState.Loading
        ) {
            if (loginState is AuthViewModel.AuthState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { navController.navigate("signup") }
        ) {
            Text("Don't have an account? Sign Up")
        }

        TextButton(
            onClick = {
                if (email.isNotEmpty()) {
                    authViewModel.resetPassword(email)
                    Toast.makeText(context, "Password reset email sent", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Enter your email first", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text("Forgot Password?")
        }
    }
}