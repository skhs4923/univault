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

async function setupCollegeForUser(uid, collegeId, collegeName, accessCode) {
  const db = testEnv.authenticatedContext(uid).firestore();
  await db.collection("colleges").doc(collegeId).set({
    collegeId,
    collegeName,
    description: "Engineering Campus",
    accessCode,
    ownerId: uid,
    ownerName: uid === ALICE_UID ? "Alice" : "Bob",
    createdAt: new Date(),
    updatedAt: new Date(),
  });
  await db.collection("college_codes").doc(accessCode).set({
    collegeId,
    collegeName,
    description: "Engineering Campus",
    accessCode,
    ownerId: uid,
    ownerName: uid === ALICE_UID ? "Alice" : "Bob",
    createdAt: new Date(),
  });
  await db.collection("users").doc(uid).collection("unlocked_colleges").doc(collegeId).set({
    userId: uid,
    collegeId,
    collegeName,
    description: "Engineering Campus",
    accessCode,
    ownerId: uid,
    ownerName: uid === ALICE_UID ? "Alice" : "Bob",
    unlockedAt: new Date(),
  });
  return db;
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

test("Unauthenticated user: cannot read college_codes, colleges, or study_materials", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("college_codes").doc("MIT2026").get());
  await assertFails(unauthDb.collection("colleges").doc("col_mit").get());
  await assertFails(unauthDb.collection("colleges").doc("col_mit").collection("study_materials").get());
});

test("Authenticated user: cannot list all college_codes or colleges to enumerate secrets", async () => {
  await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("college_codes").get());
  await assertFails(bobDb.collection("colleges").get());
  await assertFails(bobDb.collection("colleges").doc("col_mit").get());
});

test("Locked college gate: Bob cannot read or upload files inside Alice's college before entering code", async () => {
  const aliceDb = await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("study_materials").doc("mat_1").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      userName: "Alice",
      title: "Algorithms Lecture 1",
      subject: "Computer Science",
      courseCode: "CS201",
      type: "LECTURE_NOTE",
      content: "Big O notation fundamentals",
      isShared: true,
      tags: ["algorithms", "cs201"],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").doc("mat_1").get()
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").where("isShared", "==", true).get()
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").doc("bob_mat").set({
      userId: BOB_UID,
      collegeId: "col_mit",
      userName: "Bob",
      title: "Unauthorized Upload",
      subject: "CS",
      courseCode: "CS101",
      type: "LECTURE_NOTE",
      isShared: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Wrong special code: Bob cannot forge an unlocked_colleges membership without exact code", async () => {
  await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).collection("unlocked_colleges").doc("col_mit").set({
      userId: BOB_UID,
      collegeId: "col_mit",
      collegeName: "MIT Campus",
      accessCode: "WRONGCODE",
      ownerId: ALICE_UID,
      unlockedAt: new Date(),
    })
  );
});

test("Valid special code unlocks college card and grants isolated read/download + chunk access", async () => {
  const aliceDb = await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");
  await setupCollegeForUser(ALICE_UID, "col_stan", "Stanford Campus", "STAN99");

  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("study_materials").doc("mat_mit").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      userName: "Alice",
      title: "MIT Signals Notes",
      subject: "EE",
      courseCode: "EE201",
      type: "LECTURE_NOTE",
      isShared: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("study_materials").doc("mat_mit")
      .collection("file_chunks").doc("chunk_0").set({
        chunkIndex: 0,
        data: "JVBERi0xLjQK",
      })
  );
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_stan").collection("study_materials").doc("mat_stan").set({
      userId: ALICE_UID,
      collegeId: "col_stan",
      userName: "Alice",
      title: "Stanford AI Paper",
      subject: "AI",
      courseCode: "CS229",
      type: "QUESTION_PAPER",
      isShared: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob looks up MIT2026 directly and unlocks col_mit ONLY
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const codeSnap = await assertSucceeds(bobDb.collection("college_codes").doc("MIT2026").get());
  const codeData = codeSnap.data();

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("unlocked_colleges").doc(codeData.collegeId).set({
      userId: BOB_UID,
      collegeId: codeData.collegeId,
      collegeName: codeData.collegeName,
      description: codeData.description,
      accessCode: "MIT2026",
      ownerId: codeData.ownerId,
      ownerName: codeData.ownerName,
      unlockedAt: new Date(),
    })
  );

  // Bob can list & download shared materials and chunks inside col_mit
  await assertSucceeds(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").where("isShared", "==", true).get()
  );
  await assertSucceeds(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").doc("mat_mit").collection("file_chunks").get()
  );

  // Bob CANNOT edit or delete Alice's material inside col_mit
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").doc("mat_mit").delete()
  );

  // Bob STILL cannot access Stanford (col_stan) materials because he has not unlocked STAN99
  await assertFails(
    bobDb.collection("colleges").doc("col_stan").collection("study_materials").where("isShared", "==", true).get()
  );
});

test("Shadow update & private isolation: Bob cannot inject ghost fields or read private materials", async () => {
  const aliceDb = await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");

  // Ghost field on create should be rejected by hasOnly()
  await assertFails(
    aliceDb.collection("colleges").doc("col_mit").collection("study_materials").doc("ghost_mat").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      title: "Notes",
      subject: "Math",
      courseCode: "M101",
      type: "LECTURE_NOTE",
      isShared: false,
      isAdminVerified: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Private material cannot be read by Bob even after unlocking col_mit
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("study_materials").doc("priv_mat").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      title: "Private Draft",
      subject: "Math",
      courseCode: "M101",
      type: "LECTURE_NOTE",
      isShared: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("unlocked_colleges").doc("col_mit").set({
      userId: BOB_UID,
      collegeId: "col_mit",
      collegeName: "MIT Campus",
      accessCode: "MIT2026",
      ownerId: ALICE_UID,
      unlockedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("study_materials").doc("priv_mat").get()
  );
});

test("College-scoped schedules and discussions work for unlocked members and isolate properly", async () => {
  const aliceDb = await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");

  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("schedules").doc("sched_1").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      title: "Midterm Exam",
      courseCode: "CS201",
      type: "EXAM",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("discussion_messages").doc("msg_1").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      userName: "Alice",
      channel: "CS201",
      text: "Hello MIT peers!",
      createdAt: new Date(),
    })
  );

  // Bob (not yet unlocked) cannot read discussions or Alice's schedule
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("discussion_messages").get()
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("schedules").doc("sched_1").get()
  );

  // After unlocking, Bob can read/write discussions, still cannot read Alice's personal schedule
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("unlocked_colleges").doc("col_mit").set({
      userId: BOB_UID,
      collegeId: "col_mit",
      collegeName: "MIT Campus",
      accessCode: "MIT2026",
      ownerId: ALICE_UID,
      unlockedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("colleges").doc("col_mit").collection("discussion_messages").get()
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("schedules").doc("sched_1").get()
  );
});

test("Flashcard decks: enforce owner isolation, schema validation, and college unlock", async () => {
  const aliceDb = await setupCollegeForUser(ALICE_UID, "col_mit", "MIT Campus", "MIT2026");

  // Unauthenticated cannot read flashcard_decks
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("colleges").doc("col_mit").collection("flashcard_decks").get()
  );

  // Alice can create a valid flashcard deck
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("flashcard_decks").doc("deck_1").set({
      userId: ALICE_UID,
      collegeId: "col_mit",
      title: "OS Concurrency Flashcards",
      subject: "Operating Systems",
      courseCode: "CS301",
      sourceNoteId: "mat_1",
      sourceNoteTitle: "Concurrency Notes",
      sourceExcerpt: "A semaphore is a synchronization variable.",
      cardsCount: 1,
      masteredCount: 0,
      cardsJson: '[{"id":"c1","front":"What is a semaphore?","back":"A synchronization primitive."}]',
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Alice can update masteredCount/cardsJson
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("flashcard_decks").doc("deck_1").update({
      masteredCount: 1,
      updatedAt: new Date(),
    })
  );

  // Shadow update with unknown field must fail
  await assertFails(
    aliceDb.collection("colleges").doc("col_mit").collection("flashcard_decks").doc("deck_1").update({
      isVerified: true,
      updatedAt: new Date(),
    })
  );

  // Bob cannot read Alice's flashcard deck even after unlocking the college
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("flashcard_decks").doc("deck_1").get()
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("unlocked_colleges").doc("col_mit").set({
      userId: BOB_UID,
      collegeId: "col_mit",
      collegeName: "MIT Campus",
      accessCode: "MIT2026",
      ownerId: ALICE_UID,
      unlockedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("flashcard_decks").doc("deck_1").get()
  );
  await assertFails(
    bobDb.collection("colleges").doc("col_mit").collection("flashcard_decks").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("colleges").doc("col_mit").collection("flashcard_decks").where("userId", "==", BOB_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("colleges").doc("col_mit").collection("flashcard_decks").where("userId", "==", ALICE_UID).get()
  );
});
