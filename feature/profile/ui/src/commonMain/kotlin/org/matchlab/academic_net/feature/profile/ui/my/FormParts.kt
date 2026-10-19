package org.matchlab.academic_net.feature.profile.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.components.AppButton
import org.matchlab.academic_net.components.AppButtonVariant
import org.matchlab.academic_net.components.AppIcons
import org.matchlab.academic_net.components.InfoBanner
import org.matchlab.academic_net.components.InfoBannerTone
import org.matchlab.academic_net.components.InfoCard
import org.matchlab.academic_net.components.StatusBanner
import org.matchlab.academic_net.feature.profile.domain.UploadFile
import org.matchlab.academic_net.feature.profile.domain.VerificationStatus
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_conflict
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_moderator_comment
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_open_link
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_attached
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_file_attach
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_file_chosen
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_file_hint
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_file_remove
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_proof_file_replace
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_submit
import org.matchlab.academic_net.feature.profile.ui.bannerTitle
import org.matchlab.academic_net.feature.profile.ui.toBannerStatus

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
 * edge of the screen, the button stays above the navigation bar. A failed action is told above
 * the button ([errorText]).
 *
 * @param enabled whether the button takes taps; while the action runs ([loading]) it does not
 * @param loading the action of the button is on its way: an arc turns in place of the label
 */
@Composable
internal fun FormBottomBar(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    errorText: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
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
        AppButton(
            text = text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            loading = loading,
        )
    }
}

/**
 * The banners at the top of the form of a record that is edited, one under another:
 *
 * - [isConflict]: the record was changed by someone else while the form was open, and the form shows the newer
 *   version ("Запись изменилась…"), an error banner;
 * - the state of the record ([StatusBanner]) when the server tells it. The comment of the moderator is part of that
 *   banner;
 * - API-GAP G12: when the state is not known (`null`, the Gateway never tells it) but the moderator left a comment, the
 *   comment is an error banner of its own ("Комментарий модератора: …"), whatever the state is.
 */
@Composable
internal fun PortfolioBanners(
    status: VerificationStatus?,
    moderationComment: String?,
    isConflict: Boolean,
) {
    if (isConflict) InfoBanner(stringResource(Res.string.profile_conflict), tone = InfoBannerTone.Error)
    val comment = moderationComment?.takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.profile_moderator_comment, it) }
    when {
        status != null -> StatusBanner(status.toBannerStatus(), title = status.bannerTitle(), text = comment)
        comment != null -> InfoBanner(comment, tone = InfoBannerTone.Error)
    }
}

/**
 * "Подтверждение добавлено": the record has a proof file on the server (it was attached elsewhere, the app cannot
 * upload files yet). The card is not a button, there is nothing to do with it; the file stays when the record is saved.
 */
@Composable
internal fun EvidenceAttachedCard(modifier: Modifier = Modifier) {
    InfoCard(text = stringResource(Res.string.profile_proof_attached), icon = AppIcons.Check, modifier = modifier)
}

/**
 * The actions under the form of a record that is edited, each as wide as the form and 8dp apart:
 *
 * - "Отправить на проверку" (outlined) when the record [canSend];
 * - "Открыть ссылку" (a text button) when there is a web link to open ([onOpenLink], a work only; API-GAP G56: the
 *   screen passes `null` for a link of another kind);
 * - the red text button that deletes the record ([deleteText]: "Удалить работу" / "Удалить документ"); the screen asks
 *   for the confirmation in [onDelete].
 *
 * @param enabled `false` while some action of the form runs: none of the buttons takes a tap then
 * @param isSending the sending is the action that runs: an arc turns in place of the label of the first button
 */
@Composable
internal fun PortfolioActions(
    canSend: Boolean,
    isSending: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    deleteText: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canSend) {
            AppButton(
                text = stringResource(Res.string.profile_submit),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Outlined,
                enabled = enabled,
                loading = isSending,
            )
        }
        if (onOpenLink != null) {
            AppButton(
                text = stringResource(Res.string.profile_open_link),
                onClick = onOpenLink,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Text,
                enabled = enabled,
            )
        }
        AppButton(
            text = deleteText,
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.TextError,
            enabled = enabled,
        )
    }
}

/** The extensions of the files that can prove a record: the storage takes documents and images (`docs/openapi.yaml`, `CreateMediaUpload`). */
internal val ProofFileExtensions = listOf("pdf", "jpg", "jpeg", "png")

/**
 * The picker of the proof file of a record: the gallery and the files of the device (FileKit). Returns the function that
 * opens it. A chosen file is read and handed to [onChosen]; one that cannot be read goes to [onFailed]. Remember it at the
 * root of the screen, not in a sheet (on iOS a launcher in a transient surface does not work).
 */
@Composable
internal fun rememberProofFilePicker(onChosen: (UploadFile) -> Unit, onFailed: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = ProofFileExtensions),
        onError = { onFailed() },
        onResult = { file ->
            if (file != null) {
                scope.launch {
                    try {
                        onChosen(proofFileOf(file))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        onFailed()
                    }
                }
            }
        },
    )
    return launcher::launch
}

/** The MIME type of a proof file by its extension (the storage checks the type it is given); anything else is a plain stream. */
internal fun proofContentType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    else -> "application/octet-stream"
}

/** The file [file] chosen on the device as a file to upload: its bytes, its name and the type its extension says. */
internal suspend fun proofFileOf(file: PlatformFile): UploadFile =
    UploadFile(file.readBytes(), contentType = proofContentType(file.name), fileName = file.name)

/**
 * The proof file of a record on its form: the file chosen now ([chosenName]), the file the record has on the server
 * ([hasExisting] and not [removed]), or the button that attaches one. The file is private: only the moderators see it,
 * unlike the link, which everyone who opens the profile sees.
 */
@Composable
internal fun ProofFileSection(
    chosenName: String?,
    hasExisting: Boolean,
    removed: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attached = chosenName != null || (hasExisting && !removed)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            chosenName != null -> InfoCard(
                text = stringResource(Res.string.profile_proof_file_chosen, chosenName),
                icon = AppIcons.Check,
            )
            attached -> EvidenceAttachedCard()
        }
        if (attached) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = stringResource(Res.string.profile_proof_file_replace),
                    onClick = onPick,
                    variant = AppButtonVariant.Text,
                    enabled = enabled,
                )
                AppButton(
                    text = stringResource(Res.string.profile_proof_file_remove),
                    onClick = onRemove,
                    variant = AppButtonVariant.TextError,
                    enabled = enabled,
                )
            }
        } else {
            AppButton(
                text = stringResource(Res.string.profile_proof_file_attach),
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.Outlined,
                enabled = enabled,
            )
        }
        Text(
            text = stringResource(Res.string.profile_proof_file_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
