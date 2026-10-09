package com.cravewallet.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cravewallet.app.AppViewModel
import com.cravewallet.app.BuildConfig
import com.cravewallet.app.ui.theme.*

@Composable
fun AuthScreen(vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(vm.serverUrl) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Surface(color=Background, modifier=Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("CraveWallet",style=CwType.Title,color=OnSurface)
            Text(if(register) "Crea tu cuenta" else "Inicia sesión",style=CwType.Heading,color=OnSurface)
            Text("Guarda tus suscripciones y gastos en tu cuenta.",color=OnSurfaceVariant)
            OutlinedTextField(value=email,onValueChange={email=it},label={Text("Correo electrónico")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=password,onValueChange={password=it},label={Text("Contraseña")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!busy,modifier=Modifier.fillMaxWidth())
            if(register) Text("Usa al menos 8 caracteres, con letras y números.",style=CwType.Caption)
            if(BuildConfig.DEBUG) OutlinedTextField(value=url,onValueChange={url=it},label={Text("Servidor de pruebas")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
            error?.let { Text(it,color=Error) }
            Button(onClick={vm.authenticate(email,password,register,url)},enabled=!busy && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth()) {
                Text(if(busy) "Conectando…" else if(register) "Crear cuenta" else "Ingresar")
            }
            TextButton(onClick={register=!register;vm.clearError()},enabled=!busy) { Text(if(register) "Ya tengo una cuenta" else "Crear una cuenta") }
            HorizontalDivider()
            TextButton(onClick=vm::demo,enabled=!busy) { Text("Ver demostración sin conexión") }
            Text("La demostración utiliza datos de ejemplo y no modifica tu cuenta.",style=CwType.Caption,color=OnSurfaceVariant)
        }
    }
}
