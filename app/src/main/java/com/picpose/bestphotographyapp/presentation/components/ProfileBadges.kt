package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.UserRole

@Composable
fun AccountBadge(accountType: AccountType?) {
    val bgColor: Color
    val label: String

    when (accountType) {
        AccountType.PREMIUM -> {
            bgColor = MaterialTheme.colorScheme.tertiaryContainer
            label = "Premium"
        }
        AccountType.AD_FREE -> {
            bgColor = MaterialTheme.colorScheme.secondaryContainer
            label = "Ad-Free"
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            label = "Free"
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
            label = "Professional"
        }
        UserRole.ADMIN -> {
            bgColor = MaterialTheme.colorScheme.errorContainer
            label = "Admin"
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            label = "User"
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
