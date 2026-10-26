package one.lab5.kleidi.tenant.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.lab5.kleidi.tenant.R
import one.lab5.kleidi.tenant.api.models.Tenant
import one.lab5.kleidi.tenant.features.home.HomeScreen
import one.lab5.kleidi.tenant.features.placeholder.ComingSoonScreen
import one.lab5.kleidi.tenant.features.profile.ProfileScreen
import one.lab5.kleidi.tenant.ui.TenantTheme

/** The four bottom tabs: Home · Payments · Issues · Profile. */
enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Payments(R.string.tab_payments, Icons.Outlined.Payments),
    Issues(R.string.tab_issues, Icons.Outlined.Build),
    Profile(R.string.tab_profile, Icons.Outlined.Person),
}

@Composable
fun MainScaffold(tenant: Tenant) {
    var selected by rememberSaveable { mutableStateOf(Tab.Home) }
    val colors = TenantTheme.colors

    Scaffold(
        containerColor = colors.ground,
        bottomBar = {
            NavigationBar(containerColor = colors.ground, tonalElevation = 0.dp) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.accent,
                            selectedTextColor = colors.accent,
                            indicatorColor = colors.accentSoft,
                            unselectedIconColor = colors.secondaryText,
                            unselectedTextColor = colors.secondaryText,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                Tab.Home -> HomeScreen(tenant, onReportIssue = { selected = Tab.Issues })
                Tab.Payments -> ComingSoonScreen(R.string.tab_payments, R.string.placeholder_payments_message)
                Tab.Issues -> ComingSoonScreen(R.string.tab_issues, R.string.placeholder_issues_message)
                Tab.Profile -> ProfileScreen(tenant)
            }
        }
    }
}
