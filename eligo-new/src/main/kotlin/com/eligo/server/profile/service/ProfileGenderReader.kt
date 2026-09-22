package com.eligo.server.profile.service

fun interface ProfileGenderReader {

    fun genderCode(userId: Long): Int?
}
