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
import org.matchlab.academic_net.common.model.ProjectType
import org.matchlab.academic_net.components.AppCard
import org.matchlab.academic_net.components.AppIcon
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_mark_pending
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_mark_unverified
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_mark_verified

/**
 * Verification of a portfolio work or a document as the viewer sees it, shown as an icon at the
 * end of the card's title row.
 */
enum class VerificationMark {
    /** A moderator has confirmed it: the shield with a check. */
    Verified,

    /** It is being checked: a clock. Only the owner of the profile sees this state. */
    Pending,

    /** It is not confirmed (no proof, or the proof was rejected): the crossed shield. */
    Unverified,
}

/**
 * @param title name of the work
 * @param subtitle the second line: the description on a profile, the kind of work and where it
 *  lives ("Научная работа · GitHub") in the lists of works
 * @param type decides the icon: the scientific work is a document, the practical one a briefcase
 * @param mark `null` hides the verification icon
 */
@Immutable
data class WorkCardUiModel(
    val title: String,
    val subtitle: String,
    val type: ProjectType = ProjectType.Scientific,
    val mark: VerificationMark? = null,
)

/** "Card/Work": scientific or practical work in the portfolio with its verification mark. */
@Composable
fun WorkCard(
    model: WorkCardUiModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val icon = when (model.type) {
        ProjectType.Scientific -> AppIcons.FileText
        ProjectType.Practical -> AppIcons.Briefcase
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
                    if (model.mark != null) VerificationIcon(model.mark)
                }
                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Immutable
data class EducationCardUiModel(
    val title: String,
    val subtitle: String,
    val mark: VerificationMark? = null,
)

/** "Card/Education": education document or certificate with its verification mark. */
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
            if (model.mark != null) VerificationIcon(model.mark)
        }
    }
}

/** The icon of a [VerificationMark] with the text a screen reader says. */
@Composable
internal fun VerificationIcon(mark: VerificationMark, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    when (mark) {
        VerificationMark.Verified -> AppIcon(
            icon = AppIcons.ShieldCheck,
            contentDescription = stringResource(Res.string.profile_mark_verified),
            modifier = modifier,
            size = 18.dp,
            tint = colors.primary,
        )
        VerificationMark.Pending -> AppIcon(
            icon = AppIcons.Clock,
            contentDescription = stringResource(Res.string.profile_mark_pending),
            modifier = modifier,
            size = 18.dp,
            tint = colors.onSurfaceVariant,
        )
        VerificationMark.Unverified -> AppIcon(
            icon = AppIcons.ShieldX,
            contentDescription = stringResource(Res.string.profile_mark_unverified),
            modifier = modifier,
            size = 18.dp,
            tint = colors.error,
        )
    }
}
