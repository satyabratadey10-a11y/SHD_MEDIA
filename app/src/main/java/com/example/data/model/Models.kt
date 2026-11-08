package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val profileSetupCompleted: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val postsCount: Long = 0L,
    val likedCategories: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val effectiveUserId: String
        get() = userId.ifBlank { id }
}

@IgnoreExtraProperties
data class AppReleaseInfo(
    @DocumentId val id: String = "",
    val appName: String = "SHD Media",
    val buildNumber: Long = 2026110502L,
    val versionCode: Int = 2,
    val versionName: String = "2.2.0",
    val appSizeBytes: Long = 28_144_016L,
    val sha256: String = "",
    val buildTimestampUtc: String = "",
    val catalogue: List<String> = emptyList(),
    val apkDownloadUrl: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarUrl: String = "",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val mediaType: String = "image", // "image" or "video"
    val videoDurationSeconds: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoFps: Int = 0,
    val videoChunkCount: Int = 0,
    val caption: String = "",
    val filterName: String = "Normal",
    val tags: List<String> = emptyList(),
    val contentCategory: String = "General",
    val detectedObjects: List<String> = emptyList(),
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val aiCaption: String = "",
    val aiVibe: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoChunk(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val index: Int = 0,
    val data: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Like(
    @DocumentId val id: String = "",
    val userId: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowRecord(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val targetUserId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val conversationId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val replyToSenderUsername: String = "",
    val reactionEmoji: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderAvatarUrl: String = "",
    val type: String = "like", // like, comment, message, follow
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
