package com.example.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.data.repository.PrismRepository
import com.example.ui.auth.performSignOut
import com.example.ui.feed.CommentsBottomSheet
import com.example.ui.feed.PostCard
import com.example.ui.theme.PrismOrange
import com.example.ui.theme.PrismPink
import com.example.ui.theme.PrismPurple
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

import androidx.compose.material.icons.filled.SystemUpdate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    currentUserId: String,
    repository: PrismRepository,
    onSignOut: () -> Unit,
    onCheckForUpdate: (() -> Unit)? = null,
    onMessageUser: ((recipientId: String, recipientUsername: String) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val profile by repository.observeProfile(userId).collectAsState(initial = null)
    val userPosts by repository.observeUserPosts(userId).collectAsState(initial = emptyList())
    val followers by repository.observeFollowers(userId).collectAsState(initial = emptyList())

    val isFollowing = remember(followers, currentUserId) {
        followers.any { it.followerId == currentUserId }
    }
    val effectiveFollowersCount = maxOf(followers.size.toLong(), profile?.followersCount ?: 0L)

    var showEditProfileSheet by remember { mutableStateOf(false) }
    var selectedPostDetail by remember { mutableStateOf<Post?>(null) }
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val isMyProfile = userId == currentUserId

    val gradientBrush = remember {
        Brush.linearGradient(listOf(PrismPurple, PrismPink, PrismOrange))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.username?.let { "@$it" } ?: "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    if (isMyProfile && onCheckForUpdate != null) {
                        IconButton(
                            onClick = onCheckForUpdate,
                            modifier = Modifier.testTag("app_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "App Update Manager",
                                tint = PrismPink
                            )
                        }
                    }
                    if (isMyProfile) {
                        IconButton(
                            onClick = {
                                performSignOut(context, credentialManager, coroutineScope, onSignOut)
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val filteredPosts = if (selectedTab == 1) {
            userPosts.filter { it.mediaType == "video" }
        } else {
            userPosts
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("profile_photo_grid"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Profile Header Area
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .border(2.5.dp, gradientBrush, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatar = profile?.avatarUrl.orEmpty()
                            if (avatar.startsWith("data:image")) {
                                val bytes = try {
                                    Base64.decode(avatar.substringAfter(","), Base64.DEFAULT)
                                } catch (_: Exception) {
                                    null
                                }
                                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else if (avatar.isNotBlank()) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = (profile?.username?.firstOrNull() ?: 'U').uppercase(),
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = PrismPink
                                )
                            }
                        }

                        // Stats (Posts, Followers, Following)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.weight(1f)
                        ) {
                            StatColumn(count = userPosts.size.toLong(), label = "Posts")
                            StatColumn(count = effectiveFollowersCount, label = "Followers")
                            StatColumn(count = profile?.followingCount ?: 0L, label = "Following")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Display Name & Username & Bio
                    Text(
                        text = profile?.displayName.orEmpty().ifBlank { "SHD Creator" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!profile?.username.isNullOrBlank()) {
                        Text(
                            text = "@${profile?.username}",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrismPink
                        )
                    }
                    if (!profile?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile?.bio.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons (Edit Profile / Follow / Message)
                    if (isMyProfile) {
                        Button(
                            onClick = { showEditProfileSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Edit Profile & Username",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            repository.toggleFollow(userId, effectiveFollowersCount)
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else PrismPink
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("follow_button")
                            ) {
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFollowing) MaterialTheme.colorScheme.onSurface else Color.White
                                )
                            }

                            Button(
                                onClick = {
                                    val handle = profile?.username ?: "user"
                                    onMessageUser?.invoke(userId, handle)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("profile_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Message",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = PrismPink
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(imageVector = Icons.Default.GridOn, contentDescription = "All Posts") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(imageVector = Icons.Default.Videocam, contentDescription = "Videos") }
                        )
                    }
                }
            }

            // Grid Items of User Posts
            if (filteredPosts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Default.Videocam else Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedTab == 1) "No videos uploaded yet" else "No posts yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedPostDetail = post },
                        contentAlignment = Alignment.Center
                    ) {
                        if (post.imageUrl.startsWith("data:image")) {
                            val bitmap = remember(post.imageUrl) {
                                try {
                                    val bytes = Base64.decode(post.imageUrl.substringAfter(","), Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = post.caption,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else if (post.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = post.caption,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (post.mediaType == "video") {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val mins = post.videoDurationSeconds / 60
                                val secs = post.videoDurationSeconds % 60
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = String.format("%d:%02d", mins, secs),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Post Detail Sheet when tapping any item in Profile grid
        selectedPostDetail?.let { post ->
            ModalBottomSheet(
                onDismissRequest = { selectedPostDetail = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                PostCard(
                    post = post,
                    currentUserId = currentUserId,
                    repository = repository,
                    onCommentClick = {
                        selectedPostForComments = post
                    },
                    onProfileClick = { selectedPostDetail = null },
                    onDirectMessageClick = { recipientId, recipientUsername ->
                        selectedPostDetail = null
                        onMessageUser?.invoke(recipientId, recipientUsername)
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        selectedPostForComments?.let { post ->
            CommentsBottomSheet(
                post = post,
                repository = repository,
                currentUserId = currentUserId,
                onDirectMessageClick = { recipientId, recipientUsername ->
                    selectedPostForComments = null
                    selectedPostDetail = null
                    onMessageUser?.invoke(recipientId, recipientUsername)
                },
                onDismiss = { selectedPostForComments = null }
            )
        }

        // Edit Profile Sheet
        if (showEditProfileSheet) {
            val initialProfile = profile ?: UserProfile(
                userId = currentUserId,
                username = "user_${currentUserId.take(6)}",
                displayName = "SHD Creator"
            )
            EditProfileBottomSheet(
                currentProfile = initialProfile,
                repository = repository,
                onDismiss = { showEditProfileSheet = false }
            )
        }
    }
}

@Composable
private fun StatColumn(count: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileBottomSheet(
    currentProfile: UserProfile,
    repository: PrismRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var username by remember { mutableStateOf(currentProfile.username) }
    var displayName by remember { mutableStateOf(currentProfile.displayName) }
    var bio by remember { mutableStateOf(currentProfile.bio) }
    var avatarBase64 by remember { mutableStateOf(currentProfile.avatarUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(originalBitmap, 320, 320, true)
                    val outputStream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    avatarBase64 = "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                }
            } catch (_: Exception) {
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Avatar picker
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(PrismPink.copy(alpha = 0.2f))
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBase64.startsWith("data:image")) {
                    val bitmap = remember(avatarBase64) {
                        try {
                            val bytes = Base64.decode(avatarBase64.substringAfter(","), Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else if (avatarBase64.isNotBlank()) {
                    AsyncImage(
                        model = avatarBase64,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Change Avatar",
                        tint = PrismPink
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.removePrefix("@").trim() },
                label = { Text("Username (@handle)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                minLines = 3,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (username.trim().length < 2) {
                        errorMessage = "Username must be at least 2 characters."
                        return@Button
                    }
                    isSaving = true
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            repository.createOrUpdateProfile(
                                currentProfile.copy(
                                    username = username.trim(),
                                    displayName = displayName.trim(),
                                    bio = bio.trim(),
                                    avatarUrl = avatarBase64
                                )
                            )
                            isSaving = false
                            onDismiss()
                        } catch (e: Exception) {
                            isSaving = false
                            errorMessage = e.localizedMessage ?: "Failed to update profile."
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
