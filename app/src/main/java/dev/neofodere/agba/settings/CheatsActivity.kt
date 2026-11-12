package dev.neofodere.agba.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.neofodere.agba.BaseActivity
import dev.neofodere.agba.R
import androidx.compose.ui.res.stringResource
import dev.neofodere.agba.app
import dev.neofodere.agba.data.Cheat
import dev.neofodere.agba.ui.AgbaHeader
import dev.neofodere.agba.ui.AgbaTheme

class CheatsActivity : BaseActivity() {
    private lateinit var gameId: String
    private val cheats = mutableStateListOf<Cheat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_GAME)
        val game = id?.let(app.library::find)
        if (game == null) {
            finish()
            return
        }
        gameId = game.id
        cheats.addAll(app.cheats.load(gameId))
        setContent {
            AgbaTheme {
                CheatsScreen(
                    title = game.title,
                    cheats = cheats,
                    onChanged = { app.cheats.save(gameId, cheats.toList()) },
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_GAME = "dev.neofodere.agba.GAME"

        fun intentFor(context: Context, gameId: String): Intent =
            Intent(context, CheatsActivity::class.java).putExtra(EXTRA_GAME, gameId)
    }
}

@Composable
private fun CheatsScreen(
    title: String,
    cheats: MutableList<Cheat>,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = { AgbaHeader(title = stringResource(R.string.cheats_title, title), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = { editing = NEW_CHEAT },
                modifier = Modifier.padding(16.dp),
            ) {
                Text(stringResource(R.string.add_cheat))
            }
            if (cheats.isEmpty()) {
                Text(
                    stringResource(R.string.cheats_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyColumn {
                itemsIndexed(cheats) { index, cheat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = index }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cheat.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                cheat.code.lines().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Switch(
                            checked = cheat.enabled,
                            onCheckedChange = {
                                cheats[index] = cheat.copy(enabled = it)
                                onChanged()
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { index ->
        CheatDialog(
            initial = cheats.getOrNull(index),
            onDismiss = { editing = null },
            onDelete = {
                cheats.removeAt(index)
                onChanged()
                editing = null
            },
            onSave = { cheat ->
                if (index == NEW_CHEAT) cheats.add(cheat) else cheats[index] = cheat
                onChanged()
                editing = null
            },
        )
    }
}

@Composable
private fun CheatDialog(
    initial: Cheat?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Cheat) -> Unit,
) {
    val defaultName = stringResource(R.string.cheat_default_name)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_cheat else R.string.edit_cheat)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cheat_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.cheat_code)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank(),
                onClick = {
                    onSave(Cheat(name.ifBlank { defaultName }, code.trim(), initial?.enabled ?: true))
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private const val NEW_CHEAT = -1
