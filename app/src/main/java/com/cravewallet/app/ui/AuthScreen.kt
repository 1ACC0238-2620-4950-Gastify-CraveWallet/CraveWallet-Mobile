package com.cravewallet.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.BuildConfig
import com.cravewallet.app.R
import com.cravewallet.app.ui.components.CwTextButton
import com.cravewallet.app.ui.components.PrimaryButton
import com.cravewallet.app.ui.theme.*

@Composable
fun AuthScreen(vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var showTermsDialog by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable { mutableStateOf(vm.serverUrl) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = CwType.Title, color = OnSurface)
            Text(
                stringResource(if (register) R.string.auth_title_register else R.string.auth_title_login),
                style = CwType.Heading,
                color = OnSurface,
            )
            Text(stringResource(R.string.auth_subtitle), color = OnSurfaceVariant)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email_label)) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (register) {
                Text(stringResource(R.string.auth_password_hint), style = CwType.Caption)
                val checkboxCd = stringResource(R.string.auth_terms_checkbox_cd)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acceptedTerms,
                        onCheckedChange = { acceptedTerms = it },
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = checkboxCd },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.auth_terms_accept_prefix),
                        style = CwType.Body,
                        color = OnSurface,
                        modifier = Modifier.clickable(enabled = !busy) { acceptedTerms = !acceptedTerms },
                    )
                    Text(
                        text = stringResource(R.string.auth_terms_link),
                        style = CwType.BodyStrong,
                        color = Primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(role = Role.Button) { showTermsDialog = true },
                    )
                }
            }
            if (BuildConfig.DEBUG) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.auth_server_label)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (busy) {
                Text(stringResource(R.string.auth_connecting_hint), style = CwType.Caption, color = OnSurfaceVariant)
            }
            error?.let { Text(it, color = Error) }
            val canSubmit = !busy && email.isNotBlank() && password.isNotBlank() && (!register || acceptedTerms)
            Button(
                onClick = { vm.authenticate(email, password, register, url) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            busy -> R.string.auth_btn_connecting
                            register -> R.string.auth_btn_register
                            else -> R.string.auth_btn_login
                        }
                    )
                )
            }
            TextButton(
                onClick = {
                    register = !register
                    acceptedTerms = false
                    vm.clearError()
                },
                enabled = !busy,
            ) {
                Text(stringResource(if (register) R.string.auth_toggle_have_account else R.string.auth_toggle_create_account))
            }
            HorizontalDivider()
            TextButton(onClick = vm::demo, enabled = !busy) {
                Text(stringResource(R.string.auth_demo_btn))
            }
            Text(stringResource(R.string.auth_demo_caption), style = CwType.Caption, color = OnSurfaceVariant)
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = Surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(stringResource(R.string.terms_dialog_title), style = CwType.Heading, color = OnSurface)
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.terms_dialog_body),
                        style = CwType.Body,
                        color = OnSurface,
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.terms_dialog_accept_btn),
                    onClick = {
                        acceptedTerms = true
                        showTermsDialog = false
                    },
                    height = 44.dp,
                    shape = RoundedCornerShape(12.dp),
                )
            },
            dismissButton = {
                CwTextButton(
                    text = stringResource(R.string.terms_dialog_close_btn),
                    onClick = { showTermsDialog = false },
                )
            },
        )
    }
}
