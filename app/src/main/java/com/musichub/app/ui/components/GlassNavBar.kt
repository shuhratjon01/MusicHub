package com.musichub.app.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.app.ui.theme.UiStyle

data class NavItem(val label: String, val icon: ImageVector)

private val BarShape = RoundedCornerShape(28.dp)
private val ItemShape = RoundedCornerShape(22.dp)

/**
 * Compact floating bar in the Telegram style: 60dp tall, rounded corners, capsule highlight on the
 * active tab. Glass mode blurs [backdrop] (RenderEffect blur on API 31+, tinted fallback below),
 * Matte mode is a solid surface.
 */
@Composable
fun GlassNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GraphicsLayer,
    style: UiStyle,
    modifier: Modifier = Modifier,
) {
    val isGlass = style == UiStyle.Glass
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurLayer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val matteColor = MaterialTheme.colorScheme.surfaceVariant
    val bg = MaterialTheme.colorScheme.background
    val isDarkTheme = bg.luminance() < 0.5f
    val isOled = bg == Color.Black
    val baseColor = if (isGlass && isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
    val glassTint = when {
        isOled -> 0.22f
        isDarkTheme -> if (canBlur) 0.14f else 0.30f
        else -> if (canBlur) 0.55f else 0.80f
    }

    val borderBrush = if (isGlass) Brush.horizontalGradient(
        listOf(
            Color.White.copy(alpha = 0.65f),
            Color.White.copy(alpha = 0.06f),
            Color(0xFF9FE8FF).copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.65f),
        )
    ) else Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f))
    )

    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(BarShape)
            .onGloballyPositioned { position = it.positionInRoot() }
            .drawBehind {
                if (!isGlass) {
                    drawRect(matteColor)
                    return@drawBehind
                }
                if (backdrop.size != IntSize.Zero) {
                    blurLayer.renderEffect = if (canBlur) BlurEffect(40f, 40f, TileMode.Clamp) else null
                    blurLayer.record(size = backdrop.size) { drawLayer(backdrop) }
                    translate(-position.x, -position.y) { drawLayer(blurLayer) }
                }
                drawRect(Color.White.copy(alpha = glassTint))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)))
            }
            .border(1.dp, borderBrush, BarShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavBarItem(
                item = item,
                selected = index == selectedIndex,
                baseColor = baseColor,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    baseColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(
        targetValue = if (selected) accent else baseColor.copy(alpha = 0.7f),
        label = "tint",
    )
    val highlightAlpha by animateFloatAsState(if (selected) 0.20f else 0f, label = "highlight")

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(ItemShape)
            .drawBehind { drawRect(accent.copy(alpha = highlightAlpha)) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            color = tint,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
