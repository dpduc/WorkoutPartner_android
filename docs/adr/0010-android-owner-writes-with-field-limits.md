# Owners may write sessions, profiles and account settings; Web's read-only role is a scope rule

`firestore.rules` makes `accounts`, `sessions` and `trackedProfiles` read-only for everyone, because [[0006-camera-tracking-lives-in-android-only|ADR-0006]] says Web only reads and Firestore has no way to tell an Android session from a Web session signed in as the same uid. Multi-device sync ([[0002-offline-first-sync]]) needs Android to write those collections, so read-only cannot stay. We allow the authenticated owner to write them, limited by field, and accept that **ADR-0006 is then enforced by what the Web project is built to do, not by the rules.**

## What the rules will allow

- `sessions` and `trackedProfiles`: owner create and update. Roster entries are never deleted; a `isDeleted` tombstone syncs the removal to other devices.
- `accounts`: owner update limited to `name`, `age`, `heightCm`, `weightKg`, `activityLevel`, `weeklyTarget`, `notificationsEnabled` and `updatedAt`.
- `currentStreak` and `bankedShields`: writable by the owner **only until** the Cloud Function from [[0009-streak-computed-on-device-cloud-authority-later]] ships, then rejected by a rule (`!affectedKeys().hasAny(['currentStreak','bankedShields'])`).
- `sets` and `tallies`: unchanged — create-only, immutable, including `exerciseVariant`. Nothing here weakens the records that matter most.
- Ownerless Guest rows are never pushed ([[0007-guest-feature-parity-stays-local]]); every write must carry an `accountId` equal to `request.auth.uid`.

## Why this exposure is acceptable

A Web session for an Account holder could, with the same uid, edit that Account's own Weekly Target or body-stats. That affects only their own settings. The things that could be gamed — the record of Sets and the Streak — are protected separately: Sets are immutable, and Streak is locked once it has an authority. Where a platform-distinguishing check later becomes available, this ADR can be tightened without changing the data model.

## Consequences

The rules change ships **together with** the push path for these collections (the roadmap's Phase 2), never before it; until then the current read-only rules stand. `firestore-schema.md` and the comment block in `firestore.rules` that cite ADR-0006 as the reason for read-only are updated in the same change. `SetEntity.toFirestoreMap()` and `TallyEntity.toFirestoreMap()` must also start sending `exerciseVariant` in that work, or Step Jack Sets reach the cloud as plain Jumping Jacks.
