package org.matchlab.academic_net.feature.profile.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.matchlab.academic_net.common.model.UserId
import org.matchlab.academic_net.components.AppTopBar
import org.matchlab.academic_net.components.LoadingState
import org.matchlab.academic_net.components.MessageState
import org.matchlab.academic_net.feature.profile.ui.ProfileUiDependencies
import org.matchlab.academic_net.feature.profile.ui.WorkCard
import org.matchlab.academic_net.feature.profile.ui.generated.resources.Res
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_load_error
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_text
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_not_found_title
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_retry
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_works
import org.matchlab.academic_net.feature.profile.ui.generated.resources.profile_works_empty
import org.matchlab.academic_net.feature.profile.ui.rememberLinkOpener
import org.matchlab.academic_net.feature.profile.ui.toCardUiModel

/**
 * "09e Все работы": every work of a person, newest first; a work with a link opens it in the
 * browser. It serves the profiles of others and the viewer's own.
 */
@Composable
fun UserWorksRoute(
    dependencies: ProfileUiDependencies,
    userId: UserId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel(key = "user-works-${userId.value}") {
        UserWorksViewModel(userId, dependencies.getUserWorks)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserWorksScreen(state, onNavigateBack, rememberLinkOpener(), viewModel::onRetry, modifier)
}

@Composable
internal fun UserWorksScreen(
    state: UserWorksUiState,
    onNavigateBack: () -> Unit,
    onWorkLinkClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        AppTopBar(title = stringResource(Res.string.profile_works), onNavigateBack = onNavigateBack)
        when (state) {
            UserWorksUiState.Loading -> LoadingState()
            UserWorksUiState.NotFound -> MessageState(
                title = stringResource(Res.string.profile_not_found_title),
                text = stringResource(Res.string.profile_not_found_text),
            )
            UserWorksUiState.Error -> MessageState(
                title = stringResource(Res.string.profile_load_error),
                actionText = stringResource(Res.string.profile_retry),
                onAction = onRetry,
            )
            is UserWorksUiState.Content -> if (state.works.isEmpty()) {
                MessageState(title = stringResource(Res.string.profile_works_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.works, key = { it.id.value }) { work ->
                        WorkCard(
                            model = work.toCardUiModel(listStyle = true),
                            onClick = work.link?.let { link -> { onWorkLinkClick(link) } },
                        )
                    }
                }
            }
        }
    }
}
