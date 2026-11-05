@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.ledger

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.data.EntryKind
import kg.depter.app.data.LedgerLine
import kg.depter.app.data.PartyRow
import kg.depter.app.data.PartyType
import kg.depter.app.domain.Countries
import kg.depter.app.domain.Phone
import kg.depter.app.notify.Reminders
import kg.depter.app.pdf.PdfExport
import kg.depter.app.ui.Avatar
import kg.depter.app.ui.Dates
import kg.depter.app.ui.EmptyState
import kg.depter.app.ui.LocalPro
import kg.depter.app.ui.LocalSettings
import kg.depter.app.ui.ProRequiredDialog
import kg.depter.app.ui.Routes
import kg.depter.app.ui.balanceColor
import kg.depter.app.ui.balanceLabelRes
import kg.depter.app.ui.currentLocale
import kg.depter.app.ui.dayTitle
import kg.depter.app.ui.rememberMoney
import kg.depter.app.ui.theme.AmountLarge
import kg.depter.app.ui.theme.Depter
import kg.depter.app.ui.theme.NumberStyle
import kg.depter.app.util.Intents
import kg.depter.app.util.ReminderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LedgerScreen(partyId: Long, nav: NavController) {
    val context = LocalContext.current
    val repo = context.container.repo
    val settings = LocalSettings.current
    val pro = LocalPro.current
    val money = rememberMoney()
    val scope = rememberCoroutineScope()

    val row by remember(partyId) { repo.party(partyId) }.collectAsStateWithLifecycle(initialValue = null)
    val lines by remember(partyId) { repo.ledger(partyId) }.collectAsStateWithLifecycle(initialValue = null)

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var proDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(row) {
        if (row != null) loaded = true
        // The party was deleted (or never existed): leave the screen.
        if (row == null && loaded) nav.popBackStack()
    }

    val r = row
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    if (r != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.party.name, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(r.party.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    r.party.phone.ifBlank {
                                        stringResource(if (r.party.type == PartyType.CUSTOMER) R.string.customer else R.string.supplier)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (r != null && r.party.phone.isNotBlank()) {
                        IconButton(onClick = { Intents.dial(context, r.party.phone) }) {
                            Icon(Icons.Filled.Phone, stringResource(R.string.call))
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("ledger_menu")) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = {
                                    menu = false
                                    if (r != null) nav.navigate(Routes.partyEdit(r.party.id, r.party.type.id))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (r != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { nav.navigate(Routes.entry(partyId, kind = EntryKind.GAVE.id)) },
                            modifier = Modifier.weight(1f).height(56.dp).testTag("btn_gave"),
                            colors = ButtonDefaults.buttonColors(containerColor = Depter.colors.gave, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.CallMade, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.gave), style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = { nav.navigate(Routes.entry(partyId, kind = EntryKind.GOT.id)) },
                            modifier = Modifier.weight(1f).height(56.dp).testTag("btn_got"),
                            colors = ButtonDefaults.buttonColors(containerColor = Depter.colors.got, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.CallReceived, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.got), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (r == null) return@Scaffold
        val all = lines ?: emptyList()
        val grouped: List<Pair<LocalDate, List<LedgerLine>>> = remember(all) {
            all.groupBy { Dates.localDate(it.entry.date) }.toList()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("ledger_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                BalanceCard(
                    row = r,
                    onDue = { pickDue = true },
                    onWhatsapp = {
                        val amount = money.format(kotlin.math.abs(r.balance))
                        val text = ReminderText.build(context, settings, pro, r.party.name, amount, r.balance > 0)
                        Intents.whatsapp(context, Phone.toInternational(r.party.phone, Countries.byIso(settings.country).phoneCode), text)
                    },
                    onSms = {
                        val amount = money.format(kotlin.math.abs(r.balance))
                        val text = ReminderText.build(context, settings, pro, r.party.name, amount, r.balance > 0)
                        Intents.sms(context, r.party.phone, text)
                    },
                    onPdf = {
                        if (!pro.isPro) {
                            proDialog = true
                        } else {
                            scope.launch {
                                val ledger = repo.ledgerNow(partyId)
                                val file = withContext(Dispatchers.IO) { PdfExport.statement(context, settings, r, ledger) }
                                Intents.shareFile(context, file, "application/pdf")
                            }
                        }
                    },
                )
            }
            if (lines != null && all.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.ReceiptLong,
                        title = stringResource(R.string.ledger_empty_title),
                        text = stringResource(R.string.ledger_empty_text),
                    )
                }
            }
            grouped.forEach { (day, dayLines) ->
                item(key = "h_${day.toEpochDay()}") {
                    Text(
                        dayTitle(day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(dayLines, key = { it.entry.id }) { line ->
                    EntryItem(line = line, onClick = { nav.navigate(Routes.entry(partyId, line.entry.id, line.entry.kind.id)) })
                }
            }
        }
    }

    if (confirmDelete && r != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text(stringResource(R.string.delete_party_title)) },
            text = { Text(stringResource(R.string.delete_party_text, r.party.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        Reminders.schedule(context, r.party.id, null)
                        repo.deleteParty(r.party.id)
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (pickDue && r != null) {
        val initial = (r.party.dueDay ?: LocalDate.now().plusDays(7).toEpochDay())
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(initial).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDue = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDue = false
                    val ms = state.selectedDateMillis ?: return@TextButton
                    val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    scope.launch {
                        repo.setDueDay(r.party.id, day)
                        Reminders.schedule(context, r.party.id, day)
                    }
                    if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Toast.makeText(context, R.string.due_saved, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (r.party.dueDay != null) {
                        TextButton(onClick = {
                            pickDue = false
                            scope.launch {
                                repo.setDueDay(r.party.id, null)
                                Reminders.schedule(context, r.party.id, null)
                            }
                        }) { Text(stringResource(R.string.remove)) }
                    }
                    TextButton(onClick = { pickDue = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (proDialog) {
        ProRequiredDialog(
            message = stringResource(R.string.pro_feature_pdf),
            onDismiss = { proDialog = false },
            onOpenPro = {
                proDialog = false
                nav.navigate(Routes.PRO)
            },
        )
    }
}

@Composable
private fun BalanceCard(
    row: PartyRow,
    onDue: () -> Unit,
    onWhatsapp: () -> Unit,
    onSms: () -> Unit,
    onPdf: () -> Unit,
) {
    val money = rememberMoney()
    val today = LocalDate.now().toEpochDay()
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(balanceLabelRes(row.balance)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                money.format(kotlin.math.abs(row.balance)),
                style = AmountLarge,
                color = balanceColor(row.balance),
                modifier = Modifier.testTag("ledger_balance"),
            )
            Spacer(Modifier.height(10.dp))
            val due = row.party.dueDay
            val dueColor = if (due != null && due <= today && row.balance != 0L) Depter.colors.warning else MaterialTheme.colorScheme.primary
            Surface(
                onClick = onDue,
                shape = RoundedCornerShape(12.dp),
                color = if (due != null && due <= today && row.balance != 0L) Depter.colors.warningContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.testTag("btn_due"),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Event, null, tint = dueColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (due == null) {
                            stringResource(R.string.set_due_date)
                        } else {
                            stringResource(R.string.due_label, Dates.long(LocalDate.ofEpochDay(due), currentLocale()))
                        },
                        color = dueColor,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.whatsapp), Modifier.weight(1f).testTag("btn_whatsapp"), onWhatsapp)
                ActionButton(Icons.Filled.Sms, stringResource(R.string.sms), Modifier.weight(1f), onSms, enabled = row.party.phone.isNotBlank())
                ActionButton(Icons.Filled.PictureAsPdf, stringResource(R.string.pdf), Modifier.weight(1f).testTag("btn_pdf"), onPdf)
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EntryItem(line: LedgerLine, onClick: () -> Unit) {
    val money = rememberMoney()
    val e = line.entry
    val gave = e.kind == EntryKind.GAVE
    val color = if (gave) Depter.colors.gave else Depter.colors.got
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().testTag("entry_${e.id}"),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(if (gave) Depter.colors.gaveContainer else Depter.colors.gotContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (gave) Icons.Filled.CallMade else Icons.Filled.CallReceived, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.note.ifBlank { stringResource(if (gave) R.string.gave_default_note else R.string.got_default_note) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Dates.time(e.date, currentLocale()) + " · " + stringResource(R.string.balance_after, money.format(kotlin.math.abs(line.balanceAfter))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                money.format(e.amount),
                style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
