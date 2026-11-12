package dev.neofodere.agba.emulator

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.neofodere.agba.R
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.ui.containerColor
import dev.neofodere.agba.ui.headerContainerColor
import dev.neofodere.agba.ui.headerContentColor
import java.text.DateFormat
import java.util.Date

enum class MenuScreen { NONE, MAIN, SAVE, LOAD }

class SlotInfo(val slot: Int, val modified: Long?, val thumbnail: ImageBitmap?)

class MenuActions(
    val onResume: () -> Unit,
    val onShowSlots: (MenuScreen) -> Unit,
    val onBackToMain: () -> Unit,
    val onSlot: (MenuScreen, Int) -> Unit,
    val onQuickSave: () -> Unit,
    val onQuickLoad: () -> Unit,
    val onReset: () -> Unit,
    val onToggleFastForward: () -> Unit,
    val onScreenshot: () -> Unit,
    val onCheats: () -> Unit,
    val onSettings: () -> Unit,
    val onEditControls: () -> Unit,
    val onQuit: () -> Unit,
)

@Composable
fun PauseMenu(
    screen: MenuScreen,
    title: String,
    platform: Platform,
    cover: ImageBitmap?,
    fastForwardLocked: Boolean,
    slots: List<SlotInfo>,
    actions: MenuActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onResume),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .heightIn(max = 600.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                GameHeader(title, platform, cover)
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (screen == MenuScreen.MAIN) {
                        MainItems(fastForwardLocked, actions)
                    } else {
                        SlotItems(screen, slots, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHeader(title: String, platform: Platform, cover: ImageBitmap?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerContainerColor())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Image(
                painter = painterResource(platform.iconRes),
                contentDescription = platform.label,
                modifier = Modifier.size(52.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = headerContentColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = platform.label,
                style = MaterialTheme.typography.bodySmall,
                color = headerContentColor().copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun MainItems(fastForwardLocked: Boolean, actions: MenuActions) {
    Spacer(Modifier.height(2.dp))
    MenuGroup {
        MenuItem(stringResource(R.string.resume), actions.onResume)
    }
    MenuGroup {
        MenuItem(stringResource(R.string.save_state)) { actions.onShowSlots(MenuScreen.SAVE) }
        MenuItem(stringResource(R.string.load_state)) { actions.onShowSlots(MenuScreen.LOAD) }
        MenuItem(stringResource(R.string.quick_save), actions.onQuickSave)
        MenuItem(stringResource(R.string.quick_load), actions.onQuickLoad)
    }
    MenuGroup {
        MenuItem(
            stringResource(if (fastForwardLocked) R.string.fast_forward_on else R.string.fast_forward_off),
            actions.onToggleFastForward,
        )
        MenuItem(stringResource(R.string.screenshot), actions.onScreenshot)
        MenuItem(stringResource(R.string.reset_game), actions.onReset)
    }
    MenuGroup {
        MenuItem(stringResource(R.string.cheats), actions.onCheats)
        MenuItem(stringResource(R.string.edit_controls), actions.onEditControls)
        MenuItem(stringResource(R.string.settings), actions.onSettings)
    }
    MenuGroup {
        MenuItem(stringResource(R.string.quit), actions.onQuit)
    }
}

@Composable
private fun SlotItems(screen: MenuScreen, slots: List<SlotInfo>, actions: MenuActions) {
    val saving = screen == MenuScreen.SAVE
    Text(
        text = stringResource(if (saving) R.string.save_state else R.string.load_state),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    MenuGroup {
        for (info in slots) {
            val enabled = saving || info.modified != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { actions.onSlot(screen, info.slot) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (info.thumbnail != null) {
                        Image(
                            bitmap = info.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column {
                    Text(stringResource(R.string.slot_n, info.slot), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = info.modified?.let { format.format(Date(it)) } ?: stringResource(R.string.slot_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    OutlinedButton(onClick = actions.onBackToMain) {
        Text(stringResource(R.string.back))
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
fun EditBar(
    selected: Control?,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onToggleVisible: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = selected?.let { stringResource(R.string.edit_selected, stringResource(it.title)) } ?: stringResource(R.string.edit_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSmaller, enabled = selected != null) { Text(stringResource(R.string.smaller)) }
                OutlinedButton(onClick = onLarger, enabled = selected != null) { Text(stringResource(R.string.larger)) }
                OutlinedButton(onClick = onToggleVisible, enabled = selected != null) { Text(stringResource(R.string.show_hide)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                Button(onClick = onDone) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

fun decodeThumbnail(path: String): ImageBitmap? =
    BitmapFactory.decodeFile(path)?.asImageBitmap()
