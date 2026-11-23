@file:OptIn(ExperimentalMaterial3Api::class)

package com.purify.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurifyApp(vm: PurifyViewModel = viewModel()) {
    val reciter by vm.reciter.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val ui by vm.player.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var onlyDownloaded by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+ needs this for the playback notification and lock-screen controls.
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val visible = remember(query, onlyDownloaded, downloaded) {
        val q = normalize(query)
        vm.surahs.filter { s ->
            val matchesQuery = when {
                q.isEmpty() -> true
                q.all { it.isDigit() } -> s.number.toString() == q
                else -> s.searchKey.contains(q)
            }
            matchesQuery && (!onlyDownloaded || s.number in downloaded)
        }
    }
    val current = vm.surahs.getOrNull((ui.surah ?: 0) - 1)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Header(
                reciter = reciter,
                onReciter = vm::selectReciter,
                query = query,
                onQuery = { query = it },
                onlyDownloaded = onlyDownloaded,
                onOnlyDownloaded = { onlyDownloaded = it },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                if (current != null) {
                    MiniPlayer(
                        surah = current,
                        reciter = reciter,
                        ui = ui,
                        onToggle = vm::togglePlayPause,
                        onNext = vm::next,
                        onOpen = { showPlayer = true },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = if (onlyDownloaded) "Nothing downloaded yet for ${reciter.short}." else "No surahs found.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visible, key = { it.number }) { s ->
                SurahRow(
                    surah = s,
                    isCurrent = ui.surah == s.number,
                    isDownloaded = s.number in downloaded,
                    progress = progress[Downloads.key(reciter.id, s.number)],
                    onPlay = { vm.playSurah(s.number) },
                    onDownload = { vm.download(s.number) },
                    onCancel = { vm.cancelDownload(s.number) },
                    onDelete = { confirmDelete = s.number },
                )
            }
        }
    }

    if (showPlayer && current != null) {
        NowPlayingSheet(
            surah = current,
            reciter = reciter,
            ui = ui,
            vm = vm,
            onDismiss = { showPlayer = false },
        )
    }

    confirmDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove download?") },
            text = {
                Text(
                    "Surah ${vm.surahs[n - 1].english} (${reciter.short}) will be deleted from this " +
                        "device. You can download it again any time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDownload(n)
                    confirmDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Header(
    reciter: Reciter,
    onReciter: (Reciter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onlyDownloaded: Boolean,
    onOnlyDownloaded: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Purify",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier.weight(1f),
            )
            ReciterPicker(reciter, onReciter)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search by name or number") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !onlyDownloaded,
                onClick = { onOnlyDownloaded(false) },
                label = { Text("All 114") },
            )
            FilterChip(
                selected = onlyDownloaded,
                onClick = { onOnlyDownloaded(true) },
                label = { Text("Downloaded") },
            )
        }
    }
}

@Composable
private fun ReciterPicker(current: Reciter, onSelect: (Reciter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(current.short)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Reciters.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.name) },
                    onClick = {
                        open = false
                        onSelect(r)
                    },
                    trailingIcon = {
                        if (r == current) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                    },
                )
            }
        }
    }
}

@Composable
private fun SurahRow(
    surah: Surah,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    progress: Float?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) scheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isCurrent) Gold else scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = surah.number.toString(),
                color = if (isCurrent) scheme.onPrimary else scheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = surah.english,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Gold else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${surah.meaning} · ${surah.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = surah.arabic, fontSize = 22.sp, color = scheme.onSurface)
        when {
            progress != null -> IconButton(onClick = onCancel) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                    )
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Cancel download",
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            isDownloaded -> IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = "Downloaded. Tap to remove.",
                    tint = scheme.primary,
                )
            }
            else -> IconButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Download for offline")
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    surah: Surah,
    reciter: Reciter,
    ui: PlayerUi,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column {
            val fraction =
                if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = surah.english,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = reciter.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(text = surah.arabic, fontSize = 20.sp, color = Gold)
                IconButton(onClick = onToggle) {
                    if (ui.buffering) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (ui.isPlaying) "Pause" else "Play",
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next surah")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(
    surah: Surah,
    reciter: Reciter,
    ui: PlayerUi,
    vm: PurifyViewModel,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = surah.arabic, fontSize = 48.sp, color = Gold)
            Text(text = surah.english, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "${surah.meaning} · ${surah.subtitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = reciter.name,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.secondary,
            )
            Spacer(Modifier.height(24.dp))

            var dragging by remember { mutableStateOf<Float?>(null) }
            val duration = ui.durationMs.coerceAtLeast(1L).toFloat()
            Slider(
                value = (dragging ?: ui.positionMs.toFloat()).coerceIn(0f, duration),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..duration,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatTime(dragging?.toLong() ?: ui.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    formatTime(ui.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        imageVector = if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (ui.repeat) {
                            Player.REPEAT_MODE_ONE -> "Repeat this surah"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (ui.repeat == Player.REPEAT_MODE_OFF) scheme.onSurfaceVariant else Gold,
                    )
                }
                IconButton(onClick = vm::previous) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous surah",
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledIconButton(onClick = vm::togglePlayPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ui.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = vm::next) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next surah",
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
