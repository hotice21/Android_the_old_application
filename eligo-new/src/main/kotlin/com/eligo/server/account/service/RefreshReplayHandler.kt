package com.eligo.server.account.service

interface RefreshReplayHandler {
    fun handleAuthenticatedReplay(candidate: RefreshReplayCandidate)
}
