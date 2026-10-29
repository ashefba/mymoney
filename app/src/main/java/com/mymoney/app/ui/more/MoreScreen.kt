package com.mymoney.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.InfoNote
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.paletteColor

enum class MoreDestination(val title: String, val subtitle: String, val icon: ImageVector) {
    ACCOUNTS("Accounts", "Balances, opening amounts and transfers", Icons.Rounded.AccountBalanceWallet),
    BILLS("Bills & subscriptions", "Upcoming payments, calendar and reminders", Icons.AutoMirrored.Rounded.ReceiptLong),
    DEBTS("Debts & loans", "What you owe and what you're owed", Icons.Rounded.AccountBalance),
    PLANNER("Planning", "Forecasts and what-if calculators", Icons.Rounded.Insights),
    REPORTS("Reports", "Weekly, monthly and yearly summaries, PDF & CSV", Icons.Rounded.QueryStats),
    CATEGORIES("Categories", "Default and custom categories", Icons.Rounded.Category),
    SETTINGS("Settings", "Appearance, app lock, backup and data", Icons.Rounded.Settings),
}

@Composable
fun MoreScreen(onOpen: (MoreDestination) -> Unit) {
    val groups = listOf(
        "Money" to listOf(MoreDestination.ACCOUNTS, MoreDestination.BILLS, MoreDestination.DEBTS),
        "Insights" to listOf(MoreDestination.PLANNER, MoreDestination.REPORTS),
        "App" to listOf(MoreDestination.CATEGORIES, MoreDestination.SETTINGS),
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("More", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp)) }
        groups.forEach { (title, items) ->
            item { SectionHeader(title) }
            item {
                AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items.forEach { d ->
                        ListRow(
                            title = d.title,
                            subtitle = d.subtitle,
                            icon = d.icon,
                            iconTint = paletteColor(d.ordinal),
                            trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { onOpen(d) },
                        )
                    }
                }
            }
        }
        item {
            InfoNote("Everything stays on this phone. MyMoney works fully offline and never asks for internet access.", Modifier.padding(top = 8.dp))
        }
    }
}
