package org.matchlab.academic_net.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_rejected
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_verified

/** Verification of a portfolio work or a document, shown as a shield icon. */
enum class VerificationStatus { Verified, Rejected }

/** Kind of a portfolio work; defines its icon. */
enum class WorkType { Research, Project }

@Immutable
data class WorkCardUiModel(
    val title: String,
    val description: String,
    val type: WorkType = WorkType.Research,
    val status: VerificationStatus? = null,
)

/** "Card/Work": research or project work in the portfolio with its verification status. */
@Composable
fun WorkCard(
    model: WorkCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val icon = when (model.type) {
        WorkType.Research -> AppIcons.FileText
        WorkType.Project -> AppIcons.Briefcase
    }
    AppCard(modifier, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            AppIcon(icon, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    if (model.status != null) VerificationIcon(model.status)
                }
                Text(model.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Immutable
data class EducationCardUiModel(
    val title: String,
    val subtitle: String,
    val status: VerificationStatus? = null,
)

/** "Card/Education": education document with its verification status. */
@Composable
fun EducationCard(
    model: EducationCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.GraduationCap, contentDescription = null, size = 20.dp, tint = colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (model.status != null) VerificationIcon(model.status)
        }
    }
}

@Composable
private fun VerificationIcon(status: VerificationStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        VerificationStatus.Verified -> AppIcon(
            icon = AppIcons.ShieldCheck,
            contentDescription = stringResource(Res.string.profile_verified),
            size = 18.dp,
            tint = colors.primary,
        )
        VerificationStatus.Rejected -> AppIcon(
            icon = AppIcons.ShieldX,
            contentDescription = stringResource(Res.string.profile_rejected),
            size = 18.dp,
            tint = colors.error,
        )
    }
}
