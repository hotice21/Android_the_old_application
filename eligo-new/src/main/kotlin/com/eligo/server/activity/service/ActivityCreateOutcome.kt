package com.eligo.server.activity.service

import com.eligo.server.activity.vo.ManagedActivityDetailView

data class ActivityCreateOutcome(
    val view: ManagedActivityDetailView,
    val replayed: Boolean
)
