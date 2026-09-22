package com.eligo.server.account.service

import com.eligo.server.account.vo.DeactivationView

data class DeactivationRequestOutcome(val view: DeactivationView, val created: Boolean)
