package com.example.ui.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.repository.PrismRepository
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrismOrange
import com.example.ui.theme.PrismPink
import com.example.ui.theme.PrismPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun CompleteProfileSetupScreen(
    userId: String,
    repository: PrismRepository,
    onProfileCompleted: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Explicitly start with empty username and empty profile picture — NEVER import from Google Mail account
    var customUsername by remember { mutableStateOf("") }
    var customDisplayName by remember { mutableStateOf("") }
    var customBio by remember { mutableStateOf("") }
    var selectedAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedAvatarBase64 by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            errorMessage = null
            coroutineScope.launch {
                try {
                    val decoded = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                    if (decoded != null) {
                        val scaled = Bitmap.createScaledBitmap(decoded, 320, 320, true)
                        selectedAvatarBitmap = scaled
                        val output = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 84, output)
                        selectedAvatarBase64 = "data:image/jpeg;base64," +
                            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    } else {
                        errorMessage = "Could not load selected image from gallery."
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to read gallery photo: ${e.localizedMessage}"
                }
            }
        }
    }

    val ringBrush = remember {
        Brush.linearGradient(listOf(PrismPurple, PrismPink, PrismOrange))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Set Up Your Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose your custom @username and add your Profile Picture (PP) from your gallery.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Gallery Profile Picture (PP) Picker
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .clickable {
                        galleryPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("setup_pick_avatar_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .border(3.dp, ringBrush, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedAvatarBitmap != null) {
                        Image(
                            bitmap = selectedAvatarBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Pick Profile Picture",
                                tint = PrismPink,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add PP",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrismPink,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PrismPink)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selectedAvatarBitmap != null) Icons.Default.CheckCircle else Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = PrismPink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedAvatarBase64.isNotBlank()) "Change Profile Picture from Gallery" else "Select Profile Picture from Gallery"
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Custom Username Input
            OutlinedTextField(
                value = customUsername,
                onValueChange = { input ->
                    customUsername = input.removePrefix("@")
                        .replace(" ", "_")
                        .lowercase()
                        .take(30)
                    errorMessage = null
                },
                label = { Text("Create Username (Required)") },
                placeholder = { Text("your_username") },
                prefix = { Text("@", color = PrismPink, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = PrismPink
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrismPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_username_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Display Name Input
            OutlinedTextField(
                value = customDisplayName,
                onValueChange = { customDisplayName = it.take(50) },
                label = { Text("Display Name (Optional)") },
                placeholder = { Text("Your Name") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrismPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_display_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bio Input
            OutlinedTextField(
                value = customBio,
                onValueChange = { customBio = it.take(150) },
                label = { Text("Bio (Optional)") },
                placeholder = { Text("Tell people about yourself...") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrismPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_bio_input")
            )

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val cleanHandle = customUsername.trim().removePrefix("@").trim()
                    if (cleanHandle.length < 2) {
                        errorMessage = "Please enter a username (at least 2 characters)."
                        return@Button
                    }
                    if (selectedAvatarBase64.isBlank()) {
                        errorMessage = "Please select a Profile Picture (PP) from your gallery."
                        return@Button
                    }

                    isSaving = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            // Check if another user already took this username
                            val existingByHandle = repository.getUserByUsername(cleanHandle)
                            if (existingByHandle != null && existingByHandle.effectiveUserId != userId) {
                                isSaving = false
                                errorMessage = "Username @$cleanHandle is already taken. Please choose another."
                                return@launch
                            }

                            val newProfile = UserProfile(
                                id = userId,
                                userId = userId,
                                username = cleanHandle,
                                displayName = customDisplayName.trim().ifBlank { cleanHandle },
                                avatarUrl = selectedAvatarBase64,
                                bio = customBio.trim(),
                                profileSetupCompleted = true
                            )
                            repository.createOrUpdateProfile(newProfile)
                            isSaving = false
                            onProfileCompleted(newProfile)
                        } catch (e: Exception) {
                            isSaving = false
                            errorMessage = e.localizedMessage ?: "Failed to save profile."
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("setup_complete_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving Profile...")
                } else {
                    Text(
                        text = "Continue to SHD Media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
