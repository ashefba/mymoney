package com.mymoney.app.data.repo

import androidx.room.withTransaction
import com.mymoney.app.core.BillSchedule
import com.mymoney.app.core.Dates
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.AppDatabase
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.Budget
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.Debt
import com.mymoney.app.data.db.DebtEntry
import com.mymoney.app.data.db.DebtEntryKind
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.PaymentMethod
import com.mymoney.app.data.db.SavingsGoal
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class FinanceRepository(
    private val db: AppDatabase,
    val receipts: ReceiptStorage,
) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val txDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val goalDao = db.goalDao()
    private val billDao = db.billDao()
    private val debtDao = db.debtDao()

    // ------------------------------------------------------------------ observation

    val accounts: Flow<List<Account>> = accountDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val transactions: Flow<List<Transaction>> = txDao.observeAll()
    val budgets: Flow<List<Budget>> = budgetDao.observeAll()
    val goals: Flow<List<SavingsGoal>> = goalDao.observeGoals()
    val contributions: Flow<List<GoalContribution>> = goalDao.observeContributions()
    val bills: Flow<List<Bill>> = billDao.observeAll()
    val billPayments: Flow<List<Transaction>> = txDao.observeBillPayments()
    val debts: Flow<List<Debt>> = debtDao.observeDebts()
    val debtEntries: Flow<List<DebtEntry>> = debtDao.observeEntries()

    fun transactionsBetween(start: Long, end: Long): Flow<List<Transaction>> = txDao.observeBetween(start, end)
    fun recentTransactions(limit: Int): Flow<List<Transaction>> = txDao.observeRecent(limit)

    /** Current balance per account id: opening balance plus the signed effect of every transaction. */
    val balances: Flow<Map<Long, Long>> = combine(
        accountDao.observeAll(),
        accountDao.observeOutgoingFlows(),
        accountDao.observeIncomingTransfers(),
    ) { accounts, outgoing, incoming ->
        val deltas = HashMap<Long, Long>()
        outgoing.forEach { deltas[it.accountId] = (deltas[it.accountId] ?: 0L) + it.delta }
        incoming.forEach { deltas[it.accountId] = (deltas[it.accountId] ?: 0L) + it.delta }
        accounts.associate { it.id to it.openingBalanceCents + (deltas[it.id] ?: 0L) }
    }

    suspend fun getTransaction(id: Long): Transaction? = txDao.get(id)
    suspend fun getTransactionsBetween(start: Long, end: Long) = txDao.getBetween(start, end)
    suspend fun allTransactions() = txDao.getAll()
    suspend fun allCategories() = categoryDao.getAll()
    suspend fun allAccounts() = accountDao.getAll()
    suspend fun allBills() = billDao.getAll()
    suspend fun getBill(id: Long) = billDao.get(id)
    suspend fun firstTransactionDate(): Long? = txDao.firstDate()

    // ------------------------------------------------------------------ setup

    suspend fun ensureDefaults() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(DefaultData.categories())
    }

    // ------------------------------------------------------------------ accounts

    suspend fun saveAccount(account: Account): Long =
        if (account.id == 0L) accountDao.insert(account) else {
            accountDao.update(account); account.id
        }

    /** Deletes an unused account, otherwise archives it so history stays correct. Returns true if archived. */
    suspend fun deleteOrArchiveAccount(account: Account): Boolean {
        return if (accountDao.transactionCount(account.id) > 0) {
            accountDao.update(account.copy(archived = true)); true
        } else {
            accountDao.delete(account); false
        }
    }

    // ------------------------------------------------------------------ categories

    suspend fun saveCategory(category: Category): Long =
        if (category.id == 0L) categoryDao.insert(category) else {
            categoryDao.update(category); category.id
        }

    suspend fun deleteOrArchiveCategory(category: Category): Boolean {
        return if (categoryDao.usageCount(category.id) > 0) {
            categoryDao.update(category.copy(archived = true)); true
        } else {
            budgetDao.deleteForCategory(category.id)
            categoryDao.delete(category); false
        }
    }

    // ------------------------------------------------------------------ transactions

    suspend fun saveTransaction(tx: Transaction, previousReceipt: String? = null): Long {
        val id = if (tx.id == 0L) txDao.insert(tx) else {
            txDao.update(tx); tx.id
        }
        if (previousReceipt != null && previousReceipt != tx.receiptFile) receipts.delete(previousReceipt)
        return id
    }

    suspend fun deleteTransaction(tx: Transaction) {
        txDao.deleteById(tx.id)
        receipts.delete(tx.receiptFile)
    }

    // ------------------------------------------------------------------ budgets

    suspend fun setBudget(categoryId: Long?, amountCents: Long) {
        db.withTransaction {
            val existing = if (categoryId == null) budgetDao.getOverall() else budgetDao.getForCategory(categoryId)
            when {
                amountCents <= 0 -> existing?.let { budgetDao.deleteById(it.id) }
                existing != null -> budgetDao.insert(existing.copy(amountCents = amountCents))
                else -> budgetDao.insert(Budget(categoryId = categoryId, amountCents = amountCents))
            }
        }
    }

    // ------------------------------------------------------------------ goals

    suspend fun saveGoal(goal: SavingsGoal): Long =
        if (goal.id == 0L) goalDao.insertGoal(goal) else {
            goalDao.updateGoal(goal); goal.id
        }

    /** Deletes the goal and its allocations. Transfers that were recorded alongside stay, since the money really moved. */
    suspend fun deleteGoal(goal: SavingsGoal) = db.withTransaction {
        goalDao.deleteContributionsFor(goal.id)
        goalDao.deleteGoal(goal.id)
    }

    suspend fun addContribution(
        goalId: Long,
        amountCents: Long,
        date: Long,
        note: String,
        fromAccountId: Long?,
        toAccountId: Long?,
        goalName: String,
    ) = db.withTransaction {
        var transferId: Long? = null
        if (fromAccountId != null && toAccountId != null && fromAccountId != toAccountId && amountCents != 0L) {
            transferId = txDao.insert(
                Transaction(
                    type = TransactionType.TRANSFER,
                    amountCents = kotlin.math.abs(amountCents),
                    date = date,
                    accountId = fromAccountId,
                    toAccountId = toAccountId,
                    paymentMethod = PaymentMethod.BANK_TRANSFER,
                    description = if (amountCents > 0) "Saved for $goalName" else "Withdrawn from $goalName",
                )
            )
        }
        goalDao.insertContribution(
            GoalContribution(goalId = goalId, amountCents = amountCents, date = date, note = note, transferTransactionId = transferId)
        )
    }

    suspend fun deleteContribution(c: GoalContribution) = db.withTransaction {
        c.transferTransactionId?.let { txDao.deleteById(it) }
        goalDao.deleteContribution(c.id)
    }

    // ------------------------------------------------------------------ bills

    suspend fun saveBill(bill: Bill): Long =
        if (bill.id == 0L) billDao.insert(bill) else {
            billDao.update(bill); bill.id
        }

    suspend fun deleteBill(bill: Bill) = db.withTransaction {
        txDao.unlinkBill(bill.id)
        billDao.deleteById(bill.id)
    }

    /**
     * Records the payment of the current occurrence as an expense and moves the bill to its next
     * due date. Nothing is ever deducted automatically.
     */
    suspend fun markBillPaid(bill: Bill, amountCents: Long, date: Long, accountId: Long, method: PaymentMethod) =
        db.withTransaction {
            txDao.insert(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amountCents = amountCents,
                    date = date,
                    categoryId = bill.categoryId,
                    accountId = accountId,
                    paymentMethod = method,
                    description = bill.name,
                    billId = bill.id,
                )
            )
            advanceBill(bill)
        }

    suspend fun skipBillOccurrence(bill: Bill) = advanceBill(bill)

    private suspend fun advanceBill(bill: Bill) {
        val next = BillSchedule.next(Dates.of(bill.nextDueDate), bill.frequency, bill.anchorDay)
        billDao.update(bill.copy(nextDueDate = next.toEpochDay()))
    }

    // ------------------------------------------------------------------ debts

    suspend fun saveDebt(debt: Debt, recordInitialInAccount: Boolean): Long = db.withTransaction {
        val isNew = debt.id == 0L
        val id = if (isNew) debtDao.insertDebt(debt) else {
            debtDao.updateDebt(debt); debt.id
        }
        if (isNew && recordInitialInAccount && debt.accountId != null && debt.principalCents > 0) {
            txDao.insert(
                Transaction(
                    type = if (debt.kind.iOwe) TransactionType.DEBT_IN else TransactionType.DEBT_OUT,
                    amountCents = debt.principalCents,
                    date = debt.startDate,
                    accountId = debt.accountId,
                    paymentMethod = PaymentMethod.BANK_TRANSFER,
                    description = if (debt.kind.iOwe) "Borrowed: ${debt.name}" else "Lent: ${debt.name}",
                    initialDebtId = id,
                )
            )
        } else if (!isNew) {
            // If this debt originally moved money into/out of an account, keep that movement in
            // sync when the user corrects the principal, date, kind, account or name.
            txDao.getInitialDebtTransaction(id)?.let { initial ->
                val linkedAccount = debt.accountId
                if (linkedAccount != null) {
                    txDao.update(
                        initial.copy(
                            type = if (debt.kind.iOwe) TransactionType.DEBT_IN else TransactionType.DEBT_OUT,
                            amountCents = debt.principalCents,
                            date = debt.startDate,
                            accountId = linkedAccount,
                            description = if (debt.kind.iOwe) "Borrowed: ${debt.name}" else "Lent: ${debt.name}",
                        )
                    )
                }
            }
        }
        id
    }

    suspend fun addDebtRepayment(
        debt: Debt,
        date: Long,
        principalCents: Long,
        interestCents: Long,
        accountId: Long?,
        note: String,
    ) = db.withTransaction {
        val entryId = debtDao.insertEntry(
            DebtEntry(
                debtId = debt.id,
                kind = DebtEntryKind.REPAYMENT,
                date = date,
                principalCents = principalCents,
                interestCents = interestCents,
                accountId = accountId,
                note = note,
            )
        )
        if (accountId != null) {
            val cats = categoryDao.getAll()
            if (principalCents > 0) {
                txDao.insert(
                    Transaction(
                        type = if (debt.kind.iOwe) TransactionType.DEBT_OUT else TransactionType.DEBT_IN,
                        amountCents = principalCents,
                        date = date,
                        accountId = accountId,
                        paymentMethod = PaymentMethod.BANK_TRANSFER,
                        description = if (debt.kind.iOwe) "Repayment: ${debt.name}" else "Repaid to me: ${debt.name}",
                        debtEntryId = entryId,
                    )
                )
            }
            if (interestCents > 0) {
                // Interest is a real cost (or real income when you lent money), unlike principal.
                val category = if (debt.kind.iOwe) {
                    cats.firstOrNull { it.kind == CategoryKind.EXPENSE && it.iconKey == DefaultData.INTEREST_EXPENSE_ICON }
                } else {
                    cats.firstOrNull { it.kind == CategoryKind.INCOME && it.iconKey == DefaultData.INTEREST_INCOME_ICON }
                }
                txDao.insert(
                    Transaction(
                        type = if (debt.kind.iOwe) TransactionType.EXPENSE else TransactionType.INCOME,
                        amountCents = interestCents,
                        date = date,
                        categoryId = category?.id,
                        accountId = accountId,
                        paymentMethod = PaymentMethod.BANK_TRANSFER,
                        description = "Interest: ${debt.name}",
                        debtEntryId = entryId,
                    )
                )
            }
        }
        entryId
    }

    suspend fun addDebtIncrease(debt: Debt, date: Long, amountCents: Long, accountId: Long?, note: String) =
        db.withTransaction {
            val entryId = debtDao.insertEntry(
                DebtEntry(
                    debtId = debt.id,
                    kind = DebtEntryKind.INCREASE,
                    date = date,
                    principalCents = amountCents,
                    accountId = accountId,
                    note = note,
                )
            )
            if (accountId != null) {
                txDao.insert(
                    Transaction(
                        type = if (debt.kind.iOwe) TransactionType.DEBT_IN else TransactionType.DEBT_OUT,
                        amountCents = amountCents,
                        date = date,
                        accountId = accountId,
                        paymentMethod = PaymentMethod.BANK_TRANSFER,
                        description = if (debt.kind.iOwe) "Borrowed more: ${debt.name}" else "Lent more: ${debt.name}",
                        debtEntryId = entryId,
                    )
                )
            }
            entryId
        }

    suspend fun deleteDebtEntry(entry: DebtEntry) = db.withTransaction {
        txDao.deleteForDebtEntry(entry.id)
        debtDao.deleteEntry(entry.id)
    }

    /** Removes the debt and its history. Account movements already recorded are kept but unlinked. */
    suspend fun deleteDebt(debt: Debt) = db.withTransaction {
        val entryIds = debtDao.entriesFor(debt.id).map { it.id }
        if (entryIds.isNotEmpty()) txDao.unlinkDebtEntries(entryIds)
        txDao.unlinkInitialDebtTransaction(debt.id)
        debtDao.deleteEntriesFor(debt.id)
        debtDao.deleteDebt(debt.id)
    }

    suspend fun setDebtClosed(debt: Debt, closed: Boolean) = debtDao.updateDebt(debt.copy(closed = closed))

    // ------------------------------------------------------------------ backup / wipe

    suspend fun snapshot(): DataSnapshot = db.withTransaction {
        DataSnapshot(
            accounts = accountDao.getAll(),
            categories = categoryDao.getAll(),
            transactions = txDao.getAll(),
            budgets = budgetDao.getAll(),
            goals = goalDao.getGoals(),
            contributions = goalDao.getContributions(),
            bills = billDao.getAll(),
            debts = debtDao.getDebts(),
            debtEntries = debtDao.getEntries(),
        )
    }

    suspend fun replaceAll(data: DataSnapshot) = db.withTransaction {
        clearTables()
        accountDao.insertAll(data.accounts)
        categoryDao.insertAll(data.categories)
        txDao.insertAll(data.transactions)
        budgetDao.insertAll(data.budgets)
        goalDao.insertGoals(data.goals)
        goalDao.insertContributions(data.contributions)
        billDao.insertAll(data.bills)
        debtDao.insertDebts(data.debts)
        debtDao.insertEntries(data.debtEntries)
    }

    suspend fun wipeEverything() {
        db.withTransaction { clearTables() }
        receipts.deleteAll()
        ensureDefaults()
    }

    private suspend fun clearTables() {
        txDao.deleteAll()
        budgetDao.deleteAll()
        goalDao.deleteAllContributions()
        goalDao.deleteAllGoals()
        billDao.deleteAll()
        debtDao.deleteAllEntries()
        debtDao.deleteAllDebts()
        categoryDao.deleteAll()
        accountDao.deleteAll()
    }

    suspend fun referencedReceipts(): Set<String> = txDao.allReceiptFiles().toSet()
}

data class DataSnapshot(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val goals: List<SavingsGoal>,
    val contributions: List<GoalContribution>,
    val bills: List<Bill>,
    val debts: List<Debt>,
    val debtEntries: List<DebtEntry>,
)
