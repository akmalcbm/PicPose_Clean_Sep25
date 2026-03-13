/**
 * ---
 * File: AuthRepository.kt
 * Layer: Domain (Repository Contract)
 * Project: PicPose
 *
 * Purpose:
 * Domain contract for authentication and account-management operations.
 * Presentation depends on this interface while the data layer provides the
 * concrete implementation.
 *
 * Data Flow:
 * UI -> AuthViewModel -> AuthRepository (this contract) -> AuthRepositoryImpl -> API/DataStore
 * ---
 */

package com.picpose.bestphotographyapp.domain.repository

import android.net.Uri
import com.picpose.bestphotographyapp.data.remote.dto.AccountType
import com.picpose.bestphotographyapp.data.remote.dto.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String, name: String): Result<User>
    suspend fun socialLogin(
        provider: String,
        token: String,
        email: String? = null,
        name: String? = null,
        profilePicture: String? = null
    ): Result<User>

    suspend fun logout()
    suspend fun getUserProfile(userId: String): Result<User>
    suspend fun updateProfile(
        name: String,
        bio: String?,
        profilePictureUri: Uri?,
        accountType: AccountType
    ): Result<User>

    suspend fun deleteAccount(reason: String = "user_requested_in_app"): Result<Unit>
    suspend fun requestPasswordReset(email: String): Result<String>
    suspend fun resetPassword(token: String, newPassword: String): Result<String>
    suspend fun requestEmailVerification(userId: String): Result<String>
    suspend fun verifyEmailToken(token: String): Result<String>
}

