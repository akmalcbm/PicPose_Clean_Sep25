/**
 * ---
 * File: AuthModels.kt
 * Layer: Domain (Model)
 * Project: PicPose
 *
 * Purpose:
 * Transitional domain-model aliases used by auth domain contracts while data
 * DTOs are progressively decoupled from presentation.
 * ---
 */

package com.picpose.bestphotographyapp.domain.model

typealias AuthUser = com.picpose.bestphotographyapp.data.remote.dto.User
typealias AuthAccountType = com.picpose.bestphotographyapp.data.remote.dto.AccountType

