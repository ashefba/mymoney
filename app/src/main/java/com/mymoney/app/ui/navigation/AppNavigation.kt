package com.mymoney.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mymoney.app.data.db.TransactionType
import com.mymoney.app.ui.accounts.AccountsScreen
import com.mymoney.app.ui.accounts.EditAccountScreen
import com.mymoney.app.ui.bills.BillsScreen
import com.mymoney.app.ui.bills.EditBillScreen
import com.mymoney.app.ui.budgets.BudgetsScreen
import com.mymoney.app.ui.categories.CategoriesScreen
import com.mymoney.app.ui.debts.DebtDetailScreen
import com.mymoney.app.ui.debts.DebtsScreen
import com.mymoney.app.ui.debts.EditDebtScreen
import com.mymoney.app.ui.home.HomeScreen
import com.mymoney.app.ui.more.MoreDestination
import com.mymoney.app.ui.more.MoreScreen
import com.mymoney.app.ui.planner.PlannerScreen
import com.mymoney.app.ui.reports.ReportsScreen
import com.mymoney.app.ui.savings.EditGoalScreen
import com.mymoney.app.ui.savings.GoalDetailScreen
import com.mymoney.app.ui.savings.SavingsScreen
import com.mymoney.app.ui.settings.SettingsScreen
import com.mymoney.app.ui.transactions.EditTransactionScreen
import com.mymoney.app.ui.transactions.TransactionsScreen

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val SAVINGS = "savings"
    const val MORE = "more"
    const val EDIT_TX = "tx-edit?id={id}&type={type}"
    const val ACCOUNTS = "accounts"
    const val EDIT_ACCOUNT = "account-edit?id={id}"
    const val GOAL = "goal/{id}"
    const val EDIT_GOAL = "goal-edit?id={id}"
    const val BILLS = "bills"
    const val EDIT_BILL = "bill-edit?id={id}"
    const val DEBTS = "debts"
    const val DEBT = "debt/{id}"
    const val EDIT_DEBT = "debt-edit?id={id}"
    const val PLANNER = "planner"
    const val REPORTS = "reports"
    const val CATEGORIES = "categories"
    const val SETTINGS = "settings"

    fun editTx(id: Long = 0, type: TransactionType = TransactionType.EXPENSE) = "tx-edit?id=$id&type=${type.name}"
    fun editAccount(id: Long = 0) = "account-edit?id=$id"
    fun goal(id: Long) = "goal/$id"
    fun editGoal(id: Long = 0) = "goal-edit?id=$id"
    fun editBill(id: Long = 0) = "bill-edit?id=$id"
    fun debt(id: Long) = "debt/$id"
    fun editDebt(id: Long = 0) = "debt-edit?id=$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab(Routes.BUDGETS, "Budgets", Icons.Rounded.PieChart),
    Tab(Routes.SAVINGS, "Savings", Icons.Rounded.Savings),
    Tab(Routes.MORE, "More", Icons.Rounded.Menu),
)

private val idArg = navArgument("id") { type = NavType.LongType; defaultValue = 0L }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    fun openTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBar) {
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.editTx()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add transaction", modifier = Modifier.size(30.dp))
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddTransaction = { nav.navigate(Routes.editTx()) },
                    onOpenTransaction = { nav.navigate(Routes.editTx(it)) },
                    onSeeAllTransactions = { openTab(Routes.TRANSACTIONS) },
                    onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                    onOpenBills = { nav.navigate(Routes.BILLS) },
                    onOpenBudgets = { openTab(Routes.BUDGETS) },
                    onOpenGoal = { nav.navigate(Routes.goal(it)) },
                    onOpenSavings = { openTab(Routes.SAVINGS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(onAdd = { nav.navigate(Routes.editTx()) }, onOpen = { nav.navigate(Routes.editTx(it)) })
            }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.SAVINGS) {
                SavingsScreen(onOpenGoal = { nav.navigate(Routes.goal(it)) }, onNewGoal = { nav.navigate(Routes.editGoal()) })
            }
            composable(Routes.MORE) {
                MoreScreen { d ->
                    nav.navigate(
                        when (d) {
                            MoreDestination.ACCOUNTS -> Routes.ACCOUNTS
                            MoreDestination.BILLS -> Routes.BILLS
                            MoreDestination.DEBTS -> Routes.DEBTS
                            MoreDestination.PLANNER -> Routes.PLANNER
                            MoreDestination.REPORTS -> Routes.REPORTS
                            MoreDestination.CATEGORIES -> Routes.CATEGORIES
                            MoreDestination.SETTINGS -> Routes.SETTINGS
                        },
                    )
                }
            }
            composable(
                Routes.EDIT_TX,
                arguments = listOf(idArg, navArgument("type") { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val type = entry.arguments?.getString("type")
                    ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() } ?: TransactionType.EXPENSE
                EditTransactionScreen(transactionId = id, initialType = type, onDone = { nav.popBackStack() })
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editAccount(it)) },
                    onTransfer = { nav.navigate(Routes.editTx(type = TransactionType.TRANSFER)) },
                )
            }
            composable(Routes.EDIT_ACCOUNT, arguments = listOf(idArg)) { entry ->
                EditAccountScreen(accountId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(Routes.GOAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                GoalDetailScreen(
                    goalId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editGoal(it)) },
                )
            }
            composable(Routes.EDIT_GOAL, arguments = listOf(idArg)) { entry ->
                EditGoalScreen(goalId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(Routes.BILLS) {
                BillsScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.editBill(it)) })
            }
            composable(Routes.EDIT_BILL, arguments = listOf(idArg)) { entry ->
                EditBillScreen(billId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(Routes.DEBTS) {
                DebtsScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(Routes.debt(it)) },
                    onNew = { nav.navigate(Routes.editDebt()) },
                )
            }
            composable(Routes.DEBT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                DebtDetailScreen(
                    debtId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editDebt(it)) },
                )
            }
            composable(Routes.EDIT_DEBT, arguments = listOf(idArg)) { entry ->
                EditDebtScreen(debtId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(Routes.PLANNER) { PlannerScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.REPORTS) { ReportsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
