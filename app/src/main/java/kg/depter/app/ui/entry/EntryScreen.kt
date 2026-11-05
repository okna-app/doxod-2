@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.entry

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.data.EntryKind
import kg.depter.app.domain.Calc
import kg.depter.app.domain.Money
import kg.depter.app.ui.Dates
import kg.depter.app.ui.currentLocale
import kg.depter.app.ui.dayTitle
import kg.depter.app.ui.rememberMoney
import kg.depter.app.ui.theme.Depter
import kg.depter.app.ui.theme.NumberStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun EntryScreen(partyId: Long, entryId: Long, initialKind: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repo
    val money = rememberMoney()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val party by remember(partyId) { repo.party(partyId) }.collectAsStateWithLifecycle(initialValue = null)
    var kindId by rememberSaveable { mutableIntStateOf(initialKind) }
    var expr by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var loaded by rememberSaveable { mutableStateOf(entryId < 0) }
    var noteFocused by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (!loaded) {
            repo.entry(entryId)?.let { e ->
                kindId = e.kind.id
                expr = Money.toEditable(e.amount)
                note = e.note
                date = e.date
            }
            loaded = true
        }
        suggestions = repo.recentNotes()
    }

    val kind = EntryKind.of(kindId)
    val gave = kind == EntryKind.GAVE
    val accent = if (gave) Depter.colors.gave else Depter.colors.got
    val total = Calc.evaluate(expr) ?: 0L
    val valid = total in 1..Money.MAX_MINOR

    fun save() {
        if (saving) return
        if (!valid) {
            Toast.makeText(context, if (total < 0) R.string.error_negative else R.string.error_amount, Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        focus.clearFocus()
        keyboard?.hide()
        scope.launch(Dispatchers.Main.immediate) {
            if (entryId > 0) repo.updateEntry(entryId, partyId, kind, total, note, date)
            else repo.addEntry(partyId, kind, total, note, date)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    Column {
                        Text(
                            stringResource(if (entryId > 0) R.string.entry_edit_title else if (gave) R.string.entry_gave_title else R.string.entry_got_title),
                            maxLines = 1,
                        )
                        party?.let {
                            Text(
                                it.party.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (entryId > 0) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = { save() }, enabled = valid, modifier = Modifier.testTag("entry_save_top")) {
                        Icon(Icons.Filled.Check, stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = gave,
                        onClick = { kindId = EntryKind.GAVE.id },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Depter.colors.gaveContainer,
                            activeContentColor = Depter.colors.gave,
                        ),
                    ) { Text(stringResource(R.string.gave)) }
                    SegmentedButton(
                        selected = !gave,
                        onClick = { kindId = EntryKind.GOT.id },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Depter.colors.gotContainer,
                            activeContentColor = Depter.colors.got,
                        ),
                    ) { Text(stringResource(R.string.got)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (gave) R.string.gave_hint else R.string.got_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (Calc.hasOperator(expr)) {
                    Text(
                        expr.replace("+", " + ").replace("-", " − "),
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                }
                Text(
                    if (expr.isEmpty()) money.format(0) else money.format(total),
                    style = MaterialTheme.typography.displaySmall.merge(NumberStyle),
                    fontWeight = FontWeight.Bold,
                    color = if (expr.isEmpty()) MaterialTheme.colorScheme.outline else accent,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().testTag("entry_amount"),
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { noteFocused = it.isFocused }
                        .testTag("entry_note"),
                    placeholder = { Text(stringResource(if (gave) R.string.note_hint_gave else R.string.note_hint_got)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
                if (suggestions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        items(suggestions) { s ->
                            AssistChip(onClick = { note = s }, label = { Text(s, maxLines = 1) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    TextButton(onClick = { pickDate = true }) {
                        Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dayTitle(Dates.localDate(date)) + ", " + Dates.time(date, currentLocale()))
                    }
                }
            }
            if (!noteFocused) {
                Keypad(
                    accent = accent,
                    canSave = valid,
                    onKey = { k ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expr = when (k) {
                            KEY_BACK -> Calc.backspace(expr)
                            KEY_00 -> Calc.pressDoubleZero(expr)
                            else -> Calc.press(expr, k)
                        }
                    },
                    onClear = { expr = "" },
                    onSave = { save() },
                )
            }
        }
    }

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Dates.localDate(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    val ms = state.selectedDateMillis ?: return@TextButton
                    val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    val time = Instant.ofEpochMilli(date).atZone(Dates.zone).toLocalTime()
                    date = day.atTime(time).atZone(Dates.zone).toInstant().toEpochMilli()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch(Dispatchers.Main.immediate) {
                        repo.deleteEntry(entryId)
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val KEY_BACK = '<'
private const val KEY_00 = 'd'

@Composable
private fun Keypad(accent: Color, canSave: Boolean, onKey: (Char) -> Unit, onClear: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 6.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rows = listOf(
                listOf('7', '8', '9', KEY_BACK),
                listOf('4', '5', '6', '-'),
                listOf('1', '2', '3', '+'),
            )
            rows.forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { k ->
                        Key(
                            modifier = Modifier.weight(1f),
                            onClick = { onKey(k) },
                            onLongClick = if (k == KEY_BACK) onClear else null,
                            tag = "key_$k",
                            isOp = k == KEY_BACK || Calc.isOperator(k),
                        ) {
                            when (k) {
                                KEY_BACK -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.erase))
                                '-' -> Text("−", fontSize = 26.sp)
                                else -> Text(k.toString(), fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(Modifier.weight(1f), { onKey(KEY_00) }, tag = "key_00") { Text("00", fontSize = 24.sp) }
                Key(Modifier.weight(1f), { onKey('0') }, tag = "key_0") { Text("0", fontSize = 26.sp) }
                Key(Modifier.weight(1f), { onKey('.') }, tag = "key_dot") { Text(",", fontSize = 26.sp) }
                Surface(
                    onClick = onSave,
                    enabled = canSave,
                    shape = RoundedCornerShape(16.dp),
                    color = if (canSave) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (canSave) Color.White else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f).aspectRatio(1.6f).testTag("entry_save"),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    tag: String = "",
    isOp: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isOp) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .aspectRatio(1.6f)
            .testTag(tag)
            .combinedClickableKey(onClick, onLongClick),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableKey(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this
        .clip(RoundedCornerShape(16.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
