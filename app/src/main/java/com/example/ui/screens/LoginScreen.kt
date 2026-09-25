package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.SupermarketGreen
import com.example.ui.theme.SupermarketGreenDark
import com.example.ui.theme.SupermarketGreenLight
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val loginError by authViewModel.loginError.collectAsState()
    val allUsers by authViewModel.allUsers.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showInitialSetupDialog by remember { mutableStateOf(false) }

    val isFirstTimeSetup = allUsers.isEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_card")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo Badge
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo_1790177482465),
                    contentDescription = "Supermarket POS Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(2.dp, SupermarketGreen.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Supermarket POS & Audit",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = SupermarketGreenDark
                )

                Text(
                    text = "Sign in to access register & audit engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isFirstTimeSetup) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SupermarketGreenLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = SupermarketGreenDark
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Initial System Provisioning",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SupermarketGreenDark
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No accounts configured yet. Create the initial administrator credentials to begin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SupermarketGreenDark
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showInitialSetupDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_admin_btn")
                            ) {
                                Text("Provision Administrator Account")
                            }
                        }
                    }
                }

                if (loginError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = loginError!!,
                            color = CriticalRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        if (loginError != null) authViewModel.clearError()
                    },
                    label = { Text("Email or Username") },
                    placeholder = { Text("admin@store.com or admin") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (loginError != null) authViewModel.clearError()
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { authViewModel.login(username, password) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_submit_btn")
                ) {
                    if (isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.LightGray.copy(alpha = 0.25f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DEBUG BUILD • PBKDF2 Encrypted Storage Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInitialSetupDialog) {
        InitialAdminDialog(
            onDismiss = { showInitialSetupDialog = false },
            onConfirm = { adminUser, adminPass ->
                authViewModel.registerInitialAdmin(adminUser, adminPass) { success, _ ->
                    if (success) {
                        showInitialSetupDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun InitialAdminDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Initial Administrator Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configure the primary administrator account for this system. Use an email address for Firebase Cloud Authentication or a username for local offline credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (localError != null) {
                    Text(
                        text = localError!!,
                        color = CriticalRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = adminUsername,
                    onValueChange = { adminUsername = it },
                    label = { Text("Admin Email or Username") },
                    placeholder = { Text("admin@store.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setup_admin_username")
                )
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = { adminPassword = it },
                    label = { Text("Password (min 6 characters)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("setup_admin_password")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("setup_admin_confirm_password")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (adminUsername.isBlank() || adminPassword.isBlank()) {
                        localError = "Username and password cannot be empty"
                        return@Button
                    }
                    if (adminPassword.length < 6) {
                        localError = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (adminPassword != confirmPassword) {
                        localError = "Passwords do not match"
                        return@Button
                    }
                    onConfirm(adminUsername, adminPassword)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SupermarketGreen),
                modifier = Modifier.testTag("confirm_admin_setup_btn")
            ) {
                Text("Create Administrator")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

