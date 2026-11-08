package com.example.ui.direct

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DirectMessage
import com.example.data.model.UserProfile
import com.example.data.repository.PrismRepository
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrismOrange
import com.example.ui.theme.PrismPink
import com.example.ui.theme.PrismPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DirectUserAvatar(
    avatarUrl: String,
    username: String,
    modifier: Modifier = Modifier
) {
    val cleanUrl = if (avatarUrl.contains("googleusercontent.com", ignoreCase = true)) "" else avatarUrl
    if (cleanUrl.startsWith("data:image")) {
        val bitmap = remember(cleanUrl) {
            try {
                val base64Part = cleanUrl.substringAfter(",")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = username,
                modifier = modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            return
        }
    } else if (cleanUrl.isNotBlank()) {
        AsyncImage(
            model = cleanUrl,
            contentDescription = username,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        return
    }

    Text(
        text = (username.removePrefix("@").firstOrNull() ?: 'U').uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = PrismPink
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMessagesScreen(
    repository: PrismRepository,
    currentUserId: String,
    initialRecipientId: String? = null,
    initialRecipientUsername: String? = null,
    onOpenProfile: ((userId: String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val conversations by repository.observeConversations().collectAsState(initial = emptyList())
    val exploreUsers by repository.observeExploreUsers().collectAsState(initial = emptyList())
    val myProfile by repository.observeProfile(currentUserId).collectAsState(initial = null)

    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var activeRecipientId by remember { mutableStateOf<String?>(null) }
    var activeRecipientUsername by remember { mutableStateOf("Chat") }

    var showNewChatSheet by remember { mutableStateOf(false) }
    var showSendByUsernameDialog by remember { mutableStateOf(false) }
    var isOpeningConversation by remember { mutableStateOf(false) }
    var chatErrorBanner by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun openConversationSafely(targetUserId: String, targetUsername: String) {
        val cleanId = targetUserId.trim()
        if (cleanId.isBlank() || cleanId == currentUserId) {
            chatErrorBanner = if (cleanId == currentUserId) {
                "You cannot start a direct chat with yourself."
            } else {
                "Unable to open chat: invalid recipient profile ID."
            }
            return
        }
        if (isOpeningConversation) return
        isOpeningConversation = true
        chatErrorBanner = null
        coroutineScope.launch {
            try {
                val convId = repository.getOrCreateConversation(cleanId)
                activeConversationId = convId
                activeRecipientId = cleanId
                activeRecipientUsername = targetUsername.ifBlank { "user" }
                searchQuery = ""
                showNewChatSheet = false
            } catch (e: Exception) {
                chatErrorBanner = e.localizedMessage ?: "Could not open conversation. Please check your connection and try again."
            } finally {
                isOpeningConversation = false
            }
        }
    }

    // If navigated with initial recipient, open that chat immediately with full error handling
    LaunchedEffect(initialRecipientId) {
        if (!initialRecipientId.isNullOrBlank() && initialRecipientId != currentUserId) {
            openConversationSafely(initialRecipientId, initialRecipientUsername ?: "user")
        }
    }

    // Dynamic search effect for username/query
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            delay(250)
            searchResults = try {
                repository.searchUsers(searchQuery)
            } catch (_: Exception) {
                emptyList()
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    if (activeConversationId != null && activeRecipientId != null) {
        ChatDetailScreen(
            conversationId = activeConversationId!!,
            recipientId = activeRecipientId!!,
            recipientUsername = activeRecipientUsername,
            currentUserId = currentUserId,
            myUsername = myProfile?.username?.ifBlank { "you" } ?: "you",
            repository = repository,
            onOpenProfile = onOpenProfile,
            onBack = {
                activeConversationId = null
                activeRecipientId = null
            }
        )
    } else {
        BackHandler { onBack() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Direct Messages",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        // Quick Action: Send to @username
                        IconButton(
                            onClick = { showSendByUsernameDialog = true },
                            modifier = Modifier.testTag("send_by_username_action")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = "Send to @username",
                                tint = PrismPink
                            )
                        }
                        // Start Chat button
                        IconButton(
                            onClick = { showNewChatSheet = true },
                            modifier = Modifier.testTag("new_dm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Start Chat",
                                tint = PrismPink
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Error Banner if any conversation action failed
                if (chatErrorBanner != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = chatErrorBanner.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { chatErrorBanner = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Search Bar for @username
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by @username or name to chat...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrismPink,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dm_search_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Button: Send message via @username directly
                        Card(
                            onClick = { showSendByUsernameDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = PrismPink.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(PrismPink),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AlternateEmail,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Send Message to @username",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Directly message any registered user via their handle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Send",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PrismPink
                                )
                            }
                        }
                    }
                }

                // If searching, show dynamic search results
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text = "Search Results for \"$searchQuery\"",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrismPink, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (searchResults.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No registered user found matching \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        showSendByUsernameDialog = true
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Try exact lookup for @${searchQuery.removePrefix("@")}")
                                }
                            }
                        }
                    } else {
                        val validSearchResults = searchResults.filter {
                            it.effectiveUserId.isNotBlank() && it.effectiveUserId != currentUserId
                        }
                        items(validSearchResults, key = { it.effectiveUserId }) { user ->
                            UserSearchRow(
                                user = user,
                                onStartChat = {
                                    openConversationSafely(user.effectiveUserId, user.username)
                                }
                            )
                        }
                    }
                } else {
                    // "Registered Creators (Tap to Chat)"
                    val realOtherUsers = exploreUsers.filter {
                        it.effectiveUserId.isNotBlank() && it.effectiveUserId != currentUserId
                    }
                    if (realOtherUsers.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                                Text(
                                    text = "Registered Creators (Tap to Chat)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(realOtherUsers, key = { it.effectiveUserId }) { creator ->
                                        CreatorAvatarBubble(
                                            user = creator,
                                            onClick = {
                                                openConversationSafely(creator.effectiveUserId, creator.username)
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }

                    // Existing Conversations Section
                    item {
                        Text(
                            text = "Conversations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    if (conversations.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(PrismPink.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = PrismPink,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "Your Direct Messages",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Chat directly with real users on SHD Media. Any message you send appears in their Messages tab so they can read and reply to you in real time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { showSendByUsernameDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send to @username")
                                }
                            }
                        }
                    } else {
                        items(conversations, key = { it.id }) { conv ->
                            val otherParticipantUid = conv.participants.firstOrNull { it != currentUserId } ?: "user"
                            var otherProfile by remember { mutableStateOf<UserProfile?>(null) }

                            // Resolve other participant metadata safely
                            LaunchedEffect(otherParticipantUid) {
                                otherProfile = try {
                                    repository.getProfile(otherParticipantUid)
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            val name = otherProfile?.displayName?.ifBlank { null }
                                ?: otherProfile?.username?.ifBlank { null }
                                ?: "Creator"
                            val handle = otherProfile?.username?.ifBlank { null }
                                ?: "user_${otherParticipantUid.take(5)}"
                            val avatarUrl = otherProfile?.avatarUrl.orEmpty()
                            val hasIncomingMessage = conv.lastSenderId.isNotBlank() &&
                                conv.lastSenderId != currentUserId &&
                                conv.lastMessageText != "Started a new conversation"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeConversationId = conv.id
                                        activeRecipientId = otherParticipantUid
                                        activeRecipientUsername = handle
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(PrismPurple, PrismPink, PrismOrange))
                                            )
                                            .padding(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            DirectUserAvatar(
                                                avatarUrl = avatarUrl,
                                                username = handle
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(OnlineGreen)
                                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "@$handle",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PrismPink
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val prefix = if (conv.lastSenderId == currentUserId) "You: " else ""
                                    Text(
                                        text = prefix + conv.lastMessageText.ifBlank { "Sent a message" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (hasIncomingMessage) FontWeight.Bold else FontWeight.Normal,
                                        color = if (hasIncomingMessage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (hasIncomingMessage) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PrismPink)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Reply",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Open Chat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }

        // Send via @username Dialog
        if (showSendByUsernameDialog) {
            SendByUsernameDialog(
                repository = repository,
                currentUserId = currentUserId,
                onDismiss = { showSendByUsernameDialog = false },
                onSuccess = { convId, targetUser ->
                    showSendByUsernameDialog = false
                    activeConversationId = convId
                    activeRecipientId = targetUser.effectiveUserId
                    activeRecipientUsername = targetUser.username
                }
            )
        }

        // New Chat Contact Picker BottomSheet
        if (showNewChatSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNewChatSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New Message",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            showNewChatSheet = false
                            showSendByUsernameDialog = true
                        }) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("By @username")
                        }
                    }

                    val chatCandidates = exploreUsers.filter {
                        it.effectiveUserId.isNotBlank() && it.effectiveUserId != currentUserId
                    }
                    if (chatCandidates.isEmpty()) {
                        Text(
                            text = "No other registered users yet. Invite a friend to sign in with Google or use '@username' search!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        Text(
                            text = "Select a real user to chat with:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.height(340.dp)) {
                            items(chatCandidates, key = { it.effectiveUserId }) { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            openConversationSafely(candidate.effectiveUserId, candidate.username)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(PrismPurple.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DirectUserAvatar(
                                            avatarUrl = candidate.avatarUrl,
                                            username = candidate.username
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.displayName.ifBlank { candidate.username },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "@${candidate.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PrismPink
                                        )
                                    }
                                    Text(
                                        text = "Chat",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrismPink,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorAvatarBubble(
    user: UserProfile,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
            .testTag("creator_avatar_${user.username}")
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(PrismPurple, PrismPink, PrismOrange))
                    )
                    .padding(2.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    DirectUserAvatar(
                        avatarUrl = user.avatarUrl,
                        username = user.username
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(66.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UserSearchRow(
    user: UserProfile,
    onStartChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStartChat() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PrismPink.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            DirectUserAvatar(
                avatarUrl = user.avatarUrl,
                username = user.username
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName.ifBlank { user.username },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = PrismPink
            )
        }
        Button(
            onClick = onStartChat,
            colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("Chat", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SendByUsernameDialog(
    repository: PrismRepository,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSuccess: (conversationId: String, targetUser: UserProfile) -> Unit
) {
    var usernameInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var resolvedUser by remember { mutableStateOf<UserProfile?>(null) }
    var isCheckingUser by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Real-time lookup as username is typed
    LaunchedEffect(usernameInput) {
        val clean = usernameInput.trim().removePrefix("@").trim()
        if (clean.length >= 2) {
            isCheckingUser = true
            errorMessage = null
            delay(350)
            val found = try {
                repository.getUserByUsername(clean)
            } catch (_: Exception) {
                null
            }
            isCheckingUser = false
            if (found != null) {
                if (found.effectiveUserId == currentUserId) {
                    errorMessage = "You cannot send messages to yourself."
                    resolvedUser = null
                } else {
                    resolvedUser = found
                    errorMessage = null
                }
            } else {
                resolvedUser = null
            }
        } else {
            resolvedUser = null
            isCheckingUser = false
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = null,
                    tint = PrismPink,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Send to @username", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter the @username of any registered user to direct message them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Username") },
                    prefix = { Text("@", color = PrismPink, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("satyabratadey10") },
                    singleLine = true,
                    isError = errorMessage != null,
                    trailingIcon = {
                        if (isCheckingUser) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (resolvedUser != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Found", tint = OnlineGreen)
                        } else if (errorMessage != null) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_dm_username_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Resolved User Preview Card
                if (resolvedUser != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PrismPink.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                DirectUserAvatar(
                                    avatarUrl = resolvedUser!!.avatarUrl,
                                    username = resolvedUser!!.username
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = resolvedUser!!.displayName.ifBlank { resolvedUser!!.username },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${resolvedUser!!.username} • Verified User",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnlineGreen
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    label = { Text("Message") },
                    placeholder = { Text("Hey! Loved your post...") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_dm_text_field"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetUser = resolvedUser
                    val text = messageInput.trim()
                    if (targetUser == null) {
                        errorMessage = "Please enter a valid registered @username."
                        return@Button
                    }
                    if (text.isBlank()) {
                        errorMessage = "Message cannot be empty."
                        return@Button
                    }
                    isSending = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = repository.sendDirectMessageByUsername(
                            recipientUsername = targetUser.username,
                            text = text
                        )
                        isSending = false
                        result.onSuccess { (convId, user) ->
                            onSuccess(convId, user)
                        }.onFailure { err ->
                            errorMessage = err.localizedMessage ?: "Failed to send message."
                        }
                    }
                },
                enabled = resolvedUser != null && messageInput.isNotBlank() && !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                modifier = Modifier.testTag("send_dm_confirm_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("Send & Chat")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Captures the current Compose/Android View hierarchy into a PNG file in
 * `/storage/emulated/0/Download/SHD_Instagram_Chat_<username>_<timestamp>.png`.
 */
private suspend fun saveChatScreenshotToDownloads(
    context: Context,
    view: View,
    recipientUsername: String
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = withContext(Dispatchers.Main) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            view.draw(canvas)
            bmp
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "SHD_Instagram_Chat_${recipientUsername}_$timestamp.png"
        val displayPath = "/storage/emulated/0/Download/$fileName"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Could not create screenshot in Download folder."))
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        Result.success(displayPath)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    recipientId: String,
    recipientUsername: String,
    currentUserId: String,
    myUsername: String = "you",
    repository: PrismRepository,
    onOpenProfile: ((userId: String) -> Unit)? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val rootView = LocalView.current
    val messages by repository.observeMessages(conversationId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var inputMessage by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<DirectMessage?>(null) }
    var sendErrorText by remember { mutableStateOf<String?>(null) }
    var statusBannerText by remember { mutableStateOf<String?>(null) }
    var isSendingMessage by remember { mutableStateOf(false) }

    var recipientProfile by remember { mutableStateOf<UserProfile?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val igGradientBrush = remember {
        Brush.linearGradient(listOf(PrismPurple, PrismPink, PrismOrange))
    }

    LaunchedEffect(recipientId) {
        recipientProfile = try {
            repository.getProfile(recipientId)
        } catch (_: Exception) {
            null
        }
    }

    // Mark incoming messages from the other user as read ("Seen") and auto-scroll to newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            try {
                repository.markConversationMessagesRead(conversationId)
            } catch (_: Exception) {
            }
        }
    }

    val displayHandle = recipientProfile?.username?.ifBlank { null } ?: recipientUsername
    val displayName = recipientProfile?.displayName?.ifBlank { null } ?: displayHandle
    val recipientAvatarUrl = recipientProfile?.avatarUrl.orEmpty()

    // Photo/Screenshot picker for sending images directly in Instagram DM thread
    val chatPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isSendingMessage = true
                sendErrorText = null
                try {
                    val base64Photo = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val original = BitmapFactory.decodeStream(stream)
                            if (original != null) {
                                val maxDim = 640
                                val ratio = minOf(
                                    maxDim.toFloat() / original.width.coerceAtLeast(1),
                                    maxDim.toFloat() / original.height.coerceAtLeast(1),
                                    1f
                                )
                                val w = (original.width * ratio).toInt().coerceAtLeast(1)
                                val h = (original.height * ratio).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(original, w, h, true)
                                val out = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                                "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                            } else null
                        }
                    }
                    if (!base64Photo.isNullOrBlank()) {
                        val captionText = inputMessage.trim().ifBlank { "Shared a photo 📸" }
                        val quoteMsg = replyingToMessage
                        inputMessage = ""
                        replyingToMessage = null
                        repository.sendMessage(
                            conversationId = conversationId,
                            recipientId = recipientId,
                            text = captionText,
                            imageUrl = base64Photo,
                            replyToMessageId = quoteMsg?.id.orEmpty(),
                            replyToText = quoteMsg?.text.orEmpty(),
                            replyToSenderUsername = if (quoteMsg?.senderId == currentUserId) myUsername else displayHandle
                        )
                    }
                } catch (e: Exception) {
                    sendErrorText = e.localizedMessage ?: "Could not send photo."
                } finally {
                    isSendingMessage = false
                }
            }
        }
    }

    fun sendUserMessage(textToSend: String) {
        val clean = textToSend.trim()
        if (clean.isBlank() || isSendingMessage) return
        val quoted = replyingToMessage
        isSendingMessage = true
        sendErrorText = null
        inputMessage = ""
        replyingToMessage = null

        coroutineScope.launch {
            try {
                repository.sendMessage(
                    conversationId = conversationId,
                    recipientId = recipientId,
                    text = clean,
                    replyToMessageId = quoted?.id.orEmpty(),
                    replyToText = quoted?.text.orEmpty(),
                    replyToSenderUsername = if (quoted == null) "" else if (quoted.senderId == currentUserId) myUsername else displayHandle
                )
            } catch (e: Exception) {
                sendErrorText = e.localizedMessage ?: "Failed to send message."
            } finally {
                isSendingMessage = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onOpenProfile?.invoke(recipientId)
                        }
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(igGradientBrush)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DirectUserAvatar(
                                        avatarUrl = recipientAvatarUrl,
                                        username = displayHandle
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@$displayHandle • Active now",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnlineGreen
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save Screenshot of the Instagram Chat Screen to /storage/emulated/0/Download
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val res = saveChatScreenshotToDownloads(context, rootView, displayHandle)
                                res.onSuccess { savedPath ->
                                    statusBannerText = "Chat Screenshot saved: $savedPath"
                                }.onFailure { err ->
                                    sendErrorText = "Screenshot failed: ${err.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.testTag("capture_chat_screenshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Save Chat Screenshot",
                            tint = PrismPink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (statusBannerText != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OnlineGreen.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = OnlineGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusBannerText.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { statusBannerText = null }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (sendErrorText != null) {
                Text(
                    text = sendErrorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val lastOutgoingMessageId = remember(messages, currentUserId) {
                messages.lastOrNull { it.senderId == currentUserId }?.id
            }

            // Main Instagram Message Thread (Real-Time Firestore Stream)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Instagram Top Profile Card inside Chat Thread
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(igGradientBrush)
                                .padding(2.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                DirectUserAvatar(
                                    avatarUrl = recipientAvatarUrl,
                                    username = displayHandle
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "@$displayHandle • SHD Media",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onOpenProfile?.invoke(recipientId) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("View Profile", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUserId
                    val isLastOutgoing = isMine && msg.id == lastOutgoingMessageId

                    InstagramMessageRow(
                        msg = msg,
                        isMine = isMine,
                        isLastOutgoing = isLastOutgoing,
                        recipientHandle = displayHandle,
                        recipientAvatarUrl = recipientAvatarUrl,
                        igGradientBrush = igGradientBrush,
                        onReplyClick = { replyingToMessage = msg },
                        onToggleHeart = {
                            coroutineScope.launch {
                                repository.toggleMessageReaction(
                                    conversationId = conversationId,
                                    messageId = msg.id,
                                    currentEmoji = msg.reactionEmoji,
                                    targetEmoji = "❤️"
                                )
                            }
                        }
                    )
                }
            }

            // Quoted Reply Banner above Composer (Instagram style)
            AnimatedVisibility(visible = replyingToMessage != null) {
                replyingToMessage?.let { quoted ->
                    val targetAuthor = if (quoted.senderId == currentUserId) "yourself" else "@$displayHandle"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(PrismPink)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to $targetAuthor",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrismPink,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = quoted.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { replyingToMessage = null },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Reply",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Instagram Pill Composer Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera / Gallery Photo button inside circular gradient badge
                IconButton(
                    onClick = {
                        chatPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(igGradientBrush)
                        .testTag("dm_attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Send Photo or Screenshot",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Message @$displayHandle...") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                chatPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Gallery",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dm_message_input"),
                    shape = RoundedCornerShape(26.dp),
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrismPink,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (inputMessage.isBlank()) {
                    // Quick Heart Send (like Instagram DM)
                    IconButton(
                        onClick = { sendUserMessage("❤️") },
                        enabled = !isSendingMessage,
                        modifier = Modifier.testTag("send_heart_dm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Send Heart",
                            tint = PrismPink,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { sendUserMessage(inputMessage) },
                        enabled = !isSendingMessage,
                        modifier = Modifier.testTag("send_dm_button")
                    ) {
                        if (isSendingMessage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrismPink,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = PrismPink,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstagramMessageRow(
    msg: DirectMessage,
    isMine: Boolean,
    isLastOutgoing: Boolean,
    recipientHandle: String,
    recipientAvatarUrl: String,
    igGradientBrush: Brush,
    onReplyClick: () -> Unit,
    onToggleHeart: () -> Unit
) {
    val timeLabel = remember(msg.createdAt) {
        val date = msg.createdAt?.toDate() ?: Date()
        SimpleDateFormat("h:mm a", Locale.US).format(date)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        // Quoted Message Header (Instagram style)
        if (msg.replyToText.isNotBlank()) {
            val replyHeaderLabel = if (isMine) {
                "You replied to @${msg.replyToSenderUsername.ifBlank { recipientHandle }}"
            } else {
                "@$recipientHandle replied to you"
            }
            Column(
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                modifier = Modifier
                    .padding(
                        start = if (isMine) 48.dp else 38.dp,
                        end = if (isMine) 8.dp else 48.dp,
                        bottom = 3.dp
                    )
            ) {
                Text(
                    text = replyHeaderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isMine) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = msg.replyToText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isMine) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(PrismPink)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left avatar for incoming partner replies (Instagram style)
            if (!isMine) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PrismPink.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    DirectUserAvatar(
                        avatarUrl = recipientAvatarUrl,
                        username = recipientHandle
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                // Action icons on the left of outgoing bubble (Reply & Heart)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                ) {
                    IconButton(
                        onClick = onReplyClick,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleHeart,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (msg.reactionEmoji.isNotBlank()) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "React",
                            tint = if (msg.reactionEmoji.isNotBlank()) PrismPink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Message Bubble + Floating Heart Reaction Pill
            Box {
                val bubbleShape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isMine) 20.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 20.dp
                )

                val bubbleModifier = if (isMine) {
                    Modifier
                        .widthIn(max = 270.dp)
                        .clip(bubbleShape)
                        .background(igGradientBrush)
                } else {
                    Modifier
                        .widthIn(max = 270.dp)
                        .clip(bubbleShape)
                        .background(Color(0xFF262626))
                }

                Column(
                    modifier = bubbleModifier
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = onToggleHeart
                        )
                        .padding(
                            horizontal = if (msg.imageUrl.isNotBlank()) 6.dp else 14.dp,
                            vertical = if (msg.imageUrl.isNotBlank()) 6.dp else 10.dp
                        )
                ) {
                    // Attached Photo / Screenshot inside DM bubble
                    if (msg.imageUrl.isNotBlank()) {
                        val attachedBitmap = remember(msg.imageUrl) {
                            if (msg.imageUrl.startsWith("data:image")) {
                                try {
                                    val bytes = Base64.decode(msg.imageUrl.substringAfter(","), Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) {
                                    null
                                }
                            } else null
                        }
                        if (attachedBitmap != null) {
                            Image(
                                bitmap = attachedBitmap.asImageBitmap(),
                                contentDescription = "Chat Photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = if (msg.imageUrl.isNotBlank()) Modifier.padding(horizontal = 8.dp) else Modifier
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                // Floating Heart Reaction Badge (Instagram style)
                if (msg.reactionEmoji.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(if (isMine) Alignment.BottomStart else Alignment.BottomEnd)
                            .offset(x = if (isMine) (-6).dp else 6.dp, y = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable { onToggleHeart() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = msg.reactionEmoji,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Action icons on the right of incoming bubble (Reply & Heart)
            if (!isMine) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                ) {
                    IconButton(
                        onClick = onReplyClick,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleHeart,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (msg.reactionEmoji.isNotBlank()) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "React",
                            tint = if (msg.reactionEmoji.isNotBlank()) PrismPink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Instagram "Seen just now" indicator underneath last outgoing message
        if (isLastOutgoing) {
            Spacer(modifier = Modifier.height(if (msg.reactionEmoji.isNotBlank()) 10.dp else 4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (msg.read) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(PrismPink.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DirectUserAvatar(
                            avatarUrl = recipientAvatarUrl,
                            username = recipientHandle
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Seen just now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = "Sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        } else if (msg.reactionEmoji.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
