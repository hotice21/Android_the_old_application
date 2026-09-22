package com.eligo.server.activity.entity

import java.time.LocalDateTime

class ActivityCreateIdempotencyTombstoneEntity {
    var createIdempotencyScope: String? = null
    var createIdempotencyKey: String? = null
    var createIdempotencyFingerprint: String? = null
    var activityId: Long? = null
    var deletedAt: LocalDateTime? = null
    var expiresAt: LocalDateTime? = null
}
