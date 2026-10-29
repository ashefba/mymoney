package com.mymoney.app.ui.transactions

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.PaymentMethod
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditTransactionViewModel(
    container: AppContainer,
    private val transactionId: Long,
    initialType: TransactionType,
) : ViewModel() {
    private val repo = container.repository
    private val settingsRepo = container.settings
    val receipts = container.receipts

    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<Account>> = repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var loaded by mutableStateOf(false)
        private set
    var currency by mutableStateOf("EUR")
        private set
    var type by mutableStateOf(initialType)
        private set
    var amountText by mutableStateOf("")
    var date by mutableStateOf(Dates.todayEpoch())
    var categoryId by mutableStateOf<Long?>(null)
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var paymentMethod by mutableStateOf(PaymentMethod.CARD)
    var description by mutableStateOf("")
    var notes by mutableStateOf("")
    var receiptFile by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var finished by mutableStateOf(false)
        private set
    var processingReceipt by mutableStateOf(false)
        private set

    /** Debt-linked movements are managed from the Debts screen to keep both sides consistent. */
    var readOnlyReason by mutableStateOf<String?>(null)
        private set

    private var original: Transaction? = null
    private var saved = false
    val isNew: Boolean get() = transactionId == 0L

    init {
        viewModelScope.launch {
            val settings = settingsRepo.current()
            currency = settings.currency
            if (transactionId != 0L) {
                repo.getTransaction(transactionId)?.let { t ->
                    original = t
                    type = t.type
                    amountText = Money.toInput(t.amountCents)
                    date = t.date
                    categoryId = t.categoryId
                    accountId = t.accountId
                    toAccountId = t.toAccountId
                    paymentMethod = t.paymentMethod
                    description = t.description
                    notes = t.notes
                    receiptFile = t.receiptFile
                    if (t.type == TransactionType.DEBT_IN || t.type == TransactionType.DEBT_OUT || t.debtEntryId != null) {
                        readOnlyReason = "This entry was created from a debt repayment. Edit or delete it from More → Debts so the loan balance stays correct."
                    } else if (t.billId != null) {
                        readOnlyReason = "This entry was created by marking a bill as paid. It is locked so the bill schedule and payment history stay consistent."
                    }
                }
            } else {
                val accs = repo.accounts.first().filter { !it.archived }
                accountId = accs.firstOrNull { it.id == settings.defaultAccountId }?.id ?: accs.firstOrNull()?.id
            }
            loaded = true
        }
    }

    fun changeType(newType: TransactionType) {
        if (newType == type) return
        type = newType
        val cats = categories.value
        val currentKind = categoryId?.let { id -> cats.firstOrNull { it.id == id }?.kind }
        val wantedKind = if (newType == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        if (currentKind != wantedKind) categoryId = null
        if (newType == TransactionType.TRANSFER) paymentMethod = PaymentMethod.BANK_TRANSFER
    }

    fun attachReceipt(uri: Uri) {
        viewModelScope.launch {
            processingReceipt = true
            val name = withContext(Dispatchers.IO) { runCatching { receipts.saveFromUri(uri) }.getOrNull() }
            processingReceipt = false
            if (name == null) {
                error = "That image couldn't be read. Try another photo."
                return@launch
            }
            discardTemporaryReceipt()
            receiptFile = name
        }
    }

    fun removeReceipt() {
        discardTemporaryReceipt()
        receiptFile = null
    }

    private fun discardTemporaryReceipt() {
        val current = receiptFile
        if (current != null && current != original?.receiptFile) receipts.delete(current)
    }

    fun save() {
        if (readOnlyReason != null) return
        val amount = Money.parseToCents(amountText)
        val from = accountId
        error = when {
            amount == null || amount <= 0 -> "Enter an amount greater than zero."
            from == null -> "Choose an account. You can add one in More → Accounts."
            type == TransactionType.TRANSFER && toAccountId == null -> "Choose the account the money goes to."
            type == TransactionType.TRANSFER && toAccountId == from -> "Choose two different accounts for a transfer."
            else -> null
        }
        if (error != null || amount == null || from == null) return

        viewModelScope.launch {
            val cats = categories.value
            val resolvedCategory = when (type) {
                TransactionType.TRANSFER -> null
                TransactionType.INCOME -> categoryId ?: cats.firstOrNull { it.kind == CategoryKind.INCOME && it.iconKey == "money" }?.id
                else -> categoryId ?: cats.firstOrNull { it.kind == CategoryKind.EXPENSE && it.iconKey == "more" }?.id
            }
            val base = original ?: Transaction(type = type, amountCents = amount, date = date, accountId = from)
            val tx = base.copy(
                type = type,
                amountCents = amount,
                date = date,
                categoryId = resolvedCategory,
                accountId = from,
                toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
                paymentMethod = paymentMethod,
                description = description.trim(),
                notes = notes.trim(),
                receiptFile = receiptFile,
            )
            repo.saveTransaction(tx, previousReceipt = original?.receiptFile)
            settingsRepo.setDefaultAccount(from)
            saved = true
            finished = true
        }
    }

    fun delete() {
        val t = original ?: return
        viewModelScope.launch {
            repo.deleteTransaction(t)
            discardTemporaryReceipt()
            saved = true
            finished = true
        }
    }

    override fun onCleared() {
        if (!saved) discardTemporaryReceipt()
        super.onCleared()
    }
}
