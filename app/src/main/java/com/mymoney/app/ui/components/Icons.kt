package com.mymoney.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.House
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.ui.theme.FinanceTheme

object AppIcons {
    val categoryIcons: Map<String, ImageVector> = linkedMapOf(
        "grocery" to Icons.Rounded.LocalGroceryStore,
        "restaurant" to Icons.Rounded.Restaurant,
        "cafe" to Icons.Rounded.LocalCafe,
        "car" to Icons.Rounded.DirectionsCar,
        "bus" to Icons.Rounded.DirectionsBus,
        "fuel" to Icons.Rounded.LocalGasStation,
        "home" to Icons.Rounded.Home,
        "bolt" to Icons.Rounded.Bolt,
        "wifi" to Icons.Rounded.Wifi,
        "phone" to Icons.Rounded.Phone,
        "health" to Icons.Rounded.LocalHospital,
        "fitness" to Icons.Rounded.FitnessCenter,
        "shopping" to Icons.Rounded.ShoppingBag,
        "devices" to Icons.Rounded.Devices,
        "movie" to Icons.Rounded.Movie,
        "games" to Icons.Rounded.SportsEsports,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "flight" to Icons.Rounded.Flight,
        "beach" to Icons.Rounded.BeachAccess,
        "school" to Icons.Rounded.School,
        "spa" to Icons.Rounded.Spa,
        "gift" to Icons.Rounded.CardGiftcard,
        "charity" to Icons.Rounded.VolunteerActivism,
        "child" to Icons.Rounded.ChildCare,
        "pets" to Icons.Rounded.Pets,
        "shield" to Icons.Rounded.Shield,
        "safety" to Icons.Rounded.HealthAndSafety,
        "repair" to Icons.Rounded.Build,
        "percent" to Icons.Rounded.Percent,
        "work" to Icons.Rounded.Work,
        "payments" to Icons.Rounded.Payments,
        "redeem" to Icons.Rounded.Redeem,
        "undo" to Icons.Rounded.Undo,
        "trending_up" to Icons.Rounded.TrendingUp,
        "money" to Icons.Rounded.AttachMoney,
        "savings" to Icons.Rounded.Savings,
        "house" to Icons.Rounded.House,
        "celebration" to Icons.Rounded.Celebration,
        "diamond" to Icons.Rounded.Diamond,
        "bank" to Icons.Rounded.AccountBalance,
        "category" to Icons.Rounded.Category,
        "more" to Icons.Rounded.MoreHoriz,
    )

    val goalIconKeys = listOf("savings", "house", "car", "flight", "beach", "safety", "school", "devices", "celebration", "diamond", "child", "gift")

    fun category(key: String?): ImageVector = categoryIcons[key] ?: Icons.Rounded.Category

    fun account(type: AccountType): ImageVector = when (type) {
        AccountType.CURRENT -> Icons.Rounded.AccountBalance
        AccountType.SAVINGS -> Icons.Rounded.Savings
        AccountType.CASH -> Icons.Rounded.AccountBalanceWallet
        AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
        AccountType.OTHER -> Icons.Rounded.Payments
    }
}

@Composable
fun paletteColor(index: Int): Color {
    val palette = FinanceTheme.colors.chartPalette
    return palette[((index % palette.size) + palette.size) % palette.size]
}
