@file:OptIn(ExperimentalMaterial3Api::class)

package kg.depter.app.ui.party

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kg.depter.app.R
import kg.depter.app.container
import kg.depter.app.data.EntryKind
import kg.depter.app.data.PartyType
import kg.depter.app.domain.Money
import kg.depter.app.ui.Routes
import kg.depter.app.ui.theme.Depter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun PartyEditScreen(partyId: Long, initialType: Int, nav: NavController) {
    val context = LocalContext.current
    val repo = context.container.repo
    val scope = rememberCoroutineScope()
    val editing = partyId > 0

    var typeId by rememberSaveable { mutableIntStateOf(initialType) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("") }
    var openingTheyOwe by rememberSaveable { mutableStateOf(true) }
    var loaded by rememberSaveable { mutableStateOf(!editing) }
    var nameError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(partyId) {
        if (!loaded) {
            repo.partyNow(partyId)?.let {
                typeId = it.party.type.id
                name = it.party.name
                phone = it.party.phone
                note = it.party.note
            }
            loaded = true
        } else if (!editing) {
            nameFocus.requestFocus()
        }
    }

    val pickContact = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0).orEmpty()
                    val p = c.getString(1).orEmpty()
                    if (name.isBlank()) name = n
                    phone = p
                }
            }
        } catch (e: Exception) {
            // Some contact apps return URIs we cannot read; the user can type the number.
        }
    }

    val type = PartyType.of(typeId)

    fun save() {
        if (saving) return
        if (name.isBlank()) {
            nameError = true
            return
        }
        saving = true
        scope.launch(Dispatchers.Main.immediate) {
            if (editing) {
                repo.updateParty(partyId, type, name, phone, note)
                nav.popBackStack()
            } else {
                val id = repo.addParty(type, name, phone, note)
                val amount = Money.parse(opening) ?: 0L
                if (amount > 0) {
                    repo.addEntry(
                        partyId = id,
                        kind = if (openingTheyOwe) EntryKind.GAVE else EntryKind.GOT,
                        amount = amount,
                        note = context.getString(R.string.opening_balance_note),
                        date = System.currentTimeMillis(),
                    )
                }
                nav.navigate(Routes.party(id)) { popUpTo(Routes.PARTY_EDIT) { inclusive = true } }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    Text(
                        stringResource(
                            when {
                                editing -> R.string.edit_party_title
                                type == PartyType.CUSTOMER -> R.string.new_customer
                                else -> R.string.new_supplier
                            },
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        .height(54.dp)
                        .testTag("party_save"),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == PartyType.CUSTOMER,
                    onClick = { typeId = PartyType.CUSTOMER.id },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.customer)) }
                SegmentedButton(
                    selected = type == PartyType.SUPPLIER,
                    onClick = { typeId = PartyType.SUPPLIER.id },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.supplier)) }
            }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 60) name = it
                    nameError = false
                },
                label = { Text(stringResource(R.string.field_name)) },
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                isError = nameError,
                supportingText = if (nameError) ({ Text(stringResource(R.string.error_name)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("party_name"),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { if (it.length <= 24) phone = it },
                label = { Text(stringResource(R.string.field_phone)) },
                placeholder = { Text("0555 123 456") },
                leadingIcon = { Icon(Icons.Filled.Phone, null) },
                trailingIcon = {
                    IconButton(onClick = { runCatching { pickContact.launch(Unit) } }) {
                        Icon(Icons.Filled.Contacts, stringResource(R.string.pick_contact))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("party_phone"),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text(stringResource(R.string.field_note)) },
                leadingIcon = { Icon(Icons.Filled.Notes, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                maxLines = 3,
            )
            if (!editing) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.opening_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.opening_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = opening,
                            onValueChange = { v -> if (v.length <= 14 && v.all { it.isDigit() || it == '.' || it == ',' }) opening = v },
                            label = { Text(stringResource(R.string.field_amount)) },
                            leadingIcon = { Icon(Icons.Filled.History, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().testTag("party_opening"),
                            shape = RoundedCornerShape(14.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = openingTheyOwe,
                                onClick = { openingTheyOwe = true },
                                label = { Text(stringResource(R.string.opening_they_owe)) },
                            )
                            FilterChip(
                                selected = !openingTheyOwe,
                                onClick = { openingTheyOwe = false },
                                label = { Text(stringResource(R.string.opening_i_owe)) },
                            )
                        }
                        if (opening.isNotBlank()) {
                            Text(
                                stringResource(if (openingTheyOwe) R.string.to_receive else R.string.to_pay),
                                color = if (openingTheyOwe) Depter.colors.got else Depter.colors.gave,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
