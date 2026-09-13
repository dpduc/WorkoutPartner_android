package com.workoutpartner.data

import java.util.UUID

/**
 * The default `idGenerator` for every repository that mints its own
 * client-side ids ([SetRepository], [TallyRepository], [RosterRepository])
 * — pulled out once rather than each repository redeclaring the same
 * `{ UUID.randomUUID().toString() }` lambda. Injectable per-repository
 * (constructor default) so tests can substitute deterministic ids.
 */
internal fun newEntityId(): String = UUID.randomUUID().toString()
