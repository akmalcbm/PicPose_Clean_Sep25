/**
 * ---
 * File: ProfileBadges.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.UserRole

@Composable
fun AccountBadge(accountType: AccountType?) {
    val bgColor: Color
    val label: String

    when (accountType) {
        AccountType.PREMIUM -> {
            bgColor = MaterialTheme.colorScheme.tertiaryContainer
            label = stringResource(R.string.premium)
        }
        AccountType.AD_FREE -> {
            bgColor = MaterialTheme.colorScheme.secondaryContainer
            label = stringResource(R.string.ad_free)
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            label = stringResource(R.string.free)
        }
    }

    BadgeBox(label, bgColor)
}

@Composable
fun RoleBadge(role: UserRole?) {
    val bgColor: Color
    val label: String

    when (role) {
        UserRole.PROFESSIONAL -> {
            bgColor = MaterialTheme.colorScheme.primaryContainer
            label = stringResource(R.string.professional)
        }
        UserRole.ADMIN -> {
            bgColor = MaterialTheme.colorScheme.errorContainer
            label = stringResource(R.string.admin)
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            label = stringResource(R.string.user)
        }
    }

    BadgeBox(label, bgColor)
}

@Composable
private fun BadgeBox(label: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
