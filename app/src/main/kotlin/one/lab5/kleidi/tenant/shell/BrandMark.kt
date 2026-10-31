package one.lab5.kleidi.tenant.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.ui.TenantTheme

enum class BrandMarkSize(val houseman: Dp, val seamphony: Dp, val spacing: Dp, val corner: Dp) {
    Large(houseman = 56.dp, seamphony = 24.dp, spacing = 16.dp, corner = 8.dp),
    Small(houseman = 28.dp, seamphony = 13.dp, spacing = 10.dp, corner = 4.dp),
}

/**
 * The Houseman Brokers and Seamphony marks side by side (iOS `BrandMark`). [BrandMarkSize.Large] heads the
 * sign-in screens; [BrandMarkSize.Small] sits on Home and above the version on Profile.
 */
@Composable
fun BrandMark(size: BrandMarkSize, modifier: Modifier = Modifier) {
    val c = TenantTheme.colors
    val description = stringResource(R.string.brand_mark_description)
    val shape = RoundedCornerShape(size.corner)
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Image
        },
        horizontalArrangement = Arrangement.spacedBy(size.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.houseman_logo),
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            modifier = Modifier.height(size.houseman).clip(shape).border(1.dp, c.hairline, shape),
        )
        Box(Modifier.width(1.dp).height(size.houseman).background(c.hairline))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Image(
                painterResource(R.drawable.seamphony_logo),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(size.seamphony),
            )
            if (size == BrandMarkSize.Large) {
                Text(
                    stringResource(R.string.brand_tagline),
                    color = c.ink,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.12.em,
                    maxLines = 1,
                )
            }
        }
    }
}
