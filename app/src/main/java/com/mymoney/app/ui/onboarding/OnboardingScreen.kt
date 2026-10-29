package com.mymoney.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AccountType
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AmountField
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.SelectField
import com.mymoney.app.ui.theme.FinanceTheme
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    var step by mutableIntStateOf(0)
    var currency by mutableStateOf("EUR")
    var accountName by mutableStateOf("Current account")
    var balanceText by mutableStateOf("")
    var balanceNegative by mutableStateOf(false)
    var addCash by mutableStateOf(false)
    var cashText by mutableStateOf("")
    var addSavings by mutableStateOf(false)
    var savingsText by mutableStateOf("")
    var payday by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    private fun parseOptional(text: String): Long? = if (text.isBlank()) 0L else Money.parseToCents(text)

    fun finish() {
        val balance = parseOptional(balanceText)
        val cash = if (addCash) parseOptional(cashText) else 0L
        val savings = if (addSavings) parseOptional(savingsText) else 0L
        error = when {
            accountName.isBlank() -> "Give your account a name."
            balance == null -> "Enter your current balance like 1250.00"
            cash == null -> "Enter the cash amount like 40.00"
            savings == null -> "Enter your savings balance like 3000.00"
            else -> null
        }
        if (error != null || balance == null || cash == null || savings == null || saving) return
        saving = true
        viewModelScope.launch {
            val repo = container.repository
            val settings = container.settings
            repo.ensureDefaults()
            settings.setCurrency(currency)
            settings.setPayday(payday)
            val mainId = repo.saveAccount(
                Account(name = accountName.trim(), type = AccountType.CURRENT,
                    openingBalanceCents = if (balanceNegative) -balance else balance, colorIndex = 0),
            )
            if (addCash) repo.saveAccount(Account(name = "Cash", type = AccountType.CASH, openingBalanceCents = cash, colorIndex = 3))
            if (addSavings) repo.saveAccount(Account(name = "Savings", type = AccountType.SAVINGS, openingBalanceCents = savings, colorIndex = 1))
            settings.setDefaultAccount(mainId)
            settings.setOnboardingDone(true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val vm = appViewModel { OnboardingViewModel(it) }
    val fc = FinanceTheme.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (vm.step == 0) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(fc.heroGradient).padding(24.dp),
                ) {
                    Column {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = fc.onHero, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Welcome to MyMoney", style = MaterialTheme.typography.headlineMedium, color = fc.onHero, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("See where your money goes, know what's safe to spend, and reach your savings goals.",
                            style = MaterialTheme.typography.bodyLarge, color = fc.onHero.copy(alpha = 0.9f))
                    }
                }
                AppCard {
                    Promise(Icons.Rounded.WifiOff, "Fully offline", "No internet permission, no account, no ads. Your data stays on this phone.")
                    Spacer(Modifier.height(12.dp))
                    Promise(Icons.Rounded.Lock, "Private by design", "Optional fingerprint or PIN lock and password-encrypted backups.")
                }
                Text("Currency", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Money.supportedCurrencies.forEach { c ->
                        FilterChip(selected = vm.currency == c, onClick = { vm.currency = c }, label = { Text("$c ${Money.symbol(c)}") })
                    }
                }
                Button(onClick = { vm.step = 1 }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Get started", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Set up your money", style = MaterialTheme.typography.headlineMedium)
                Text("Start with what you have today. You can add more accounts and change everything later.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppCard {
                    Text("Main account", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        vm.accountName, { if (it.length <= 30) vm.accountName = it }, label = { Text("Account name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Spacer(Modifier.height(8.dp))
                    AmountField(vm.balanceText, { vm.balanceText = it }, "Current balance", vm.currency)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("My account is overdrawn", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = vm.balanceNegative, onCheckedChange = { vm.balanceNegative = it })
                    }
                }
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("I also carry cash", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = vm.addCash, onCheckedChange = { vm.addCash = it })
                    }
                    if (vm.addCash) AmountField(vm.cashText, { vm.cashText = it }, "Cash in wallet", vm.currency)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("I have a savings account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = vm.addSavings, onCheckedChange = { vm.addSavings = it })
                    }
                    if (vm.addSavings) AmountField(vm.savingsText, { vm.savingsText = it }, "Savings balance", vm.currency)
                }
                AppCard {
                    Text("Payday", style = MaterialTheme.typography.titleMedium)
                    Text("Your daily safe-to-spend amount is planned until the day before your next payday.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SelectField("When does your income arrive?", (0..31).toList(), vm.payday,
                        { if (it == 0) "Plan to the end of the month" else "On day $it of the month" }, { vm.payday = it })
                }
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::finish, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Start using MyMoney", fontWeight = FontWeight.SemiBold)
                }
                Text("Have a backup? Finish setup, then go to More → Settings → Restore from backup.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
