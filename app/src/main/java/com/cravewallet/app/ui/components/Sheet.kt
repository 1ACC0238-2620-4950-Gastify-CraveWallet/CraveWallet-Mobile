package com.cravewallet.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Scrim
import com.cravewallet.app.ui.theme.Surface

/**
 * Hoja inferior modal con scrim y manija (Design System · Bottom sheet).
 * Se dibuja sobre la app; el botón Atrás llama a [onDismissRequest].
 * @param fullHeight ocupa toda la pantalla salvo la barra de estado (alta de gasto).
 */
@Composable
fun SheetOverlay(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fullHeight: Boolean = true,
    dismissOnScrim: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismissRequest)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = dismissOnScrim,
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically { it / 3 } + fadeIn(),
        ) {
            Column(
                modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .then(if (fullHeight) Modifier.fillMaxSize() else Modifier)
                    .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Surface)
                    // Evita que los toques en la hoja cierren el scrim
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OnSurfaceVariant),
                    )
                }
                content()
            }
        }
    }
}
