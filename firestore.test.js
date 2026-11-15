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
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
}

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

function seedUser(uid, name, coins = 0) {
  return testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(uid).set({
      userId: uid,
      displayName: name,
      username: name.toLowerCase(),
      avatarUrl: "",
      bio: "Creator",
      isDiscoverable: true,
      coinsBalance: coins,
      withdrawnCoins: 0,
      totalLikesReceived: Math.floor(coins / 10),
      createdAt: new Date("2026-01-01T00:00:00Z"),
      updatedAt: new Date("2026-01-01T00:00:00Z"),
    });
  });
}

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").where("isDiscoverable", "==", true).get());
  await assertFails(unauthDb.collection("posts").where("isPublished", "==", true).get());
  await assertFails(unauthDb.collection("reels").where("isPublished", "==", true).get());
  await assertFails(unauthDb.collection("stories").where("isActive", "==", true).get());
  await assertFails(unauthDb.collection("chats").where("memberIds", "array-contains", ALICE_UID).get());
});

test("User profile: owner create & edit allowed, coin inflation & ghost fields denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice_vibes",
      avatarUrl: "",
      bio: "Hello",
      isDiscoverable: true,
      coinsBalance: 0,
      withdrawnCoins: 0,
      totalLikesReceived: 0,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      username: "bob_hack",
      isDiscoverable: true,
      coinsBalance: 9999,
      withdrawnCoins: 0,
      totalLikesReceived: 0,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Updated",
      isAdmin: true,
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").where("isDiscoverable", "==", true).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Posts & Reels: create, atomic like (+10 coins), and cross-user delete denial", async () => {
  await seedUser(ALICE_UID, "Alice", 0);
  await seedUser(BOB_UID, "Bob", 0);

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorAvatar: "",
      mediaUrl: "https://cdn.example.com/p1.jpg",
      mediaType: "image",
      caption: "First #trending post",
      hashtags: ["#trending", "#vibes"],
      trendingSticker: "🔥 Hot",
      likesCount: 0,
      commentsCount: 0,
      coinsAwarded: 0,
      isPublished: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertFails(bobDb.collection("posts").doc("post_1").delete());

  const batch = bobDb.batch();
  batch.set(bobDb.collection("posts").doc("post_1").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    targetId: "post_1",
    createdAt: tsNow(),
  });
  batch.update(bobDb.collection("posts").doc("post_1"), {
    likesCount: 1,
    coinsAwarded: 10,
    updatedAt: tsNow(),
  });
  batch.update(bobDb.collection("users").doc(ALICE_UID), {
    coinsBalance: 10,
    totalLikesReceived: 1,
    updatedAt: tsNow(),
  });
  await assertSucceeds(batch.commit());

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      coinsBalance: 1000,
      totalLikesReceived: 1,
      updatedAt: tsNow(),
    })
  );
});

test("UPI Withdrawals: owner atomic withdrawal succeeds, invalid UPI & cross-user read denied", async () => {
  await seedUser(ALICE_UID, "Alice", 500);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const batch = aliceDb.batch();
  batch.update(aliceDb.collection("users").doc(ALICE_UID), {
    withdrawnCoins: 200,
    updatedAt: tsNow(),
  });
  batch.set(aliceDb.collection("users").doc(ALICE_UID).collection("withdrawals").doc("wd_1"), {
    userId: ALICE_UID,
    upiId: "alice@okicici",
    coinsRedeemed: 200,
    amountInr: 20,
    status: "pending",
    referenceCode: "VR-WD-1001",
    createdAt: tsNow(),
  });
  await assertSucceeds(batch.commit());

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("withdrawals").get());

  const badBatch = aliceDb.batch();
  badBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    withdrawnCoins: 1000,
    updatedAt: tsNow(),
  });
  badBatch.set(aliceDb.collection("users").doc(ALICE_UID).collection("withdrawals").doc("wd_2"), {
    userId: ALICE_UID,
    upiId: "alice@okicici",
    coinsRedeemed: 1000,
    amountInr: 100,
    status: "pending",
    referenceCode: "VR-WD-1002",
    createdAt: tsNow(),
  });
  await assertFails(badBatch.commit());
});

test("Chats & Group Chats: member isolation enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_1").set({
      title: "Creators Squad",
      isGroup: true,
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessage: "Welcome!",
      lastSenderName: "Alice",
      createdBy: ALICE_UID,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertFails(charlieDb.collection("chats").doc("grp_1").get());
  await assertFails(
    charlieDb.collection("chats").doc("grp_1").collection("messages").doc("m1").set({
      chatId: "grp_1",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Intruder",
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      createdAt: tsNow(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_1").collection("messages").doc("m1").set({
      chatId: "grp_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hey team!",
      stickerText: "🚀 Boost",
      memberIds: [ALICE_UID, BOB_UID],
      createdAt: tsNow(),
    })
  );
});
