package com.tesaduf.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.R
import com.tesaduf.app.model.BlockedUser
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.BlockIllustration
import com.tesaduf.app.ui.design.ButtonTone
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufDialog
import com.tesaduf.app.ui.design.TesadufEmptyState
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufSecondaryButton
import com.tesaduf.app.ui.design.TesadufSkeletonRow
import com.tesaduf.app.ui.design.TesadufSnackbarHost
import com.tesaduf.app.ui.design.TesadufTextButton
import com.tesaduf.app.ui.design.TesadufTopBar
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatDayTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedUiState(
    val loading: Boolean = true,
    val users: List<BlockedUser> = emptyList(),
    val error: AppError? = null,
    val unblockedNotice: Boolean = false,
)

class BlockedUsersViewModel(private val repository: TesadufRepository) : ViewModel() {
    private val _state = MutableStateFlow(BlockedUiState())
    val state: StateFlow<BlockedUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.blockedUsers()) {
                is Outcome.Success -> _state.update { it.copy(loading = false, users = result.value) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun unblock(user: BlockedUser) {
        viewModelScope.launch {
            when (val result = repository.unblock(user.id)) {
                is Outcome.Success -> _state.update { s ->
                    s.copy(users = s.users.filterNot { it.id == user.id }, unblockedNotice = true)
                }
                is Outcome.Failure -> _state.update { it.copy(error = result.error) }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(unblockedNotice = false) }
    }
}

@Composable
fun BlockedUsersScreen(viewModel: BlockedUsersViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<BlockedUser?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notice = stringResource(R.string.unblocked_done)
    LaunchedEffect(state.unblockedNotice) {
        if (state.unblockedNotice) {
            viewModel.consumeNotice()
            snackbar.showSnackbar(notice)
        }
    }

    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TesadufTopBar(title = stringResource(R.string.blocked_users_title), onBack = onBack)
            state.error?.let { TesadufInlineMessage(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onRetry = viewModel::refresh) }
            when {
                state.loading && state.users.isEmpty() -> Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { repeat(3) { TesadufSkeletonRow() } }
                state.users.isEmpty() && state.error == null -> TesadufEmptyState(
                    title = stringResource(R.string.blocked_users_empty_title),
                    body = stringResource(R.string.blocked_users_empty_sub),
                    illustration = { BlockIllustration(Modifier.padding(8.dp).width(110.dp).fillMaxWidth()) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.users, key = { it.id }) { user ->
                        TesadufGlassCard(Modifier.fillMaxWidth().animateItem()) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TesadufAvatar(user.avatar, 44.dp, alive = false)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.displayId, style = IdTextStyle.copy(fontSize = 15.sp))
                                    Text(formatDayTime(user.createdAt), style = MaterialTheme.typography.bodySmall)
                                }
                                TesadufTextButton(stringResource(R.string.unblock), { pending = user }, color = TesadufColors.Cyan)
                            }
                        }
                    }
                }
            }
        }
        TesadufSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }

    pending?.let { user ->
        TesadufDialog(
            title = stringResource(R.string.unblock_title),
            body = stringResource(R.string.unblock_body, user.displayId),
            onDismiss = { pending = null },
        ) {
            TesadufButton(stringResource(R.string.unblock), { viewModel.unblock(user); pending = null }, Modifier.fillMaxWidth(), tone = ButtonTone.Cool)
            Spacer(Modifier.padding(5.dp))
            TesadufSecondaryButton(stringResource(R.string.cancel), { pending = null }, Modifier.fillMaxWidth())
        }
    }
}

