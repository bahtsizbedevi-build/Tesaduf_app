package com.tesaduf.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.R
import com.tesaduf.app.model.AdminReport
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.ButtonTone
import com.tesaduf.app.ui.design.TIcons
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufBadge
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufEmptyState
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufIconButton
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufSecondaryButton
import com.tesaduf.app.ui.design.TesadufSkeletonRow
import com.tesaduf.app.ui.design.TesadufTopBar
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatDayTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationUiState(
    val loading: Boolean = true,
    val reports: List<AdminReport> = emptyList(),
    val error: AppError? = null,
    val busyId: Long? = null,
)

class ModerationViewModel(private val repository: TesadufRepository) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState())
    val state: StateFlow<ModerationUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.adminReports()) {
                is Outcome.Success -> _state.update { it.copy(loading = false, reports = r.value) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun resolve(report: AdminReport, suspend: Boolean) {
        _state.update { it.copy(busyId = report.id) }
        viewModelScope.launch {
            when (val r = repository.adminResolve(report.id, suspend)) {
                is Outcome.Success -> refresh()
                is Outcome.Failure -> _state.update { it.copy(error = r.error) }
            }
            _state.update { it.copy(busyId = null) }
        }
    }
}

/** Moderation panel (only reachable for accounts listed in public.admins). */
@Composable
fun ModerationScreen(viewModel: ModerationViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TesadufTopBar(
                title = stringResource(R.string.mod_title),
                onBack = onBack,
                actions = { TesadufIconButton(TIcons.Refresh, stringResource(R.string.retry), viewModel::refresh, tint = TesadufColors.TextSecondary) },
            )
            state.error?.let { TesadufInlineMessage(it, Modifier.padding(horizontal = 20.dp), onRetry = viewModel::refresh) }
            when {
                state.loading && state.reports.isEmpty() -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { TesadufSkeletonRow() }
                }
                state.reports.isEmpty() -> TesadufEmptyState(
                    title = stringResource(R.string.mod_empty_title),
                    body = stringResource(R.string.mod_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.reports, key = { it.id }) { report ->
                        ReportCard(report, busy = state.busyId == report.id, onResolve = { viewModel.resolve(report, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: AdminReport, busy: Boolean, onResolve: (suspend: Boolean) -> Unit) {
    TesadufGlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TesadufAvatar(report.reportedAvatar, 44.dp, alive = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("#${report.reportedId}", style = IdTextStyle.copy(fontSize = 15.sp))
                    Text(formatDayTime(report.createdAt), style = MaterialTheme.typography.bodySmall)
                }
                TesadufBadge(reasonLabel(report.reason), TesadufColors.Warning)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.mod_meta, report.reporterId, report.timesReported),
                style = MaterialTheme.typography.bodySmall,
            )
            if (report.evidence.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(Shapes.field).background(TesadufColors.Night.copy(alpha = 0.5f)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    report.evidence.forEach { e ->
                        Text(
                            (if (e.fromReported) "#${report.reportedId}: " else "${stringResource(R.string.mod_reporter)}: ") + e.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.fromReported) TesadufColors.Pink else TesadufColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TesadufSecondaryButton(stringResource(R.string.mod_dismiss), { onResolve(false) }, Modifier.weight(1f), enabled = !busy)
                TesadufButton(stringResource(R.string.mod_suspend), { onResolve(true) }, Modifier.weight(1f), tone = ButtonTone.Danger, loading = busy)
            }
        }
    }
}

@Composable
private fun reasonLabel(reason: String): String = stringResource(
    when (reason) {
        "spam" -> R.string.report_spam
        "insult" -> R.string.report_insult
        "harassment" -> R.string.report_harassment
        "inappropriate" -> R.string.report_inappropriate
        else -> R.string.report_other
    },
)
