package com.cravewallet.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cravewallet.app.R
import com.cravewallet.app.data.Catalog
import com.cravewallet.app.data.Category
import com.cravewallet.app.data.Fmt
import com.cravewallet.app.data.SubStatus
import com.cravewallet.app.ui.theme.CwType
import com.cravewallet.app.ui.theme.OnPrimaryContainer
import com.cravewallet.app.ui.theme.OnSurface
import com.cravewallet.app.ui.theme.OnSurfaceVariant
import com.cravewallet.app.ui.theme.Outline
import com.cravewallet.app.ui.theme.OutlineVariant
import com.cravewallet.app.ui.theme.Primary
import com.cravewallet.app.ui.theme.PrimaryContainer
import com.cravewallet.app.ui.theme.Surface as SurfaceColor
import com.cravewallet.app.ui.theme.SurfaceVariant

@Composable
fun CwIcon(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = OnSurface,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Avatar cuadrado con las iniciales y el color de la marca del servicio. */
@Composable
fun ServiceAvatar(
    name: String,
    category: Category?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val known = Catalog.find(name)
    val bg = known?.brand ?: (category?.color?.copy(alpha = 0.16f) ?: PrimaryContainer)
    val fg = known?.onBrand ?: OnPrimaryContainer
    val initials = known?.initials ?: Fmt.serviceInitials(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = CwType.BodyStrong.copy(fontSize = (size.value * 0.35f).sp, lineHeight = (size.value * 0.45f).sp),
            color = fg,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

/** Círculo con el ícono de la categoría. */
@Composable
fun CategoryIcon(category: Category, modifier: Modifier = Modifier, size: Dp = 40.dp, filled: Boolean = true) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) category.color else category.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        CwIcon(
            category.icon,
            size = size * 0.55f,
            tint = if (filled) (if (category == Category.DELIVERY || category == Category.MUSICA) OnSurface else Color.White) else category.color,
        )
    }
}

@Composable
fun StatusChip(status: SubStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(status.container)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CwIcon(status.icon, size = 14.dp, tint = status.content)
        Text(status.label, style = CwType.Caption, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = status.content)
    }
}

@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(com.cravewallet.app.ui.theme.AccentContainer)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CwIcon(R.drawable.ic_workspace_premium_fill, size = 16.dp, tint = com.cravewallet.app.ui.theme.Accent)
        Text("Premium", style = CwType.Caption, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = OnSurface)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = CwType.Heading, color = OnSurface, modifier = Modifier.semantics { heading() })
        if (action != null) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(action, style = CwType.BodyStrong, color = Primary)
                CwIcon(R.drawable.ic_chevron_right, size = 20.dp, tint = Primary)
            }
        }
    }
}

/** Tarjeta blanca con sombra suave (elevación 1 del Design System). */
@Composable
fun CwCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = SurfaceColor,
    elevated: Boolean = true,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .then(if (elevated) Modifier.shadow(1.5.dp, shape, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000)) else Modifier)
        .clip(shape)
        .background(color)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(modifier = base, content = content)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    container: Color = Primary,
    content: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = SurfaceVariant,
            disabledContentColor = OnSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            CwIcon(icon, size = 20.dp, tint = if (enabled) content else OnSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = if (height >= 56.dp) CwType.Button else CwType.ButtonSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    height: Dp = 56.dp,
) = PrimaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    height = height,
    container = PrimaryContainer,
    content = OnPrimaryContainer,
)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    height: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = shape,
        border = BorderStroke(1.dp, Primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            CwIcon(icon, size = 20.dp, tint = Primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = if (height >= 56.dp) CwType.Button else CwType.ButtonSmall, maxLines = 1)
    }
}

@Composable
fun CwTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = OnSurface,
    iconTint: Color = color,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        shape = RoundedCornerShape(8.dp),
    ) {
        if (icon != null) {
            CwIcon(icon, size = 20.dp, tint = iconTint)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = CwType.ButtonSmall)
    }
}

/** Enlace subrayado (p. ej. "Ver análisis", "Revisar"). */
@Composable
fun LinkText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = OnSurface,
    @DrawableRes trailing: Int? = null,
) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = CwType.BodyStrong, color = color, textDecoration = TextDecoration.Underline)
        if (trailing != null) CwIcon(trailing, size = 18.dp, tint = color)
    }
}

@Composable
fun CwSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            { CwIcon(R.drawable.ic_check, size = 16.dp, tint = Primary) }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Primary,
            uncheckedThumbColor = OnSurfaceVariant,
            uncheckedTrackColor = SurfaceColor,
            uncheckedBorderColor = OnSurfaceVariant,
        ),
    )
}

/** Fila de lista con ícono en círculo, título, subtítulo y contenido al final. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = OnSurface,
    iconBackground: Color = SurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = { CwIcon(R.drawable.ic_chevron_right, tint = OnSurfaceVariant) },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) { CwIcon(icon, size = 22.dp, tint = iconTint) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CwType.BodyStrong, color = OnSurface)
            if (subtitle != null) Text(subtitle, style = CwType.Caption, color = OnSurfaceVariant)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = OutlineVariant)

/** Banner informativo sobre surface-variant (alertas, consejos, estados). */
@Composable
fun InfoBanner(
    @DrawableRes icon: Int,
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    container: Color = SurfaceVariant,
    iconTint: Color = OnSurface,
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CwIcon(icon, size = 24.dp, tint = iconTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = CwType.BodyStrong, color = OnSurface)
            Text(body, style = CwType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp), color = OnSurface)
            if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}

@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(SurfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/** Barra superior de pantallas secundarias: atrás + título + acciones. */
@Composable
fun CwTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { CwIcon(R.drawable.ic_arrow_back, contentDescription = "Atrás") }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = CwType.Title,
            color = OnSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Estado vacío centrado con ícono grande. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(PrimaryContainer),
            contentAlignment = Alignment.Center,
        ) { CwIcon(icon, size = 56.dp, tint = OnPrimaryContainer) }
        Spacer(Modifier.height(24.dp))
        Text(title, style = CwType.Heading, color = OnSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = CwType.Body, color = OnSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** Ítem de lista con check (beneficios de Premium). */
@Composable
fun CheckItem(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CwIcon(R.drawable.ic_check_circle, size = 20.dp, tint = com.cravewallet.app.ui.theme.Success)
        Text(text, style = CwType.Body, color = OnSurface)
    }
}

/** Contenedor redondeado con borde para agrupar filas (Recordatorio, Historial, etc.). */
@Composable
fun OutlinedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, OutlineVariant),
    ) { Column(content = content) }
}

/** Ícono circular con fondo (encabezado de diálogos). */
@Composable
fun DialogIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = OnPrimaryContainer, background: Color = PrimaryContainer) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) { CwIcon(icon, size = 28.dp, tint = tint) }
}

val FieldBorder = Outline
