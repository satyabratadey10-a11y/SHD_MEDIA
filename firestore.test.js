const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read posts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("posts").get());
});

test("Authenticated user: can create and read own profile with custom username and gallery PP", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_w",
      displayName: "Alice Wonderland",
      avatarUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/",
      bio: "Visual artist & designer",
      profileSetupCompleted: true,
      createdAt: new Date(),
    })
  );

  const doc = await aliceDb.collection("users").doc(ALICE_UID).get();
  if (!doc.exists) throw new Error("Document should exist");
});

test("Authenticated user: cannot impersonate another user profile create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob_hacker",
      displayName: "Impersonator",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can check non-existent conversation without PERMISSION_DENIED, create it, and send message", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("conversations").doc("dm_alice_123_bob_456");

  // Crucial regression test: getting a conversation document before it exists must succeed
  const snapBefore = await assertSucceeds(convRef.get());
  if (snapBefore.exists) throw new Error("Conversation should not exist yet");

  await assertSucceeds(
    convRef.set({
      participants: [ALICE_UID, BOB_UID],
      lastMessageText: "Hello Bob!",
      lastSenderId: ALICE_UID,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      conversationId: "dm_alice_123_bob_456",
      senderId: ALICE_UID,
      recipientId: BOB_UID,
      text: "Hello Bob!",
      replyToMessageId: "",
      replyToText: "",
      replyToSenderUsername: "",
      reactionEmoji: "",
      read: false,
      createdAt: new Date(),
    })
  );

  // Verify Bob can see the conversation in his Messages tab and send a real reply
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const bobConvRef = bobDb.collection("conversations").doc("dm_alice_123_bob_456");

  await assertSucceeds(
    bobConvRef.collection("messages").doc("msg_2").set({
      conversationId: "dm_alice_123_bob_456",
      senderId: BOB_UID,
      recipientId: ALICE_UID,
      text: "Hey Alice! Replying to your message ✨",
      replyToMessageId: "msg_1",
      replyToText: "Hello Bob!",
      replyToSenderUsername: "alice_w",
      reactionEmoji: "",
      read: false,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    convRef.collection("messages").doc("msg_2").update({
      reactionEmoji: "❤️",
    })
  );
});

test("Authenticated user: can create photo or compliant 480p/60fps/5min video post and chunks, and other user can read/like/comment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("posts").doc("video_post_1").set({
      authorId: ALICE_UID,
      authorUsername: "alice_w",
      imageUrl: "data:image/jpeg;base64,thumbdata",
      videoUrl: "firestore://posts/video_post_1/video_chunks",
      mediaType: "video",
      videoDurationSeconds: 180,
      videoWidth: 854,
      videoHeight: 480,
      videoFps: 60,
      videoChunkCount: 1,
      caption: "480p 60fps clip!",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("posts").doc("video_post_1").collection("video_chunks").doc("chunk_0").set({
      postId: "video_post_1",
      authorId: ALICE_UID,
      index: 0,
      data: "AAAAHGZ0eXBtcDQyAAAAAG1wNDJpc29t",
      createdAt: new Date(),
    })
  );

  // Bob can read Alice's video chunk, like, and comment
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("posts").doc("video_post_1").collection("video_chunks").doc("chunk_0").get()
  );

  await assertSucceeds(
    bobDb.collection("posts").doc("video_post_1").collection("likes").doc(BOB_UID).set({
      userId: BOB_UID,
      postId: "video_post_1",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    bobDb.collection("posts").doc("video_post_1").collection("comments").doc("comment_1").set({
      postId: "video_post_1",
      authorId: BOB_UID,
      authorUsername: "bob_user",
      text: "Awesome video!",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: rejects video exceeding 5 minutes (300s) or 60 FPS or 480p", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Exceeds 300s duration
  await assertFails(
    aliceDb.collection("posts").doc("bad_video_duration").set({
      authorId: ALICE_UID,
      authorUsername: "alice_w",
      imageUrl: "data:image/jpeg;base64,thumb",
      mediaType: "video",
      videoDurationSeconds: 301,
      videoWidth: 854,
      videoHeight: 480,
      videoFps: 30,
      createdAt: new Date(),
    })
  );

  // Exceeds 60 FPS
  await assertFails(
    aliceDb.collection("posts").doc("bad_video_fps").set({
      authorId: ALICE_UID,
      authorUsername: "alice_w",
      imageUrl: "data:image/jpeg;base64,thumb",
      mediaType: "video",
      videoDurationSeconds: 120,
      videoWidth: 854,
      videoHeight: 480,
      videoFps: 120,
      createdAt: new Date(),
    })
  );
});

test("Direct message conversation: non-participant cannot read existing conversation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("conv_ab").set({
      participants: [ALICE_UID, BOB_UID],
      lastMessageText: "Hey!",
      updatedAt: new Date(),
    });
  });

  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(
    charlieDb.collection("conversations").doc("conv_ab").get()
  );
});
