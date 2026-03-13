/**
 * ---
 * File: LoginUseCase.kt
 * Layer: Domain (UseCase)
 * Project: PicPose
 *
 * Purpose:
 * Focused auth use case that wraps login behavior behind a domain boundary.
 * This is intentionally small and can be expanded as the domain layer grows.
 * ---
 */

package com.picpose.bestphotographyapp.domain.usecase.auth

import com.picpose.bestphotographyapp.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String) =
        repository.login(email = email, password = password)
}

