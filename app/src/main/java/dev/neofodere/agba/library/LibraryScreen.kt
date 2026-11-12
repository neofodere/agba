package dev.neofodere.agba.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.neofodere.agba.R
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.data.Game
import dev.neofodere.agba.data.ViewMode
import dev.neofodere.agba.ui.AgbaHeader
import dev.neofodere.agba.ui.ConsoleCover
import dev.neofodere.agba.ui.containerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlay: (Game) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRomFolders: () -> Unit,
    onPickCover: (Game) -> Unit,
    onShortcut: (Game) -> Unit,
    onCheats: (Game) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var actionGame by remember { mutableStateOf<Game?>(null) }
    var renameGame by remember { mutableStateOf<Game?>(null) }
    val visible = remember(viewModel.games, viewModel.platform, viewModel.query) {
        viewModel.games.filter {
            (viewModel.platform == null || it.platform == viewModel.platform) &&
                (viewModel.query.isBlank() || it.title.contains(viewModel.query, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            AgbaHeader(
                title = stringResource(R.string.app_name),
                titleContent = if (searching) {
                    {
                        TextField(
                            value = viewModel.query,
                            onValueChange = { viewModel.query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_games)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) viewModel.query = ""
                    }) {
                        Icon(
                            painter = painterResource(if (searching) R.drawable.ic_close else R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            painter = painterResource(
                                if (viewModel.viewMode == ViewMode.COVERS) R.drawable.ic_view_list else R.drawable.ic_view_grid,
                            ),
                            contentDescription = stringResource(R.string.switch_view),
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(painter = painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.rescan))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PlatformTabs(viewModel.platform) { viewModel.platform = it }
            if (viewModel.scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                !viewModel.hasDirectories -> EmptyState(
                    message = stringResource(R.string.library_setup_message),
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = onOpenRomFolders,
                )
                visible.isEmpty() && !viewModel.scanning -> EmptyState(
                    message = stringResource(R.string.no_games),
                    actionLabel = stringResource(R.string.rescan),
                    onAction = { viewModel.refresh() },
                )
                viewModel.viewMode == ViewMode.COVERS -> CoverGrid(visible, viewModel, onPlay) { actionGame = it }
                else -> GameList(visible, viewModel, onPlay) { actionGame = it }
            }
        }
    }

    actionGame?.let { game ->
        GameActionsDialog(
            game = game,
            hasCover = viewModel.hasCover(game),
            onDismiss = { actionGame = null },
            onPlay = { actionGame = null; onPlay(game) },
            onPickCover = { actionGame = null; onPickCover(game) },
            onRename = { actionGame = null; renameGame = game },
            onRemoveCover = { actionGame = null; viewModel.removeCover(game) },
            onShortcut = { actionGame = null; onShortcut(game) },
            onCheats = { actionGame = null; onCheats(game) },
        )
    }

    renameGame?.let { game ->
        RenameDialog(
            game = game,
            hasCustomTitle = viewModel.hasCustomTitle(game),
            onDismiss = { renameGame = null },
            onConfirm = { viewModel.rename(game, it); renameGame = null },
        )
    }
}

@Composable
private fun PlatformTabs(selected: Platform?, onSelect: (Platform?) -> Unit) {
    val tabs = listOf<Pair<String, Platform?>>(stringResource(R.string.all_platforms) to null) + Platform.entries.map { it.label to it }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            tabs.forEach { (label, platform) ->
                val active = selected == platform
                Column(
                    modifier = Modifier.clickable { onSelect(platform) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (platform != null) {
                            Image(
                                painter = painterResource(platform.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier.height(2.dp).width(40.dp)
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EmptyState(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(16.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun CoverGrid(
    games: List<Game>,
    viewModel: LibraryViewModel,
    onPlay: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(132.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(games, key = { it.id }) { game ->
            Column(
                Modifier.pointerInput(game.id) {
                    detectTapGestures(onTap = { onPlay(game) }, onLongPress = { onLongPress(game) })
                },
            ) {
                GameArt(game, viewModel, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)))
                Spacer(Modifier.size(6.dp))
                Text(
                    text = game.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = game.platform.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GameList(
    games: List<Game>,
    viewModel: LibraryViewModel,
    onPlay: (Game) -> Unit,
    onOptions: (Game) -> Unit,
) {
    val revision = viewModel.coverRevision
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(games, key = { it.id }) { game ->
            val hasCover = remember(revision, game.id) { viewModel.hasCover(game) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor())
                    .pointerInput(game.id) {
                        detectTapGestures(onTap = { onPlay(game) }, onLongPress = { onOptions(game) })
                    }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasCover) {
                    GameArt(game, viewModel, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                } else {
                    Image(
                        painter = painterResource(game.platform.iconRes),
                        contentDescription = game.platform.label,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        game.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        game.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = game.platform.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onOptions(game) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameArt(game: Game, viewModel: LibraryViewModel, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, game.id, viewModel.coverRevision) {
        value = withContext(Dispatchers.IO) { viewModel.loadCover(game, 400) }
    }
    val current = bitmap
    Box(modifier) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            ConsoleCover(game.platform)
        }
    }
}

@Composable
private fun GameActionsDialog(
    game: Game,
    hasCover: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPickCover: () -> Unit,
    onRename: () -> Unit,
    onRemoveCover: () -> Unit,
    onShortcut: () -> Unit,
    onCheats: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(game.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionRow(stringResource(R.string.play), onPlay)
                ActionRow(stringResource(R.string.rename), onRename)
                ActionRow(stringResource(R.string.set_cover), onPickCover)
                if (hasCover) ActionRow(stringResource(R.string.remove_cover), onRemoveCover)
                ActionRow(stringResource(R.string.add_to_home), onShortcut)
                ActionRow(stringResource(R.string.cheats), onCheats)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun RenameDialog(
    game: Game,
    hasCustomTitle: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(game.id) { mutableStateOf(game.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasCustomTitle) {
                    TextButton(onClick = { onConfirm("") }) { Text(stringResource(R.string.restore_name)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
