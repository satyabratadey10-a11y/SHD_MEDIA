package com.example.ui.feed

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.Post
import com.example.data.repository.PrismRepository
import com.example.ui.theme.LikeRed
import com.example.ui.theme.PrismMagenta
import com.example.ui.theme.PrismOrange
import com.example.ui.theme.PrismPink
import com.example.ui.theme.PrismPurple
import com.example.ui.theme.StoryBorderGradientEnd
import com.example.ui.theme.StoryBorderGradientMiddle
import com.example.ui.theme.StoryBorderGradientStart
import com.example.ui.components.ShdMediaLogoBadge
import com.example.util.VideoValidationSpec
import com.example.util.VideoValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    repository: PrismRepository,
    currentUserId: String,
    onOpenDirectMessages: (recipientId: String?, recipientUsername: String?) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val posts by repository.observeFeedPosts().collectAsState(initial = emptyList())
    val exploreUsers by repository.observeExploreUsers().collectAsState(initial = emptyList())
    val myProfile by repository.observeProfile(currentUserId).collectAsState(initial = null)
    var feedAlgorithmMode by remember { mutableStateOf("Latest") }
    var selectedPostForComments by remember { mutableStateOf<Post?>(null) }
    var showCreatePostDialog by remember { mutableStateOf(false) }

    val storyBrush = remember {
        Brush.linearGradient(
            colors = listOf(StoryBorderGradientStart, StoryBorderGradientMiddle, StoryBorderGradientEnd)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("feed_post_list")
        ) {
            // Top App Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShdMediaLogoBadge(size = 36.dp, cornerRadius = 10.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SHD Media",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showCreatePostDialog = true },
                            modifier = Modifier.testTag("new_post_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Upload Video or Photo",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { onOpenDirectMessages(null, null) },
                            modifier = Modifier.testTag("direct_messages_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Direct Messages",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Creators / Stories Row (Real Users in Firestore)
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    // Upload Button Item
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showCreatePostDialog = true }
                                .padding(end = 4.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UserAvatarContent(
                                        avatarUrl = myProfile?.avatarUrl.orEmpty(),
                                        username = myProfile?.username ?: "Me"
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(PrismPink)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    items(
                        exploreUsers.filter { it.effectiveUserId.isNotBlank() && it.effectiveUserId != currentUserId },
                        key = { it.effectiveUserId }
                    ) { user ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onOpenProfile(user.effectiveUserId) }
                                .width(70.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .border(2.5.dp, storyBrush, CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                UserAvatarContent(
                                    avatarUrl = user.avatarUrl,
                                    username = user.username
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = user.username.ifBlank { "Creator" },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
            }

            // Feed Sorting Filter Bar: "Latest" vs "For You" vs "Videos Only"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Latest", "For You", "Videos").forEach { mode ->
                        val isSelected = feedAlgorithmMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) PrismPink else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { feedAlgorithmMode = mode }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    "Latest" -> "All Latest Posts"
                                    "For You" -> "Recommended"
                                    else -> "Videos (480p)"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Feed Posts List
            val displayedPosts = when (feedAlgorithmMode) {
                "Videos" -> posts.filter { it.mediaType == "video" }
                "For You" -> {
                    if (myProfile?.likedCategories?.isNotEmpty() == true) {
                        posts.sortedByDescending { post ->
                            var score = 0
                            if (myProfile?.likedCategories?.contains(post.contentCategory) == true) {
                                score += 50
                            }
                            score += (post.likesCount * 2).toInt()
                            score
                        }
                    } else {
                        posts
                    }
                }
                else -> posts
            }

            if (displayedPosts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PrismPink.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (feedAlgorithmMode == "Videos") Icons.Default.Videocam else Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = PrismPink,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (feedAlgorithmMode == "Videos") "No Videos Uploaded Yet" else "No Media in Feed Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Upload a photo or a video (up to 5 minutes, max 480p, max 60 FPS) to share with everyone on SHD Media!",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showCreatePostDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Video or Photo")
                        }
                    }
                }
            } else {
                items(displayedPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        currentUserId = currentUserId,
                        repository = repository,
                        onCommentClick = { selectedPostForComments = post },
                        onProfileClick = { onOpenProfile(post.authorId) },
                        onDirectMessageClick = { recipientId, recipientUsername ->
                            onOpenDirectMessages(recipientId, recipientUsername)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Create Post Modal Sheet
        if (showCreatePostDialog) {
            CreatePostBottomSheet(
                repository = repository,
                onDismiss = { showCreatePostDialog = false }
            )
        }

        // Comments Modal Sheet
        selectedPostForComments?.let { post ->
            CommentsBottomSheet(
                post = post,
                repository = repository,
                currentUserId = currentUserId,
                onDirectMessageClick = { recipientId, recipientUsername ->
                    onOpenDirectMessages(recipientId, recipientUsername)
                },
                onDismiss = { selectedPostForComments = null }
            )
        }
    }
}

@Composable
fun UserAvatarContent(
    avatarUrl: String,
    username: String
) {
    val cleanUrl = if (avatarUrl.contains("googleusercontent.com", ignoreCase = true)) "" else avatarUrl
    if (cleanUrl.startsWith("data:image")) {
        val bitmap = remember(cleanUrl) {
            try {
                val bytes = Base64.decode(cleanUrl.substringAfter(","), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = username,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            return
        }
    } else if (cleanUrl.isNotBlank()) {
        AsyncImage(
            model = cleanUrl,
            contentDescription = username,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        return
    }

    Text(
        text = (username.firstOrNull() ?: 'U').uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = PrismPink,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun PostCard(
    post: Post,
    currentUserId: String,
    repository: PrismRepository,
    onCommentClick: () -> Unit,
    onProfileClick: () -> Unit,
    onDirectMessageClick: ((recipientId: String, recipientUsername: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val likes by repository.observePostLikes(post.id).collectAsState(initial = emptyList())
    val comments by repository.observeComments(post.id).collectAsState(initial = emptyList())
    val authorProfile by repository.observeProfile(post.authorId).collectAsState(initial = null)

    val isLikedByMe = remember(likes, currentUserId) {
        likes.any { it.userId == currentUserId }
    }
    var isBookmarked by remember { mutableStateOf(false) }

    val displayUsername = authorProfile?.username?.ifBlank { post.authorUsername }
        ?: post.authorUsername.ifBlank { "user" }
    val displayAvatar = authorProfile?.avatarUrl?.ifBlank { post.authorAvatarUrl }
        ?: post.authorAvatarUrl

    // Video playback state
    var isVideoPlaying by remember(post.id) { mutableStateOf(false) }
    var isVideoLoading by remember(post.id) { mutableStateOf(false) }
    var playableVideoFile by remember(post.id) { mutableStateOf<File?>(null) }
    var videoLoadError by remember(post.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Author Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onProfileClick() }
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PrismPink.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarContent(
                        avatarUrl = displayAvatar,
                        username = displayUsername
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@$displayUsername",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (post.mediaType == "video") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrismPink.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val resText = if (post.videoWidth > 0 && post.videoHeight > 0) {
                                    "${minOf(post.videoWidth, post.videoHeight)}p"
                                } else "480p"
                                val fpsText = if (post.videoFps > 0) "${post.videoFps}fps" else ""
                                Text(
                                    text = listOf("VIDEO", resText, fpsText).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = PrismPink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    val subtitleParts = buildList {
                        if (post.contentCategory.isNotBlank()) add(post.contentCategory)
                        if (post.filterName.isNotBlank() && post.filterName != "Normal") {
                            add("Filter: ${post.filterName}")
                        }
                    }
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (post.authorId == currentUserId) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                repository.deletePost(post.id)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    modifier = Modifier.testTag("delete_post_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Post",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Post Media Area (Photo or Video Player)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (post.mediaType == "video" && isVideoPlaying && playableVideoFile != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setVideoPath(playableVideoFile!!.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                videoLoadError = "Unable to decode video stream on this device."
                                isVideoPlaying = false
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Stop button overlay in corner
                IconButton(
                    onClick = { isVideoPlaying = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Render Photo or Video Thumbnail
                if (post.imageUrl.startsWith("data:image")) {
                    val base64Data = post.imageUrl.substringAfter(",")
                    val bitmap = remember(post.imageUrl) {
                        try {
                            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
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
                    } else {
                        Icon(
                            imageVector = if (post.mediaType == "video") Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                } else if (post.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = post.caption,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(PrismPurple, PrismMagenta, PrismOrange))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (post.mediaType == "video") Icons.Default.Videocam else Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // Video Play Overlay & Metadata Badge
                if (post.mediaType == "video") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable(enabled = !isVideoLoading) {
                                coroutineScope.launch {
                                    isVideoLoading = true
                                    videoLoadError = null
                                    val file = repository.loadVideoToPlayableFile(context, post)
                                    isVideoLoading = false
                                    if (file != null) {
                                        playableVideoFile = file
                                        isVideoPlaying = true
                                    } else {
                                        videoLoadError = "Could not load video from Firestore."
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideoLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Loading video from Firestore...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(PrismPink.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        // Duration & Spec badge at bottom-right
                        val mins = post.videoDurationSeconds / 60
                        val secs = post.videoDurationSeconds % 60
                        val durationFormatted = String.format("%d:%02d", mins, secs)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "▶ $durationFormatted",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        videoLoadError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // Action Buttons Row (Like, Comment, Share, Direct Message, Save)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val effectiveLikesCount = maxOf(likes.size.toLong(), post.likesCount)
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                repository.toggleLike(
                                    postId = post.id,
                                    postAuthorId = post.authorId,
                                    currentLikesCount = effectiveLikesCount,
                                    postCategory = post.contentCategory
                                )
                            } catch (_: Exception) {
                            }
                        }
                    },
                    modifier = Modifier.testTag("like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLikedByMe) LikeRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier.testTag("comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "Check out this ${post.mediaType} on SHD Media by @$displayUsername"
                            )
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out this ${post.mediaType} by @$displayUsername on SHD Media!\n\"${post.caption}\"\n${post.tags.joinToString(" ")}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share post via"))
                    },
                    modifier = Modifier.testTag("share_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (post.authorId != currentUserId) {
                    IconButton(
                        onClick = { onDirectMessageClick?.invoke(post.authorId, displayUsername) },
                        modifier = Modifier.testTag("dm_author_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Message @$displayUsername",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            IconButton(onClick = { isBookmarked = !isBookmarked }) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isBookmarked) PrismPink else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Likes Count (Real-time synced)
        val displayLikesCount = if (likes.isNotEmpty()) likes.size.toLong() else post.likesCount
        Text(
            text = "$displayLikesCount ${if (displayLikesCount == 1L) "like" else "likes"}",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )

        // Caption
        if (post.caption.isNotBlank()) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                Text(
                    text = "@$displayUsername ",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onProfileClick() }
                )
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Tags
        if (post.tags.isNotEmpty()) {
            Text(
                text = post.tags.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = PrismPink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }

        // View comments teaser (Real-time synced)
        val displayCommentsCount = maxOf(comments.size.toLong(), post.commentsCount)
        Text(
            text = if (displayCommentsCount > 0) {
                "View all $displayCommentsCount ${if (displayCommentsCount == 1L) "comment" else "comments"}"
            } else {
                "Add a comment..."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { onCommentClick() }
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostBottomSheet(
    repository: PrismRepository,
    initialUploadMode: String = "photo",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var uploadMode by remember { mutableStateOf(initialUploadMode) } // "photo" or "video"
    var caption by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Normal") }
    var selectedCategory by remember { mutableStateOf("General") }
    val selectedTags = remember { mutableStateListOf("#SHDMedia") }

    // Photo state
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var base64ImageString by remember { mutableStateOf("") }

    // Video state
    var validatedVideoSpec by remember { mutableStateOf<VideoValidationSpec?>(null) }
    var isInspectingVideo by remember { mutableStateOf(false) }
    var videoValidationError by remember { mutableStateOf<String?>(null) }

    // Upload progress state
    var isPosting by remember { mutableStateOf(false) }
    var uploadedChunks by remember { mutableIntStateOf(0) }
    var totalChunks by remember { mutableIntStateOf(0) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadMode = "photo"
            validatedVideoSpec = null
            videoValidationError = null
            uploadStatusMessage = null
            coroutineScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                    if (bitmap != null) {
                        val scaled = Bitmap.createScaledBitmap(bitmap, 720, 720, true)
                        selectedBitmap = scaled
                        val outputStream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
                        val bytes = outputStream.toByteArray()
                        base64ImageString = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } else {
                        uploadStatusMessage = "Failed to decode selected photo."
                    }
                } catch (e: Exception) {
                    uploadStatusMessage = "Failed to load selected photo: ${e.localizedMessage}"
                }
            }
        }
    }

    // Video picker launcher (enforces <= 5 mins, <= 480p, <= 60 FPS)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadMode = "video"
            selectedBitmap = null
            base64ImageString = ""
            validatedVideoSpec = null
            videoValidationError = null
            uploadStatusMessage = null
            isInspectingVideo = true

            coroutineScope.launch {
                val validationResult = withContext(Dispatchers.IO) {
                    VideoValidator.validateAndExtractVideo(context, uri)
                }
                isInspectingVideo = false
                validationResult.onSuccess { spec ->
                    validatedVideoSpec = spec
                    selectedBitmap = spec.thumbnailBitmap
                    base64ImageString = spec.thumbnailBase64
                }.onFailure { err ->
                    videoValidationError = err.message ?: "Selected video does not meet upload limits."
                }
            }
        }
    }

    val categories = listOf("General", "Travel", "Tech", "Fitness", "Food", "Nature", "Art", "Music", "Lifestyle")
    val filters = listOf("Normal", "Vibrant", "Sunset", "Noir", "Vintage", "Dreamy")
    val quickHashtags = listOf("#SHDMedia", "#Trending", "#Video", "#480p", "#Creator", "#Vibes", "#Daily")

    ModalBottomSheet(
        onDismissRequest = { if (!isPosting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create New Post",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { if (!isPosting) onDismiss() }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Media Type Switcher (Photo vs Video)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_photo_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uploadMode == "photo" && base64ImageString.isNotBlank()) {
                            PrismPink.copy(alpha = 0.15f)
                        } else Color.Transparent
                    )
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = PrismPink)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Photo", color = MaterialTheme.colorScheme.onSurface)
                }

                OutlinedButton(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uploadMode == "video" && validatedVideoSpec != null) {
                            PrismPink.copy(alpha = 0.15f)
                        } else Color.Transparent
                    )
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = PrismPink)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Video", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Video Limitations Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = PrismPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video upload limits: Max 5 Minutes • Max 480p • Max 60 FPS (Saved to Firestore)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        if (uploadMode == "video") {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isInspectingVideo -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrismPink)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Checking video duration, resolution & FPS...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    selectedBitmap != null -> {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Media Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (uploadMode == "video" && validatedVideoSpec != null) {
                            val spec = validatedVideoSpec!!
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.78f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Verified: ${spec.formattedDuration} • ${spec.resolutionLabel} • ${spec.fps} FPS",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(PrismPink.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uploadMode == "video") Icons.Default.Videocam else Icons.Default.Image,
                                    contentDescription = null,
                                    tint = PrismPink,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap 'Select Photo' or 'Select Video' above",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Videos: <= 5 min, <= 480p, <= 60 FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Video validation error card
            videoValidationError?.let { errorMsg ->
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selector
            Text(
                text = "Content Category",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }

            // Filter Selector
            Text(
                text = "Visual Filter",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) PrismPink else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Caption Input Field (Manual only — Auto Caption removed as requested)
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("caption_input_field"),
                placeholder = { Text("Write a caption for your post...") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrismPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Hashtags
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickHashtags.forEach { tag ->
                    val isSelected = selectedTags.contains(tag)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        label = { Text(tag, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isPosting && totalChunks > 0) {
                val progress = uploadedChunks.toFloat() / totalChunks.toFloat()
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrismPink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saving video to Firestore ($uploadedChunks / $totalChunks chunks)...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            val isReadyToPost = (uploadMode == "photo" && base64ImageString.isNotBlank()) ||
                (uploadMode == "video" && validatedVideoSpec != null && videoValidationError == null)

            // Share / Upload Button
            Button(
                onClick = {
                    if (!isReadyToPost) {
                        uploadStatusMessage = "Please select a valid photo or video (max 5m, 480p, 60fps) first."
                        return@Button
                    }
                    isPosting = true
                    uploadStatusMessage = null
                    coroutineScope.launch {
                        try {
                            if (uploadMode == "video" && validatedVideoSpec != null) {
                                repository.createVideoPost(
                                    videoSpec = validatedVideoSpec!!,
                                    caption = caption.trim(),
                                    filterName = selectedFilter,
                                    tags = selectedTags.toList(),
                                    contentCategory = selectedCategory,
                                    onProgress = { done, total ->
                                        uploadedChunks = done
                                        totalChunks = total
                                    }
                                )
                            } else {
                                repository.createPostWithVisualMetadata(
                                    imageUrl = base64ImageString,
                                    mediaType = "image",
                                    caption = caption.trim(),
                                    filterName = selectedFilter,
                                    tags = selectedTags.toList(),
                                    contentCategory = selectedCategory
                                )
                            }
                            isPosting = false
                            onDismiss()
                        } catch (e: Exception) {
                            isPosting = false
                            uploadStatusMessage = e.localizedMessage ?: "Failed to publish post to Firestore."
                        }
                    }
                },
                enabled = !isPosting && isReadyToPost,
                colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("publish_post_button")
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uploading to Firestore...")
                } else {
                    Text(
                        text = if (uploadMode == "video") "Upload Video to Feed" else "Share Photo to Feed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            uploadStatusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: Post,
    repository: PrismRepository,
    currentUserId: String,
    onDirectMessageClick: ((recipientId: String, recipientUsername: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val comments by repository.observeComments(post.id).collectAsState(initial = emptyList())
    var commentText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(320.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No comments yet. Be the first to comment!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PrismPink.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                UserAvatarContent(
                                    avatarUrl = comment.authorAvatarUrl,
                                    username = comment.authorUsername
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${comment.authorUsername}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (comment.authorId != currentUserId) {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onDirectMessageClick?.invoke(comment.authorId, comment.authorUsername)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Message @${comment.authorUsername}",
                                        tint = PrismPink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Comment Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add a comment...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrismPink,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            isSending = true
                            val currentCount = maxOf(comments.size.toLong(), post.commentsCount)
                            coroutineScope.launch {
                                try {
                                    repository.addComment(
                                        postId = post.id,
                                        postAuthorId = post.authorId,
                                        text = commentText.trim(),
                                        currentCommentsCount = currentCount
                                    )
                                    commentText = ""
                                } catch (_: Exception) {
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    },
                    enabled = commentText.isNotBlank() && !isSending,
                    modifier = Modifier.testTag("send_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Comment",
                        tint = if (commentText.isNotBlank()) PrismPink else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
