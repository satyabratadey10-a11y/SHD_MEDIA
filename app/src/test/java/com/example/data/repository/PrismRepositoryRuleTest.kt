package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrismRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun testUserProfile_createAndRead_succeeds() = runBlocking {
        val uid = signInTestUser("tester@prismgram.com")
        val repository = PrismRepository(firestore)

        val profile = UserProfile(
            userId = uid,
            username = "tester_1",
            displayName = "Tester One",
            avatarUrl = "data:image/jpeg;base64,customgallerypp",
            bio = "Hello SHD Media",
            profileSetupCompleted = true
        )

        withTimeout(5000L) {
            repository.createOrUpdateProfile(profile)
        }

        val fetched = withTimeout(5000L) {
            repository.getProfile(uid)
        }

        assertNotNull(fetched)
        assertEquals("tester_1", fetched?.username)
        assertEquals("Tester One", fetched?.displayName)
        assertTrue(fetched?.profileSetupCompleted == true)
    }

    @Test
    fun testDirectConversation_getOrCreateAndSendByUsername_succeedsWithoutPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_dm@prismgram.com")
        val aliceRepo = PrismRepository(firestore)
        withTimeout(5000L) {
            aliceRepo.createOrUpdateProfile(
                UserProfile(
                    userId = aliceUid,
                    username = "alice_dm",
                    displayName = "Alice DM",
                    avatarUrl = "data:image/jpeg;base64,alicepp",
                    profileSetupCompleted = true
                )
            )
        }

        val bobUid = signInTestUser("bob_dm@prismgram.com")
        val bobRepo = PrismRepository(firestore)
        withTimeout(5000L) {
            bobRepo.createOrUpdateProfile(
                UserProfile(
                    userId = bobUid,
                    username = "satyabratadey10",
                    displayName = "Satyabrata Dey",
                    avatarUrl = "data:image/jpeg;base64,bobpp",
                    profileSetupCompleted = true
                )
            )
        }

        // Switch back to Alice and start a direct message with @satyabratadey10
        signInTestUser("alice_dm@prismgram.com")
        val sendResult = withTimeout(5000L) {
            aliceRepo.sendDirectMessageByUsername(
                recipientUsername = "@satyabratadey10",
                text = "hj"
            )
        }

        assertTrue("Expected sendDirectMessageByUsername to succeed, got: ${sendResult.exceptionOrNull()}", sendResult.isSuccess)
        val (convId, targetUser) = sendResult.getOrThrow()
        assertTrue(convId.startsWith("dm_"))
        assertEquals("satyabratadey10", targetUser.username)

        // Switch to Bob (@satyabratadey10) and verify he sees Alice's message in his Messages tab and can reply
        signInTestUser("bob_dm@prismgram.com")
        val bobConversations = withTimeout(5000L) {
            bobRepo.observeConversations().first { list -> list.any { it.id == convId } }
        }
        val bobConv = bobConversations.first { it.id == convId }
        assertEquals("hj", bobConv.lastMessageText)
        assertEquals(aliceUid, bobConv.lastSenderId)

        withTimeout(5000L) {
            bobRepo.sendMessage(
                conversationId = convId,
                recipientId = aliceUid,
                text = "Hey! Great to hear from you on SHD Media! ✨",
                replyToText = "hj",
                replyToSenderUsername = "alice_dm"
            )
        }

        // Switch back to Alice and verify she sees Bob's real reply in the conversation
        signInTestUser("alice_dm@prismgram.com")
        val allMessages = withTimeout(5000L) {
            aliceRepo.observeMessages(convId).first { it.size >= 2 }
        }
        assertEquals(2, allMessages.size)
        assertEquals(bobUid, allMessages.last().senderId)
        assertEquals("Hey! Great to hear from you on SHD Media! ✨", allMessages.last().text)
    }

    @Test
    fun testCreatePostAndMultiUserLikeComment_succeeds() = runBlocking {
        val aliceUid = signInTestUser("creator@prismgram.com")
        val aliceRepo = PrismRepository(firestore)

        withTimeout(5000L) {
            aliceRepo.createOrUpdateProfile(
                UserProfile(userId = aliceUid, username = "creator_user", displayName = "Creator", profileSetupCompleted = true)
            )
        }

        val postId = withTimeout(5000L) {
            aliceRepo.createPost(
                imageUrl = "data:image/jpeg;base64,samplephotodata",
                caption = "Sunset in Tokyo",
                filterName = "Sunset",
                tags = listOf("#sunset", "#tokyo")
            )
        }

        assertNotNull(postId)
        assertTrue(postId.isNotBlank())

        // Second user (Bob) signs in, sees Alice's post, likes it, and comments
        val bobUid = signInTestUser("viewer@prismgram.com")
        val bobRepo = PrismRepository(firestore)
        withTimeout(5000L) {
            bobRepo.createOrUpdateProfile(
                UserProfile(userId = bobUid, username = "bob_viewer", displayName = "Bob", profileSetupCompleted = true)
            )
        }

        val liked = withTimeout(5000L) {
            bobRepo.toggleLike(postId, aliceUid, 0L)
        }
        assertTrue(liked)

        withTimeout(5000L) {
            bobRepo.addComment(postId, aliceUid, "Incredible shot!", 0L)
        }

        val comments = withTimeout(5000L) {
            bobRepo.observeComments(postId).first { it.isNotEmpty() }
        }
        assertEquals("Incredible shot!", comments.first().text)
    }

    @Test
    fun testObserveFeedPosts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PrismRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(5000L) {
                repository.observeFeedPosts().first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
}
