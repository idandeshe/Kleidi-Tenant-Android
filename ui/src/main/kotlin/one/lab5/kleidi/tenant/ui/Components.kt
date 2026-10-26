// The small kit every screen is built from. No user-facing strings live here: labels come in
// as parameters from :app's resources.

package one.lab5.kleidi.tenant.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val CardShape = RoundedCornerShape(16.dp)
val PillShape = RoundedCornerShape(999.dp)

/** A white card with a hairline border — the surface of every block on Home and Profile. */
@Composable
fun TenantCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(TenantTheme.colors.ground)
            .border(1.dp, TenantTheme.colors.hairline, CardShape)
            .padding(contentPadding),
        content = content,
    )
}

/** A card with a dashed border: something that is not there yet ("Pay online — coming soon"). */
@Composable
fun DashedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val stroke = TenantTheme.colors.hairline
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .drawBehind {
                val inset = 1.dp.toPx() / 2
                drawRoundRect(
                    color = stroke,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(contentPadding),
        content = content,
    )
}

enum class PillTone { Accent, Neutral, Success, Warning, Error }

/** A small rounded label: the due day on the rent card, a status, "COMING SOON". */
@Composable
fun Pill(text: String, tone: PillTone = PillTone.Accent, modifier: Modifier = Modifier, uppercase: Boolean = false) {
    val c = TenantTheme.colors
    val (fill, color) = when (tone) {
        PillTone.Accent -> c.accentSoft to c.accent
        PillTone.Neutral -> c.surface to c.secondaryText
        PillTone.Success -> c.successSoft to c.success
        PillTone.Warning -> c.warningSoft to c.warning
        PillTone.Error -> c.errorSoft to c.error
    }
    Text(
        if (uppercase) text.uppercase() else text,
        modifier = modifier.background(fill, PillShape).padding(horizontal = 10.dp, vertical = 4.dp),
        style = if (uppercase) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
    )
}

/** The accent button; [loading] replaces the label with a spinner and disables it. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TenantTheme.colors.accent,
            contentColor = Color.White,
            disabledContainerColor = TenantTheme.colors.surface,
            disabledContentColor = TenantTheme.colors.secondaryText,
        ),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) TenantTheme.colors.error else TenantTheme.colors.accent
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.4f) else TenantTheme.colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = TenantTheme.colors.secondaryText),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A large selectable choice (the language buttons on the first screen): title, optional
 * subtitle, a check when selected.
 */
@Composable
fun ChoiceButton(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val c = TenantTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) c.accentSoft else c.ground)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.hairline, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (selected) c.accent else c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(26.dp))
    }
}

/** A red banner for an error that belongs to the whole screen or form. */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(TenantTheme.colors.errorSoft, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = TenantTheme.colors.error)
        Spacer(Modifier.width(8.dp))
        Text(message, color = TenantTheme.colors.error, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A soft note (accent background): information, not a problem. */
@Composable
fun InfoNote(message: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Accent) {
    val c = TenantTheme.colors
    val (fill, color) = when (tone) {
        PillTone.Warning -> c.warningSoft to c.warning
        PillTone.Success -> c.successSoft to c.success
        PillTone.Error -> c.errorSoft to c.error
        else -> c.accentSoft to c.accent
    }
    Text(
        message,
        modifier = modifier.fillMaxWidth().background(fill, RoundedCornerShape(12.dp)).padding(12.dp),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Uppercase section label above a block of content. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = TenantTheme.colors.secondaryText,
    )
}

/** Label over value — one cell of the "Your lease" grid. */
@Composable
fun DetailCell(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TenantTheme.colors.secondaryText)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = TenantTheme.colors.ink,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** A settings-style row: title (+ optional value) with a chevron, or a destructive action. */
@Composable
fun ListRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    chevron: Boolean = onClick != null,
) {
    val c = TenantTheme.colors
    val color = if (destructive) c.error else c.ink
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (destructive) c.error else c.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = c.hairline, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = TenantTheme.colors.hairline, modifier = Modifier.padding(horizontal = 16.dp))
}

/** The state of one screen's data. */
@Immutable
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>
    data class Failed(val error: Throwable) : Loadable<Nothing>

    val valueOrNull: T? get() = (this as? Loaded<T>)?.value

    companion object {
        /** Runs [load] and turns the outcome into a state (cancellation propagates). */
        suspend fun <T> run(load: suspend () -> T): Loadable<T> = try {
            Loaded(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Failed(e)
        }
    }
}

/** Spinner, an error with Retry, or the content. */
@Composable
fun <T> LoadableView(
    state: Loadable<T>,
    retry: suspend () -> Unit,
    errorTitle: String,
    errorMessage: (Throwable) -> String,
    retryLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        Loadable.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TenantTheme.colors.accent)
        }
        is Loadable.Failed -> {
            val scope = rememberCoroutineScope()
            EmptyState(
                title = errorTitle,
                message = errorMessage(state.error),
                icon = Icons.Outlined.ErrorOutline,
                actionLabel = retryLabel,
                onAction = { scope.launch { retry() } },
                modifier = modifier,
            )
        }
        is Loadable.Loaded -> content(state.value)
    }
}

/** An icon, a title, a message and an optional action, centred. */
@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = TenantTheme.colors.secondaryText, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = TenantTheme.colors.secondaryText, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) {
                PrimaryButton(actionLabel, onAction, modifier = Modifier.padding(top = 8.dp).width(200.dp))
            }
        }
    }
}
