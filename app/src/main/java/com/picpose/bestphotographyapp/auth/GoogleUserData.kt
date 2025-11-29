package com.picpose.bestphotographyapp.auth

data class GoogleUserData(
    val displayName: String?,
    val email: String?,
    val profilePictureUrl: String?,
    val idToken: String?
)
