package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppReleaseInfo
import com.example.data.repository.PrismRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.CompleteProfileSetupScreen
import com.example.ui.direct.DirectMessagesScreen
import com.example.ui.feed.FeedScreen
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.reels.ReelsScreen
import com.example.ui.theme.PrismPink
import com.example.util.AppUpdateDialogWindow
import com.example.util.ShdAppUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FEED("Feed", Icons.Filled.Home, Icons.Outlined.Home),
    REELS("Reels", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    DIRECT("Messages", Icons.Filled.ChatBubble, Icons.Filled.ChatBubbleOutline),
    NOTIFICATIONS("Activity", Icons.Filled.Notifications, Icons.Filled.NotificationsNone),
    PROFILE("Profile", Icons.Filled.Person, Icons.Filled.PersonOutline)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val repository = remember { PrismRepository(context) }
    val currentUser by auth.authStateFlow().collectAsState(initial = auth.currentUser)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // AppUpdateManager state: check on app launch and listen in real time to Firestore app_releases/latest
    var latestReleaseInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    val observedCloudRelease by repository.observeLatestRelease().collectAsState(initial = null)

    LaunchedEffect(currentUser?.uid) {
        val release = try {
            repository.getOrSyncLatestRelease(context)
        } catch (_: Exception) {
            ShdAppUpdateManager.getDefaultReleaseInfo(context)
        }
        latestReleaseInfo = release
        if (ShdAppUpdateManager.shouldShowUpdateOnLaunch(context, release)) {
            showAppUpdateDialog = true
        }
    }

    LaunchedEffect(observedCloudRelease?.buildNumber) {
        val cloud = observedCloudRelease ?: return@LaunchedEffect
        val currentKnown = latestReleaseInfo?.buildNumber ?: 0L
        if (cloud.buildNumber > currentKnown) {
            latestReleaseInfo = cloud
            if (ShdAppUpdateManager.shouldShowUpdateOnLaunch(context, cloud)) {
                showAppUpdateDialog = true
            }
        }
    }

    if (showAppUpdateDialog && latestReleaseInfo != null) {
        AppUpdateDialogWindow(
            releaseInfo = latestReleaseInfo!!,
            onLater = {
                ShdAppUpdateManager.markBuildDismissed(context, latestReleaseInfo!!.buildNumber)
                showAppUpdateDialog = false
            },
            onUpdateCompleted = {
                showAppUpdateDialog = false
            },
            onPublishNewCloudRelease = { customUrl ->
                coroutineScope.launch {
                    try {
                        val published = repository.publishNewReleaseToCloud(context, customUrl)
                        latestReleaseInfo = published
                        showAppUpdateDialog = true
                    } catch (_: Exception) {
                    }
                }
            }
        )
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { /* authStateFlow emits automatically */ }
        )
    } else {
        val uid = currentUser!!.uid
        val userProfile by repository.observeProfile(uid).collectAsState(initial = null)
        var initialProfileChecked by remember(uid) { mutableStateOf(false) }
        var setupFinishedInSession by remember(uid) { mutableStateOf(false) }

        LaunchedEffect(uid) {
            try {
                val existing = repository.getProfile(uid)
                // If an older profile still had a Google mail avatar URL, strip it immediately
                if (existing != null && existing.avatarUrl.contains("googleusercontent.com", ignoreCase = true)) {
                    repository.createOrUpdateProfile(existing.copy(avatarUrl = ""))
                }
            } catch (_: Exception) {
            } finally {
                initialProfileChecked = true
            }
        }

        val currentProfile = userProfile
        val requiresProfileSetup = !setupFinishedInSession && (
            currentProfile == null ||
                !currentProfile.profileSetupCompleted ||
                currentProfile.username.isBlank() ||
                currentProfile.avatarUrl.isBlank() ||
                currentProfile.avatarUrl.contains("googleusercontent.com", ignoreCase = true)
            )

        if (!initialProfileChecked && currentProfile == null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrismPink, modifier = Modifier.size(36.dp))
                }
            }
        } else if (requiresProfileSetup) {
            CompleteProfileSetupScreen(
                userId = uid,
                repository = repository,
                onProfileCompleted = {
                    setupFinishedInSession = true
                }
            )
        } else {
            AuthenticatedMainScaffold(
                currentUserId = uid,
                repository = repository,
                onOpenAppUpdateDialog = {
                    if (latestReleaseInfo == null) {
                        latestReleaseInfo = ShdAppUpdateManager.getDefaultReleaseInfo(context)
                    }
                    showAppUpdateDialog = true
                },
                onSignOut = { /* handled by Firebase authStateFlow */ }
            )
        }
    }
}

@Composable
fun AuthenticatedMainScaffold(
    currentUserId: String,
    repository: PrismRepository,
    onOpenAppUpdateDialog: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.FEED) }
    var viewingProfileUserId by remember { mutableStateOf<String?>(null) }
    var directChatRecipientId by remember { mutableStateOf<String?>(null) }
    var directChatRecipientUsername by remember { mutableStateOf<String?>(null) }

    val notifications by repository.observeNotifications().collectAsState(initial = emptyList())
    val conversations by repository.observeConversations().collectAsState(initial = emptyList())
    val unreadNotificationsCount = remember(notifications) {
        notifications.count { !it.read }
    }
    val incomingMessagesCount = remember(conversations, currentUserId) {
        conversations.count { it.lastSenderId.isNotBlank() && it.lastSenderId != currentUserId && it.lastMessageText != "Started a new conversation" }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab && viewingProfileUserId == null
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            viewingProfileUserId = null
                            if (tab != MainTab.DIRECT) {
                                directChatRecipientId = null
                                directChatRecipientUsername = null
                            }
                            selectedTab = tab
                        },
                        icon = {
                            if (tab == MainTab.DIRECT && incomingMessagesCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = PrismPink) {
                                            Text(incomingMessagesCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            } else if (tab == MainTab.NOTIFICATIONS && unreadNotificationsCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = PrismPink) {
                                            Text(unreadNotificationsCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrismPink,
                            selectedTextColor = PrismPink,
                            indicatorColor = PrismPink.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (viewingProfileUserId != null) {
                ProfileScreen(
                    userId = viewingProfileUserId!!,
                    currentUserId = currentUserId,
                    repository = repository,
                    onSignOut = onSignOut,
                    onCheckForUpdate = onOpenAppUpdateDialog,
                    onMessageUser = { recipientId, recipientUsername ->
                        viewingProfileUserId = null
                        directChatRecipientId = recipientId
                        directChatRecipientUsername = recipientUsername
                        selectedTab = MainTab.DIRECT
                    },
                    onBack = { viewingProfileUserId = null }
                )
            } else {
                when (selectedTab) {
                    MainTab.FEED -> FeedScreen(
                        repository = repository,
                        currentUserId = currentUserId,
                        onOpenDirectMessages = { targetId, targetUsername ->
                            directChatRecipientId = targetId
                            directChatRecipientUsername = targetUsername
                            selectedTab = MainTab.DIRECT
                        },
                        onOpenProfile = { targetUid -> viewingProfileUserId = targetUid }
                    )
                    MainTab.REELS -> ReelsScreen(
                        repository = repository,
                        currentUserId = currentUserId,
                        onOpenDirectMessages = { targetId, targetUsername ->
                            directChatRecipientId = targetId
                            directChatRecipientUsername = targetUsername
                            selectedTab = MainTab.DIRECT
                        },
                        onOpenProfile = { targetUid -> viewingProfileUserId = targetUid }
                    )
                    MainTab.DIRECT -> DirectMessagesScreen(
                        repository = repository,
                        currentUserId = currentUserId,
                        initialRecipientId = directChatRecipientId,
                        initialRecipientUsername = directChatRecipientUsername,
                        onOpenProfile = { targetUid -> viewingProfileUserId = targetUid },
                        onBack = {
                            directChatRecipientId = null
                            directChatRecipientUsername = null
                            selectedTab = MainTab.FEED
                        }
                    )
                    MainTab.NOTIFICATIONS -> NotificationsScreen(
                        repository = repository,
                        currentUserId = currentUserId,
                        onNavigateToPost = { selectedTab = MainTab.FEED },
                        onNavigateToMessages = {
                            directChatRecipientId = null
                            directChatRecipientUsername = null
                            selectedTab = MainTab.DIRECT
                        }
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        userId = currentUserId,
                        currentUserId = currentUserId,
                        repository = repository,
                        onSignOut = onSignOut,
                        onCheckForUpdate = onOpenAppUpdateDialog
                    )
                }
            }
        }
    }
}
