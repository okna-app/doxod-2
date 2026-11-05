@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import kg.depter.app.data.PartyRow
import kg.depter.app.data.PartySort
import kg.depter.app.data.PartyType
import kg.depter.app.domain.ProStatus
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
import kg.depter.app.ui.relativeDay
import kg.depter.app.ui.rememberMoney
import kg.depter.app.ui.reports.ReportsTab
import kg.depter.app.ui.settings.SettingsTab
import kg.depter.app.ui.theme.Depter
import kg.depter.app.ui.theme.NumberStyle
import java.time.LocalDate

@Composable
fun MainScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = context.container.repo
    val pro = LocalPro.current
    val settings = LocalSettings.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var typeId by rememberSaveable { mutableIntStateOf(0) }
    var showLimit by remember { mutableStateOf(false) }
    val rows by remember { repo.allParties() }.collectAsStateWithLifecycle(initialValue = null)
    val type = PartyType.of(typeId)

    fun addParty() {
        val count = rows?.size ?: 0
        if (!pro.isPro && count >= ProStatus.FREE_PARTY_LIMIT) showLimit = true
        else nav.navigate(Routes.partyEdit(type = typeId))
    }

    Scaffold(
        topBar = {
            when (tab) {
                0 -> TopAppBar(
                    title = {
                        Column {
                            Text(
                                settings.businessName.ifBlank { stringResource(R.string.app_name) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.home_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (pro.kind != ProStatus.Kind.PRO) {
                            IconButton(onClick = { nav.navigate(Routes.PRO) }) {
                                Icon(Icons.Filled.WorkspacePremium, stringResource(R.string.pro_title), tint = Depter.colors.warning)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                1 -> TopAppBar(
                    title = { Text(stringResource(R.string.tab_reports)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                else -> TopAppBar(
                    title = { Text(stringResource(R.string.tab_settings)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.MenuBook, null) },
                    label = { Text(stringResource(R.string.tab_book)) },
                    modifier = Modifier.testTag("tab_book"),
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.BarChart, null) },
                    label = { Text(stringResource(R.string.tab_reports)) },
                    modifier = Modifier.testTag("tab_reports"),
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    modifier = Modifier.testTag("tab_settings"),
                )
            }
        },
        floatingActionButton = {
            if (tab == 0 && !rows.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { addParty() },
                    icon = { Icon(Icons.Filled.PersonAdd, null) },
                    text = { Text(stringResource(if (type == PartyType.CUSTOMER) R.string.add_customer else R.string.add_supplier)) },
                    modifier = Modifier.testTag("fab_add_party"),
                )
            }
        },
    ) { padding ->
        when (tab) {
            0 -> HomeTab(
                padding = padding,
                rows = rows,
                type = type,
                onTypeChange = { typeId = it.id },
                onOpen = { nav.navigate(Routes.party(it)) },
                onAdd = { addParty() },
            )
            1 -> ReportsTab(padding = padding, nav = nav)
            else -> SettingsTab(padding = padding, nav = nav)
        }
    }

    if (showLimit) {
        ProRequiredDialog(
            message = stringResource(R.string.pro_limit_parties, ProStatus.FREE_PARTY_LIMIT),
            onDismiss = { showLimit = false },
            onOpenPro = {
                showLimit = false
                nav.navigate(Routes.PRO)
            },
        )
    }
}

@Composable
private fun HomeTab(
    padding: PaddingValues,
    rows: List<PartyRow>?,
    type: PartyType,
    onTypeChange: (PartyType) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortId by rememberSaveable { mutableIntStateOf(PartySort.RECENT.ordinal) }
    val sort = PartySort.entries[sortId]
    val all = rows ?: emptyList()
    val ofType = all.filter { it.party.type == type }
    val receivable = ofType.filter { it.balance > 0 }.sumOf { it.balance }
    val payable = -ofType.filter { it.balance < 0 }.sumOf { it.balance }
    val q = query.trim()
    val shown = ofType
        .filter { q.isEmpty() || it.party.name.contains(q, ignoreCase = true) || it.party.phone.contains(q) }
        .let { list ->
            when (sort) {
                PartySort.RECENT -> list.sortedByDescending { it.lastAt ?: it.party.createdAt }
                PartySort.AMOUNT -> list.sortedByDescending { kotlin.math.abs(it.balance) }
                PartySort.NAME -> list.sortedBy { it.party.name.lowercase() }
                PartySort.DUE -> list.sortedWith(compareBy<PartyRow> { it.party.dueDay == null || it.balance == 0L }.thenBy { it.party.dueDay })
            }
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("home_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val customers = all.count { it.party.type == PartyType.CUSTOMER }
                val suppliers = all.count { it.party.type == PartyType.SUPPLIER }
                SegmentedButton(
                    selected = type == PartyType.CUSTOMER,
                    onClick = { onTypeChange(PartyType.CUSTOMER) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {},
                    modifier = Modifier.testTag("seg_customers"),
                ) { Text(stringResource(R.string.customers_count, customers), maxLines = 1) }
                SegmentedButton(
                    selected = type == PartyType.SUPPLIER,
                    onClick = { onTypeChange(PartyType.SUPPLIER) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {},
                    modifier = Modifier.testTag("seg_suppliers"),
                ) { Text(stringResource(R.string.suppliers_count, suppliers), maxLines = 1) }
            }
        }
        item { SummaryHero(receivable = receivable, payable = payable) }

        if (rows != null && ofType.isEmpty()) {
            item {
                EmptyState(
                    icon = if (type == PartyType.CUSTOMER) Icons.Filled.People else Icons.Filled.Storefront,
                    title = stringResource(if (type == PartyType.CUSTOMER) R.string.empty_customers_title else R.string.empty_suppliers_title),
                    text = stringResource(if (type == PartyType.CUSTOMER) R.string.empty_customers_text else R.string.empty_suppliers_text),
                    action = {
                        Button(onClick = onAdd, modifier = Modifier.testTag("btn_add_first")) {
                            Icon(Icons.Filled.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (type == PartyType.CUSTOMER) R.string.add_customer else R.string.add_supplier))
                        }
                    },
                )
            }
        } else if (ofType.isNotEmpty()) {
            item {
                SearchRow(query = query, onQuery = { query = it }, sort = sort, onSort = { sortId = it.ordinal })
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nothing_found),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.party.id }) { row ->
                PartyListItem(row = row, onClick = { onOpen(row.party.id) })
            }
        }
    }
}

@Composable
private fun SummaryHero(receivable: Long, payable: Long) {
    val money = rememberMoney()
    val c = Depter.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.total_to_receive), color = c.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    money.format(receivable),
                    color = c.onHero,
                    style = MaterialTheme.typography.headlineSmall.merge(NumberStyle),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.testTag("total_receive"),
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(c.onHero.copy(alpha = 0.25f)),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.total_to_pay), color = c.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    money.format(payable),
                    color = c.onHero,
                    style = MaterialTheme.typography.headlineSmall.merge(NumberStyle),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, sort: PartySort, onSort: (PartySort) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).testTag("search"),
            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    PartySort.RECENT to R.string.sort_recent,
                    PartySort.AMOUNT to R.string.sort_amount,
                    PartySort.NAME to R.string.sort_name,
                    PartySort.DUE to R.string.sort_due,
                ).forEach { (s, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { RadioButton(selected = s == sort, onClick = null) },
                        onClick = {
                            onSort(s)
                            menu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PartyListItem(row: PartyRow, onClick: () -> Unit) {
    val money = rememberMoney()
    val today = LocalDate.now().toEpochDay()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().testTag("party_${row.party.id}"),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(row.party.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.party.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val due = row.party.dueDay
                if (due != null && row.balance != 0L) {
                    val overdue = due < today
                    val dueText = when {
                        due == today -> stringResource(R.string.due_today)
                        overdue -> stringResource(R.string.overdue_since, Dates.long(LocalDate.ofEpochDay(due), currentLocale()))
                        else -> stringResource(R.string.due_on, Dates.long(LocalDate.ofEpochDay(due), currentLocale()))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Event,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (overdue || due == today) Depter.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            dueText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue || due == today) Depter.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(
                        row.lastAt?.let { relativeDay(it) } ?: stringResource(R.string.no_entries_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money.format(kotlin.math.abs(row.balance)),
                    style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    color = balanceColor(row.balance),
                    maxLines = 1,
                )
                Text(
                    stringResource(balanceLabelRes(row.balance)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
