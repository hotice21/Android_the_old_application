package com.eligo.server.recommendation

class RecommendationDependencyException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
