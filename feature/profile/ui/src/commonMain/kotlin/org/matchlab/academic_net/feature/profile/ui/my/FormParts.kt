package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppBottomSheet
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppTextField
import org.matchlab.academic_net.components.SheetHeader
import org.matchlab.academic_net.components.UploadCard
import org.matchlab.academic_net.feature.profile.domain.ProofAttachment
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_cancel
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_attach
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_attached
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_error_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_remove
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_sheet_field
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_sheet_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_sheet_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_subtitle
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_title
import org.matchlab.academic_net.utils.isWebLink

/** A small red line under a field or a button, for problems the field component has no slot for. */
@Composable
internal fun FormErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * The bar with the main button at the bottom of a form: the surface under the button reaches the
 * edge of the screen, the button stays above the navigation bar. A failed sending is told above
 * the button ([errorText]).
 */
@Composable
internal fun FormBottomBar(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (errorText != null) {
            Text(text = errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        AppButton(text = text, onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled)
    }
}

/**
 * "Card/Upload" of the forms: "Загрузить подтверждение", or, once there is a [proof], the link
 * that was attached. A file picker is not available yet, so the proof is a link to the file; a
 * tap opens [ProofLinkSheet].
 */
@Composable
internal fun ProofUploadCard(
    proof: ProofAttachment?,
    errorText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        UploadCard(
            title = stringResource(if (proof == null) Res.string.profile_proof_title else Res.string.profile_proof_attached),
            subtitle = proof?.url ?: stringResource(Res.string.profile_proof_subtitle),
            onClick = onClick,
        )
        if (errorText != null) FormErrorText(errorText)
    }
}

/**
 * "Ссылка на файл": the sheet where the user pastes the address of the proof file. It holds the
 * text of the field itself and passes on only a valid link, so the form never gets a wrong one.
 *
 * @param current the link attached before, shown in the field; with it "Убрать" is offered
 * @param onAttach the link the user confirmed (trimmed, `http` or `https`)
 * @param onRemove the user removed the attached link
 * @param onDismiss swiping the sheet away or tapping outside it
 */
@Composable
internal fun ProofLinkSheet(
    current: ProofAttachment?,
    onAttach: (ProofAttachment) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf(current?.url.orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(title = stringResource(Res.string.profile_proof_sheet_title))
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTextField(
                value = link,
                onValueChange = {
                    link = it
                    showError = false
                },
                label = stringResource(Res.string.profile_proof_sheet_field),
                placeholder = stringResource(Res.string.profile_proof_hint),
                errorText = if (showError) stringResource(Res.string.profile_proof_error_link) else null,
            )
            Text(
                text = stringResource(Res.string.profile_proof_sheet_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppButton(
                text = stringResource(Res.string.profile_proof_attach),
                onClick = {
                    val url = link.trim()
                    if (url.isWebLink()) onAttach(ProofAttachment(url)) else showError = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (current != null) {
                AppButton(
                    text = stringResource(Res.string.profile_proof_remove),
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.Outlined,
                )
            } else {
                AppButton(
                    text = stringResource(Res.string.profile_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.Text,
                )
            }
        }
    }
}
