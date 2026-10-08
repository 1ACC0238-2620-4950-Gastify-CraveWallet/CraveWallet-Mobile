package com.cravewallet.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cravewallet.app.R
import com.cravewallet.app.data.AppState
import com.cravewallet.app.data.Currency
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.SubStatus
import com.cravewallet.app.data.Subscription
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Tarjeta de suscripción de la lista de Gastos (84 dp). */
@Composable
fun SubscriptionCard(
    sub: Subscription,
    state: AppState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status(sub)
    CwCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ServiceAvatar(sub.name, sub.category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sub.name, style = CwType.BodyStrong, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${sub.category.label} · ${Fmt.dayMonth(sub.nextCharge)}",
                    style = CwType.Caption,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(1.dp))
                StatusChip(status)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.Top).padding(top = 12.dp)) {
                Text(
                    Fmt.pen(state.pen(sub)),
                    style = CwType.BodyLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
                    color = if (status == SubStatus.CANCELADA) OnSurfaceVariant else OnSurface,
                )
                if (sub.currency != Currency.PEN) {
                    Text(Fmt.money(sub.amount, sub.currency), style = CwType.Caption, fontWeight = FontWeight.Medium, color = OnSurfaceVariant)
                }
            }
        }
    }
}


/**
 * Envuelve una tarjeta para que al deslizarla a la izquierda muestre la acción "Sin usar" (G2).
 * La acción también está disponible como acción de accesibilidad.
 */
@Composable
fun SwipeActionCard(
    actionLabel: String,
    actionIcon: Int,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { 96.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(CustomAccessibilityAction(actionLabel) { onAction(); true })
            },
    ) {
        // Fondo con la acción
        Row(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceVariant),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.Button) {
                        onAction()
                        scope.launch { offset.animateTo(0f) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CwIcon(actionIcon, tint = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text(actionLabel, style = CwType.Caption, fontWeight = FontWeight.Medium, color = OnSurface)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-actionWidthPx, 0f)) }
                    },
                    onDragStopped = {
                        val target = if (offset.value < -actionWidthPx / 2) -actionWidthPx else 0f
                        offset.animateTo(target)
                    },
                ),
        ) { content() }
    }
}

/** Tarjeta pequeña del carrusel "Próximos cobros" (152 × 148 dp). */
@Composable
fun UpcomingCard(sub: Subscription, state: AppState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CwCard(modifier = modifier.width(152.dp).height(148.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // El chip se superpone al borde derecho como en el wireframe, sin partir el texto
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                ServiceAvatar(sub.name, sub.category)
                StatusChip(state.status(sub), Modifier.align(Alignment.TopEnd).offset(x = 6.dp))
            }
            Text(sub.name, style = CwType.BodyStrong, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Fmt.relativeDays(sub.nextCharge)} · ${Fmt.dayMonth(sub.nextCharge)}", style = CwType.Caption, color = OnSurfaceVariant)
            Text(Fmt.pen(state.pen(sub)), style = CwType.Heading.copy(fontFamily = com.cravewallet.app.ui.theme.Poppins, fontWeight = FontWeight.Bold), color = OnSurface)
        }
    }
}

/** Última tarjeta del carrusel: "Ver los 7". */
@Composable
fun SeeAllCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CwCard(
        modifier = modifier.width(152.dp).height(148.dp),
        onClick = onClick,
        elevated = false,
        color = PrimaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Primary),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CwIcon(R.drawable.ic_receipt_long, size = 28.dp, tint = Primary)
            Spacer(Modifier.size(8.dp))
            Text("Ver los $count", style = CwType.BodyStrong, color = Primary)
        }
    }
}
