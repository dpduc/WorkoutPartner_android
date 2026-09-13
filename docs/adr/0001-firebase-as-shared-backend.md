# Firebase (Auth + Firestore) as the shared backend

Android and the separate Web project need one shared backend for Accounts and synced history — Firestore-only local storage won't do, since two independent client codebases must read/write the same data. We chose Firebase Auth + Firestore over Supabase or a custom backend because both clients can use the same SDK family with minimal server code, and both platforms' SDKs ship built-in offline persistence, which [[0002-offline-first-sync|the offline-first sync model]] depends on. The trade-off is lock-in to Firebase's data model and pricing; a later move to Postgres/custom auth would mean rewriting the data layer on both clients, not just one.

## Considered Options

- **Supabase** (Postgres + Auth): more portable data model (plain SQL), but no built-in client-side offline cache comparable to Firestore's — offline-first would need to be hand-rolled on both platforms.
- **Custom backend**: full control, but means writing and hosting a server for what's currently a solo-scoped project across two clients.
