package com.lapel.app.ui.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R
import com.lapel.app.ui.common.DateInput
import com.lapel.app.ui.common.TextInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerEditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: CustomerEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    LaunchedEffect(savedId) { savedId?.let(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.customer_new else R.string.customer_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextInput(form.name, { v -> viewModel.edit { copy(name = v) } }, stringResource(R.string.field_name), isError = form.showErrors && form.nameError)
            TextInput(form.organization, { v -> viewModel.edit { copy(organization = v) } }, stringResource(R.string.field_organization))
            TextInput(form.phone, { v -> viewModel.edit { copy(phone = v) } }, stringResource(R.string.field_phone), keyboardType = KeyboardType.Phone)
            TextInput(form.email, { v -> viewModel.edit { copy(email = v) } }, stringResource(R.string.field_email), keyboardType = KeyboardType.Email)
            TextInput(
                form.paymentTermsDays, { v -> viewModel.edit { copy(paymentTermsDays = v.filter(Char::isDigit)) } },
                stringResource(R.string.field_payment_terms), keyboardType = KeyboardType.Number,
                isError = form.showErrors && form.termsError,
            )
            DateInput(form.customerSince, { d -> d?.let { viewModel.edit { copy(customerSince = it) } } }, stringResource(R.string.field_customer_since))
            TextInput(form.notes, { v -> viewModel.edit { copy(notes = v) } }, stringResource(R.string.field_notes), singleLine = false)
            Button(onClick = viewModel::save, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}
