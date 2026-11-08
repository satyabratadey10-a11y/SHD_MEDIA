package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.data.model.AppReleaseInfo
import com.example.data.model.Comment
import com.example.data.model.Conversation
import com.example.data.model.DirectMessage
import com.example.data.model.FollowRecord
import com.example.data.model.Like
import com.example.data.model.NotificationItem
import com.example.data.model.Post
import com.example.data.model.UserProfile
import com.example.data.model.VideoChunk
import com.example.util.OperationType
import com.example.util.ShdAppUpdateManager
import com.example.util.VideoValidationSpec
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PrismRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- App Release / AppUpdateManager ---

    fun observeLatestRelease(): Flow<AppReleaseInfo?> {
        val docRef = db.collection("app_releases").document("latest")
        return docRef.snapshots()
            .map { it.toObject(AppReleaseInfo::class.java) }
            .catch { /* Ignore listener errors before sign-in */ }
    }

    suspend fun getOrSyncLatestRelease(context: Context): AppReleaseInfo {
        val defaultRelease = ShdAppUpdateManager.getDefaultReleaseInfo(context)
        val docRef = db.collection("app_releases").document("latest")
        return try {
            val snap = docRef.get().await()
            val existing = snap.toObject(AppReleaseInfo::class.java)
            if (existing == null || existing.buildNumber < defaultRelease.buildNumber) {
                val payload = mapOf(
                    "appName" to defaultRelease.appName,
                    "buildNumber" to defaultRelease.buildNumber,
                    "versionCode" to defaultRelease.versionCode,
                    "versionName" to defaultRelease.versionName,
                    "appSizeBytes" to defaultRelease.appSizeBytes,
                    "sha256" to defaultRelease.sha256,
                    "buildTimestampUtc" to defaultRelease.buildTimestampUtc,
                    "catalogue" to defaultRelease.catalogue,
                    "apkDownloadUrl" to (existing?.apkDownloadUrl?.ifBlank { null } ?: defaultRelease.apkDownloadUrl),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                defaultRelease
            } else {
                existing
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            defaultRelease
        }
    }

    suspend fun publishNewReleaseToCloud(context: Context, customApkUrl: String = ""): AppReleaseInfo {
        val current = getOrSyncLatestRelease(context)
        val nextBuild = maxOf(current.buildNumber + 1L, ShdAppUpdateManager.installedBuildNumber + 1L)
        val docRef = db.collection("app_releases").document("latest")
        val updated = current.copy(
            buildNumber = nextBuild,
            apkDownloadUrl = customApkUrl.ifBlank { current.apkDownloadUrl }
        )
        val payload = mapOf(
            "appName" to updated.appName,
            "buildNumber" to updated.buildNumber,
            "versionCode" to updated.versionCode,
            "versionName" to updated.versionName,
            "appSizeBytes" to updated.appSizeBytes,
            "sha256" to updated.sha256,
            "buildTimestampUtc" to updated.buildTimestampUtc,
            "catalogue" to updated.catalogue,
            "apkDownloadUrl" to updated.apkDownloadUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return updated
    }

    // --- User Profile (Custom Username & Gallery PP only — No Google Mail Username/PP) ---

    suspend fun ensureProfileExists(): UserProfile {
        val uid = requireUserId()
        val existing = getProfile(uid)
        if (existing != null) return existing

        // Never use Google mail account username or photoUrl
        val fallbackHandle = "user_${uid.take(6)}"
        val initialProfile = UserProfile(
            id = uid,
            userId = uid,
            username = fallbackHandle,
            displayName = fallbackHandle,
            avatarUrl = "",
            bio = "",
            profileSetupCompleted = false
        )
        createOrUpdateProfile(initialProfile)
        return getProfile(uid) ?: initialProfile
    }

    suspend fun getProfile(userId: String): UserProfile? {
        if (userId.isBlank()) return null
        val docRef = db.collection("users").document(userId)
        return try {
            docRef.get().await().toObject(UserProfile::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    fun observeProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { it.toObject(UserProfile::class.java) }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun createOrUpdateProfile(profile: UserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val cleanUsername = profile.username.trim().removePrefix("@").trim()
            .replace(" ", "_")
            .ifBlank { "user_${uid.take(6)}" }
            .let { if (it.length < 2) "${it}_1" else it.take(40) }
        val cleanDisplayName = profile.displayName.trim()
            .ifBlank { cleanUsername }
            .take(60)

        // Strip any Google mail account avatar URL so only Gallery Base64 or empty is stored
        val cleanAvatar = if (profile.avatarUrl.contains("googleusercontent.com")) {
            ""
        } else {
            profile.avatarUrl
        }

        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "userId" to uid,
                    "username" to cleanUsername,
                    "displayName" to cleanDisplayName,
                    "bio" to profile.bio,
                    "avatarUrl" to cleanAvatar,
                    "profileSetupCompleted" to profile.profileSetupCompleted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val data = mapOf(
                    "userId" to uid,
                    "username" to cleanUsername,
                    "displayName" to cleanDisplayName,
                    "avatarUrl" to cleanAvatar,
                    "bio" to profile.bio,
                    "profileSetupCompleted" to profile.profileSetupCompleted,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "postsCount" to 0L,
                    "likedCategories" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Follow / Unfollow (Real Multi-User Social Graph) ---

    fun observeFollowers(userId: String): Flow<List<FollowRecord>> {
        val path = "users/$userId/followers"
        return db.collection("users").document(userId).collection("followers")
            .snapshots()
            .map { it.toObjects(FollowRecord::class.java) }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun toggleFollow(targetUserId: String, currentFollowersCount: Long): Boolean {
        val uid = requireUserId()
        if (uid == targetUserId || targetUserId.isBlank()) return false
        val myProfile = ensureProfileExists()

        val followerDocRef = db.collection("users").document(targetUserId)
            .collection("followers").document(uid)
        val targetUserRef = db.collection("users").document(targetUserId)
        val myUserRef = db.collection("users").document(uid)

        return try {
            val existing = followerDocRef.get().await()
            if (existing.exists()) {
                followerDocRef.delete().await()
                val nextCount = maxOf(0L, currentFollowersCount - 1L)
                try {
                    targetUserRef.update(
                        "followersCount", nextCount,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
                } catch (_: Exception) {
                }
                try {
                    val myNextFollowing = maxOf(0L, myProfile.followingCount - 1L)
                    myUserRef.update(
                        "followingCount", myNextFollowing,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
                } catch (_: Exception) {
                }
                false
            } else {
                val payload = mapOf(
                    "followerId" to uid,
                    "targetUserId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                followerDocRef.set(payload).await()
                try {
                    targetUserRef.update(
                        "followersCount", currentFollowersCount + 1L,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
                } catch (_: Exception) {
                }
                try {
                    myUserRef.update(
                        "followingCount", myProfile.followingCount + 1L,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
                } catch (_: Exception) {
                }

                sendNotification(
                    recipientId = targetUserId,
                    type = "follow",
                    title = "New Follower",
                    body = "@${myProfile.username} started following you.",
                    targetId = uid
                )
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followerDocRef.path)
            throw e
        }
    }

    // --- Posts (Multi-User Global Feed & Video Uploads) ---

    fun observeFeedPosts(): Flow<List<Post>> {
        val path = "posts"
        return db.collection(path)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserPosts(userId: String): Flow<List<Post>> {
        val path = "posts"
        return db.collection(path)
            .whereEqualTo("authorId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createPost(
        imageUrl: String,
        videoUrl: String = "",
        mediaType: String = "image",
        videoDurationSeconds: Long = 0L,
        caption: String,
        filterName: String = "Normal",
        tags: List<String> = emptyList(),
        aiCaption: String = "",
        aiVibe: String = ""
    ): String {
        return createPostWithVisualMetadata(
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            mediaType = mediaType,
            videoDurationSeconds = videoDurationSeconds,
            caption = caption,
            filterName = filterName,
            tags = tags,
            contentCategory = tags.firstOrNull { !it.startsWith("#") } ?: "General",
            detectedObjects = emptyList(),
            aiCaption = aiCaption,
            aiVibe = aiVibe
        )
    }

    suspend fun createPostWithVisualMetadata(
        imageUrl: String,
        videoUrl: String = "",
        mediaType: String = "image",
        videoDurationSeconds: Long = 0L,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        videoFps: Int = 0,
        videoChunkCount: Int = 0,
        caption: String,
        filterName: String = "Normal",
        tags: List<String> = emptyList(),
        contentCategory: String = "General",
        detectedObjects: List<String> = emptyList(),
        aiCaption: String = "",
        aiVibe: String = ""
    ): String {
        val uid = requireUserId()
        val userProfile = ensureProfileExists()
        val username = userProfile.username.ifBlank { "user_${uid.take(6)}" }
        val displayName = userProfile.displayName.ifBlank { username }
        val avatar = userProfile.avatarUrl

        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("posts").document(postId)

        val payload = mapOf(
            "authorId" to uid,
            "authorUsername" to username,
            "authorDisplayName" to displayName,
            "authorAvatarUrl" to avatar,
            "imageUrl" to imageUrl,
            "videoUrl" to videoUrl,
            "mediaType" to mediaType,
            "videoDurationSeconds" to videoDurationSeconds,
            "videoWidth" to videoWidth,
            "videoHeight" to videoHeight,
            "videoFps" to videoFps,
            "videoChunkCount" to videoChunkCount,
            "caption" to caption,
            "filterName" to filterName,
            "tags" to tags.take(20),
            "contentCategory" to contentCategory.take(60),
            "detectedObjects" to detectedObjects.take(30),
            "likesCount" to 0L,
            "commentsCount" to 0L,
            "aiCaption" to aiCaption,
            "aiVibe" to aiVibe,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            postRef.set(payload).await()
            try {
                db.collection("users").document(uid)
                    .update(
                        "postsCount", userProfile.postsCount + 1L,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
            } catch (_: Exception) {
            }
            return postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            throw e
        }
    }

    suspend fun createVideoPost(
        videoSpec: VideoValidationSpec,
        caption: String,
        filterName: String = "Normal",
        tags: List<String> = emptyList(),
        contentCategory: String = "General",
        onProgress: (uploadedChunks: Int, totalChunks: Int) -> Unit = { _, _ -> }
    ): String {
        val uid = requireUserId()
        val userProfile = ensureProfileExists()
        val username = userProfile.username.ifBlank { "user_${uid.take(6)}" }
        val displayName = userProfile.displayName.ifBlank { username }
        val avatar = userProfile.avatarUrl

        require(videoSpec.durationSeconds <= 300L) {
            "Video exceeds 5 minutes maximum length."
        }
        require(minOf(videoSpec.width, videoSpec.height) <= 480 && maxOf(videoSpec.width, videoSpec.height) <= 864) {
            "Video exceeds 480p maximum resolution."
        }
        require(videoSpec.fps <= 60) {
            "Video exceeds 60 FPS maximum frame rate."
        }

        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("posts").document(postId)

        val fullBase64 = withContext(Dispatchers.Default) {
            Base64.encodeToString(videoSpec.rawBytes, Base64.NO_WRAP)
        }

        val chunkSize = 450_000
        val chunks = fullBase64.chunked(chunkSize)
        val totalChunks = chunks.size

        try {
            onProgress(0, totalChunks)

            val postPayload = mapOf(
                "authorId" to uid,
                "authorUsername" to username,
                "authorDisplayName" to displayName,
                "authorAvatarUrl" to avatar,
                "imageUrl" to videoSpec.thumbnailBase64,
                "videoUrl" to "firestore://posts/$postId/video_chunks",
                "mediaType" to "video",
                "videoDurationSeconds" to videoSpec.durationSeconds,
                "videoWidth" to videoSpec.width,
                "videoHeight" to videoSpec.height,
                "videoFps" to videoSpec.fps,
                "videoChunkCount" to totalChunks,
                "caption" to caption,
                "filterName" to filterName,
                "tags" to tags.take(20),
                "contentCategory" to contentCategory.take(60),
                "detectedObjects" to emptyList<String>(),
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "aiCaption" to "",
                "aiVibe" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            postRef.set(postPayload).await()

            val chunksCollection = postRef.collection("video_chunks")
            chunks.forEachIndexed { index, chunkData ->
                val chunkId = "chunk_$index"
                val chunkPayload = mapOf(
                    "postId" to postId,
                    "authorId" to uid,
                    "index" to index,
                    "data" to chunkData,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                chunksCollection.document(chunkId).set(chunkPayload).await()
                onProgress(index + 1, totalChunks)
            }

            try {
                db.collection("users").document(uid)
                    .update(
                        "postsCount", userProfile.postsCount + 1L,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
            } catch (_: Exception) {
            }

            return postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            throw e
        }
    }

    suspend fun loadVideoToPlayableFile(context: Context, post: Post): File? = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "shd_media_video_${post.id}.mp4")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return@withContext cacheFile
        }

        try {
            if (post.videoUrl.startsWith("data:video")) {
                val base64Part = post.videoUrl.substringAfter(",")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                cacheFile.writeBytes(bytes)
                return@withContext cacheFile
            }

            val chunksSnapshot = db.collection("posts")
                .document(post.id)
                .collection("video_chunks")
                .get()
                .await()

            val chunks = chunksSnapshot.toObjects(VideoChunk::class.java)
                .sortedBy { it.index }

            if (chunks.isEmpty()) return@withContext null

            val combinedBase64 = buildString {
                chunks.forEach { append(it.data) }
            }
            val videoBytes = Base64.decode(combinedBase64, Base64.DEFAULT)
            cacheFile.writeBytes(videoBytes)
            cacheFile
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "posts/${post.id}/video_chunks")
            null
        }
    }

    suspend fun deletePost(postId: String) {
        val uid = requireUserId()
        val postRef = db.collection("posts").document(postId)
        try {
            val postSnap = postRef.get().await()
            val post = postSnap.toObject(Post::class.java)
            if (post?.authorId == uid) {
                postRef.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            throw e
        }
    }

    // --- Likes & Algorithm Feedback ---

    fun observePostLikes(postId: String): Flow<List<Like>> {
        val path = "posts/$postId/likes"
        return db.collection("posts").document(postId).collection("likes")
            .snapshots()
            .map { it.toObjects(Like::class.java) }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun toggleLike(
        postId: String,
        postAuthorId: String,
        currentLikesCount: Long,
        postCategory: String = "General"
    ): Boolean {
        val uid = requireUserId()
        val myProfile = ensureProfileExists()
        val likeRef = db.collection("posts").document(postId).collection("likes").document(uid)
        val postRef = db.collection("posts").document(postId)

        return try {
            val snapshot = likeRef.get().await()
            if (snapshot.exists()) {
                likeRef.delete().await()
                val newCount = maxOf(0L, currentLikesCount - 1L)
                postRef.update(
                    "likesCount", newCount,
                    "updatedAt", FieldValue.serverTimestamp()
                ).await()
                try {
                    db.collection("users").document(uid)
                        .update("likedCategories", FieldValue.arrayRemove(postCategory))
                        .await()
                } catch (_: Exception) {
                }
                false
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "postId" to postId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                likeRef.set(payload).await()
                postRef.update(
                    "likesCount", currentLikesCount + 1L,
                    "updatedAt", FieldValue.serverTimestamp()
                ).await()

                if (postCategory.isNotBlank() && postCategory != "General") {
                    try {
                        db.collection("users").document(uid)
                            .update("likedCategories", FieldValue.arrayUnion(postCategory))
                            .await()
                    } catch (_: Exception) {
                    }
                }

                if (postAuthorId.isNotBlank() && postAuthorId != uid) {
                    sendNotification(
                        recipientId = postAuthorId,
                        type = "like",
                        title = "New Like",
                        body = "@${myProfile.username} liked your post.",
                        targetId = postId
                    )
                }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, likeRef.path)
            throw e
        }
    }

    // --- Comments ---

    fun observeComments(postId: String): Flow<List<Comment>> {
        val path = "posts/$postId/comments"
        return db.collection("posts").document(postId).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addComment(
        postId: String,
        postAuthorId: String,
        text: String,
        currentCommentsCount: Long
    ) {
        val uid = requireUserId()
        val myProfile = ensureProfileExists()
        val username = myProfile.username.ifBlank { "user_${uid.take(6)}" }
        val avatar = myProfile.avatarUrl

        val commentId = UUID.randomUUID().toString()
        val commentRef = db.collection("posts").document(postId).collection("comments").document(commentId)
        val postRef = db.collection("posts").document(postId)

        val payload = mapOf(
            "postId" to postId,
            "authorId" to uid,
            "authorUsername" to username,
            "authorAvatarUrl" to avatar,
            "text" to text.trim().take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            commentRef.set(payload).await()
            postRef.update(
                "commentsCount", currentCommentsCount + 1L,
                "updatedAt", FieldValue.serverTimestamp()
            ).await()

            if (postAuthorId.isNotBlank() && postAuthorId != uid) {
                sendNotification(
                    recipientId = postAuthorId,
                    type = "comment",
                    title = "New Comment",
                    body = "@$username commented: \"${text.take(40)}\"",
                    targetId = postId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    // --- Direct Messaging ---

    fun observeConversations(): Flow<List<Conversation>> {
        val uid = requireUserId()
        val path = "conversations"
        return db.collection(path)
            .whereArrayContains("participants", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMessages(conversationId: String): Flow<List<DirectMessage>> {
        val path = "conversations/$conversationId/messages"
        return db.collection("conversations").document(conversationId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun getOrCreateConversation(otherUserId: String): String {
        val uid = requireUserId()
        val cleanOtherId = otherUserId.trim()
        require(cleanOtherId.isNotBlank()) { "Recipient user ID cannot be blank." }
        require(cleanOtherId != uid) { "You cannot start a direct chat with yourself." }

        ensureProfileExists()
        val sortedUids = listOf(uid, cleanOtherId).sorted()
        val convId = "dm_${sortedUids[0]}_${sortedUids[1]}"
        val convRef = db.collection("conversations").document(convId)

        try {
            val snapshot = convRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "participants" to listOf(uid, cleanOtherId),
                    "lastMessageText" to "Started a new conversation",
                    "lastSenderId" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                convRef.set(payload).await()
            }
            return convId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, convRef.path)
            throw e
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        recipientId: String,
        text: String,
        imageUrl: String = "",
        replyToMessageId: String = "",
        replyToText: String = "",
        replyToSenderUsername: String = ""
    ) {
        val uid = requireUserId()
        val cleanRecipientId = recipientId.trim()
        require(cleanRecipientId.isNotBlank() && cleanRecipientId != uid) {
            "Invalid recipient ID for direct message."
        }
        val myProfile = ensureProfileExists()
        val convRef = db.collection("conversations").document(conversationId)
        val messageId = UUID.randomUUID().toString()
        val msgRef = convRef.collection("messages").document(messageId)

        val cleanText = text.trim().ifBlank { if (imageUrl.isNotBlank()) "📷 Sent a photo" else "" }.take(2000)

        val messageData = mapOf(
            "senderId" to uid,
            "recipientId" to cleanRecipientId,
            "conversationId" to conversationId,
            "text" to cleanText,
            "imageUrl" to imageUrl,
            "replyToMessageId" to replyToMessageId,
            "replyToText" to replyToText.take(300),
            "replyToSenderUsername" to replyToSenderUsername.take(40),
            "reactionEmoji" to "",
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            val convSnap = convRef.get().await()
            if (!convSnap.exists()) {
                convRef.set(
                    mapOf(
                        "participants" to listOf(uid, cleanRecipientId),
                        "lastMessageText" to cleanText.take(200),
                        "lastSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                convRef.update(
                    mapOf(
                        "lastMessageText" to cleanText.take(200),
                        "lastSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            msgRef.set(messageData).await()

            sendNotification(
                recipientId = cleanRecipientId,
                type = "message",
                title = "New Message from @${myProfile.username}",
                body = cleanText.take(60),
                targetId = conversationId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun toggleMessageReaction(
        conversationId: String,
        messageId: String,
        currentEmoji: String,
        targetEmoji: String = "❤️"
    ) {
        if (conversationId.isBlank() || messageId.isBlank()) return
        val msgRef = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)
        val nextEmoji = if (currentEmoji == targetEmoji) "" else targetEmoji
        try {
            msgRef.update("reactionEmoji", nextEmoji).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
        }
    }

    suspend fun markConversationMessagesRead(conversationId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (conversationId.isBlank()) return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            val snap = convRef.collection("messages")
                .whereEqualTo("recipientId", uid)
                .get()
                .await()
            snap.documents.forEach { doc ->
                if (doc.getBoolean("read") != true) {
                    doc.reference.update("read", true).await()
                }
            }
        } catch (_: Exception) {
        }
    }

    // --- Real-Time Notifications ---

    fun observeNotifications(): Flow<List<NotificationItem>> {
        val uid = requireUserId()
        val path = "users/$uid/notifications"
        return db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun sendNotification(
        recipientId: String,
        type: String,
        title: String,
        body: String,
        targetId: String
    ) {
        val uid = requireUserId()
        val myProfile = getProfile(uid)
        val username = myProfile?.username ?: "user_${uid.take(6)}"
        val avatar = myProfile?.avatarUrl ?: ""

        val notifId = UUID.randomUUID().toString()
        val notifRef = db.collection("users").document(recipientId).collection("notifications").document(notifId)

        val payload = mapOf(
            "recipientId" to recipientId,
            "senderId" to uid,
            "senderUsername" to username,
            "senderAvatarUrl" to avatar,
            "type" to type,
            "title" to title.take(100),
            "body" to body.take(300),
            "targetId" to targetId,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            notifRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, notifRef.path)
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        val uid = requireUserId()
        val notifRef = db.collection("users").document(uid).collection("notifications").document(notificationId)
        try {
            notifRef.update("read", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, notifRef.path)
        }
    }

    // --- Explore / Discover Users ---

    fun observeExploreUsers(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection(path)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java)
                    .map { profile ->
                        // Ensure no legacy Google mail photo URLs are displayed
                        if (profile.avatarUrl.contains("googleusercontent.com")) {
                            profile.copy(avatarUrl = "")
                        } else {
                            profile
                        }
                    }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun getUserByUsername(username: String): UserProfile? {
        val clean = username.trim().removePrefix("@").trim()
        if (clean.isBlank()) return null
        val path = "users"
        try {
            val querySnapshot = db.collection(path)
                .whereEqualTo("username", clean)
                .limit(5)
                .get()
                .await()

            val currentUid = auth.currentUser?.uid.orEmpty()
            if (!querySnapshot.isEmpty) {
                val matches = querySnapshot.toObjects(UserProfile::class.java)
                return matches.firstOrNull { it.effectiveUserId != currentUid } ?: matches.firstOrNull()
            }

            val allUsersSnapshot = db.collection(path)
                .limit(100)
                .get()
                .await()
            val allUsers = allUsersSnapshot.toObjects(UserProfile::class.java)
                .filter { it.username.equals(clean, ignoreCase = true) }
            return allUsers.firstOrNull { it.effectiveUserId != currentUid } ?: allUsers.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            return null
        }
    }

    suspend fun searchUsers(query: String): List<UserProfile> {
        val currentUid = auth.currentUser?.uid.orEmpty()
        val clean = query.trim().removePrefix("@").trim().lowercase()
        val path = "users"
        try {
            val snapshot = db.collection(path)
                .limit(100)
                .get()
                .await()
            val all = snapshot.toObjects(UserProfile::class.java)
            return all.filter { user ->
                user.effectiveUserId.isNotBlank() &&
                    user.effectiveUserId != currentUid && (
                        clean.isBlank() ||
                            user.username.lowercase().contains(clean) ||
                            user.displayName.lowercase().contains(clean)
                        )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            return emptyList()
        }
    }

    suspend fun sendDirectMessageByUsername(
        recipientUsername: String,
        text: String
    ): Result<Pair<String, UserProfile>> {
        val clean = recipientUsername.trim().removePrefix("@").trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be empty."))
        }
        val targetUser = getUserByUsername(clean)
            ?: return Result.failure(IllegalArgumentException("No registered user found with username @$clean."))

        val targetUid = targetUser.effectiveUserId
        if (targetUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Target user profile has an invalid user ID."))
        }

        val myUid = requireUserId()
        if (targetUid == myUid) {
            return Result.failure(IllegalArgumentException("You cannot send direct messages to yourself."))
        }

        return try {
            val convId = getOrCreateConversation(targetUid)
            sendMessage(conversationId = convId, recipientId = targetUid, text = text)
            Result.success(Pair(convId, targetUser))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
