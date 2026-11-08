package com.example.ui.reels

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.components.ShdMediaLogoBadge
import com.example.ui.feed.CommentsBottomSheet
import com.example.ui.feed.CreatePostBottomSheet
import com.example.ui.feed.UserAvatarContent
import com.example.ui.theme.LikeRed
import com.example.ui.theme.PrismMagenta
import com.example.ui.theme.PrismOrange
import com.example.ui.theme.PrismPink
import com.example.ui.theme.PrismPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelsScreen(
    repository: PrismRepository,
    currentUserId: String,
    onOpenDirectMessages: (recipientId: String?, recipientUsername: String?) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val allPosts by repository.observeFeedPosts().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedReelForComments by remember { mutableStateOf<Post?>(null) }
    var showUploadReelSheet by remember { mutableStateOf(false) }

    // Strictly filter to video reels only
    val allVideoReels = remember(allPosts) {
        allPosts.filter { it.mediaType == "video" }
    }

    val categories = remember(allVideoReels) {
        val dynamic = allVideoReels.mapNotNull { it.contentCategory.takeIf { cat -> cat.isNotBlank() } }.distinct()
        listOf("All", "Trending") + dynamic
    }

    val displayedReels = remember(allVideoReels, selectedCategory) {
        when (selectedCategory) {
            "All" -> allVideoReels
            "Trending" -> allVideoReels.sortedByDescending { it.likesCount * 2 + it.commentsCount }
            else -> allVideoReels.filter { it.contentCategory.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("reels_screen_root")
    ) {
        if (displayedReels.isEmpty()) {
            // Empty Reels State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShdMediaLogoBadge(size = 84.dp, cornerRadius = 22.dp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (selectedCategory == "All") "No Reels Uploaded Yet" else "No Reels in $selectedCategory",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Watch and share vertical short videos with everyone on SHD Media. Upload any video up to 5 minutes, 480p, and 60 FPS!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showUploadReelSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("upload_first_reel_button")
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload a Reel", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { displayedReels.size })

            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reels_vertical_pager"),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val reelPost = displayedReels[pageIndex]
                val isCurrentPage = pagerState.currentPage == pageIndex
                ReelPlayerPage(
                    post = reelPost,
                    isActivePage = isCurrentPage,
                    currentUserId = currentUserId,
                    repository = repository,
                    onCommentClick = { selectedReelForComments = reelPost },
                    onProfileClick = { onOpenProfile(reelPost.authorId) },
                    onDirectMessageClick = { recipientId, recipientUsername ->
                        onOpenDirectMessages(recipientId, recipientUsername)
                    }
                )
            }
        }

        // Top Overlay Header: SHD Media Logo + "Reels" + Category Pills + Upload Reel Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
                .padding(top = 12.dp, bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShdMediaLogoBadge(size = 34.dp, cornerRadius = 10.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Reels",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrismPink.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${displayedReels.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrismPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = { showUploadReelSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrismPink),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("upload_reel_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Upload Reel",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New Reel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (categories.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) PrismPink else Color.White.copy(alpha = 0.16f)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Upload Reel Modal Sheet (pre-selected to "video" mode)
        if (showUploadReelSheet) {
            CreatePostBottomSheet(
                repository = repository,
                initialUploadMode = "video",
                onDismiss = { showUploadReelSheet = false }
            )
        }

        // Comments Bottom Sheet
        selectedReelForComments?.let { reel ->
            CommentsBottomSheet(
                post = reel,
                repository = repository,
                currentUserId = currentUserId,
                onDirectMessageClick = { recipientId, recipientUsername ->
                    onOpenDirectMessages(recipientId, recipientUsername)
                },
                onDismiss = { selectedReelForComments = null }
            )
        }
    }
}

@Composable
private fun ReelPlayerPage(
    post: Post,
    isActivePage: Boolean,
    currentUserId: String,
    repository: PrismRepository,
    onCommentClick: () -> Unit,
    onProfileClick: () -> Unit,
    onDirectMessageClick: (recipientId: String, recipientUsername: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val likes by repository.observePostLikes(post.id).collectAsState(initial = emptyList())
    val comments by repository.observeComments(post.id).collectAsState(initial = emptyList())
    val authorProfile by repository.observeProfile(post.authorId).collectAsState(initial = null)
    val followers by repository.observeFollowers(post.authorId).collectAsState(initial = emptyList())

    val isLikedByMe = remember(likes, currentUserId) {
        likes.any { it.userId == currentUserId }
    }
    val isFollowingAuthor = remember(followers, currentUserId) {
        followers.any { it.followerId == currentUserId }
    }

    val displayUsername = authorProfile?.username?.ifBlank { post.authorUsername }
        ?: post.authorUsername.ifBlank { "creator" }
    val displayAvatar = authorProfile?.avatarUrl?.ifBlank { post.authorAvatarUrl }
        ?: post.authorAvatarUrl

    var playableVideoFile by remember(post.id) { mutableStateOf<File?>(null) }
    var isVideoLoading by remember(post.id) { mutableStateOf(false) }
    var isPausedByTap by remember(post.id) { mutableStateOf(false) }
    var videoLoadError by remember(post.id) { mutableStateOf<String?>(null) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    // Automatically fetch and assemble the video chunks from Firestore when this Reel becomes active
    LaunchedEffect(post.id, isActivePage) {
        if (isActivePage && playableVideoFile == null && !isVideoLoading) {
            isVideoLoading = true
            videoLoadError = null
            val file = repository.loadVideoToPlayableFile(context, post)
            isVideoLoading = false
            if (file != null) {
                playableVideoFile = file
            } else {
                videoLoadError = "Unable to load reel stream from Firestore."
            }
        }
    }

    // Pause or resume VideoView when swiping between pages or tapping
    LaunchedEffect(isActivePage, isPausedByTap, playableVideoFile) {
        val view = videoViewRef ?: return@LaunchedEffect
        if (isActivePage && !isPausedByTap && playableVideoFile != null) {
            if (!view.isPlaying) {
                view.start()
            }
        } else {
            if (view.isPlaying) {
                view.pause()
            }
        }
    }

    DisposableEffect(post.id) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
        }
    }

    val effectiveLikesCount = maxOf(likes.size.toLong(), post.likesCount)
    val effectiveCommentsCount = maxOf(comments.size.toLong(), post.commentsCount)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(post.id, isLikedByMe, effectiveLikesCount) {
                detectTapGestures(
                    onTap = {
                        if (playableVideoFile != null) {
                            isPausedByTap = !isPausedByTap
                        }
                    },
                    onDoubleTap = {
                        showDoubleTapHeart = true
                        coroutineScope.launch {
                            if (!isLikedByMe) {
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
                            delay(750)
                            showDoubleTapHeart = false
                        }
                    }
                )
            }
    ) {
        // 1. Thumbnail background while loading or if paused before prepare
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
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(PrismPurple, PrismMagenta, Color.Black))
                    )
            )
        }

        // 2. Full-screen Looping Video Player
        if (isActivePage && playableVideoFile != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this
                        setVideoPath(playableVideoFile!!.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            if (!isPausedByTap) {
                                start()
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            videoLoadError = "Video format could not be played on this device."
                            true
                        }
                    }
                },
                update = { view ->
                    videoViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Center Status Indicators (Loading / Paused / Double-Tap Heart)
        if (isVideoLoading) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = PrismPink, modifier = Modifier.size(34.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Loading Reel...",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (isPausedByTap) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { isPausedByTap = false },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume Reel",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.Center)) {
            AnimatedVisibility(
                visible = showDoubleTapHeart,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = LikeRed,
                    modifier = Modifier.size(110.dp)
                )
            }
        }

        // 4. Bottom Dark Gradient Scrim for Legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // 5. Right-Side Vertical Action Column (Like, Comment, DM, Share, Delete)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Like Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .testTag("reel_like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like Reel",
                        tint = if (isLikedByMe) LikeRed else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = effectiveLikesCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Comment Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .testTag("reel_comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = effectiveCommentsCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Message Creator Button
            if (post.authorId != currentUserId) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onDirectMessageClick(post.authorId, displayUsername) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .testTag("reel_dm_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Message @$displayUsername",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Chat",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Share Reel Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "Watch this Reel by @$displayUsername on SHD Media"
                            )
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Watch this Reel by @$displayUsername on SHD Media!\n\"${post.caption}\"\n${post.tags.joinToString(" ")}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Reel via"))
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .testTag("reel_share_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Reel",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Share",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Delete Button if own Reel
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
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Reel",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 6. Bottom-Left Creator & Reel Info Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.80f)
                .padding(start = 16.dp, end = 8.dp, bottom = 24.dp)
        ) {
            // Creator Row + Follow Button
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(2.dp, PrismPink, CircleShape)
                        .background(PrismPink.copy(alpha = 0.2f))
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarContent(
                        avatarUrl = displayAvatar,
                        username = displayUsername
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "@$displayUsername",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clickable { onProfileClick() }
                )

                if (post.authorId != currentUserId) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = if (isFollowingAuthor) Color.White.copy(alpha = 0.5f) else PrismPink,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                if (isFollowingAuthor) Color.White.copy(alpha = 0.12f)
                                else PrismPink.copy(alpha = 0.85f)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    try {
                                        val currentFollowersCount = maxOf(
                                            followers.size.toLong(),
                                            authorProfile?.followersCount ?: 0L
                                        )
                                        repository.toggleFollow(post.authorId, currentFollowersCount)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isFollowingAuthor) "Following" else "Follow",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Video Resolution / FPS / Duration Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val resText = if (post.videoWidth > 0 && post.videoHeight > 0) {
                    "${minOf(post.videoWidth, post.videoHeight)}p"
                } else "480p"
                val fpsText = if (post.videoFps > 0) "${post.videoFps} FPS" else "60 FPS"
                val mins = post.videoDurationSeconds / 60
                val secs = post.videoDurationSeconds % 60
                val durationFormatted = String.format("%d:%02d", mins, secs)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PrismPink.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "REEL • $resText • $fpsText • $durationFormatted",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (post.contentCategory.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = post.contentCategory,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Caption
            if (post.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Hashtags
            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = post.tags.joinToString(" "),
                    style = MaterialTheme.typography.labelMedium,
                    color = PrismPink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            videoLoadError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Audio / Original Reel Track Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Original Audio • @$displayUsername • SHD Media Reels",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
