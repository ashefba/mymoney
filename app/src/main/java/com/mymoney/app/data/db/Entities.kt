package com.mymoney.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class AccountType(val label: String, val description: String) {
    CURRENT("Current account", "Everyday bank account"),
    SAVINGS("Savings account", "Money you put aside"),
    CASH("Cash wallet", "Physical cash"),
    CREDIT_CARD("Credit card", "Spending you pay back later"),
    OTHER("Other", "Any other place you keep money");

    /**
     * Whether this account counts towards safe-to-spend by default. Credit cards are included so
     * that what you owe on them is subtracted from the money you can spend.
     */
    val spendableByDefault: Boolean get() = this == CURRENT || this == CASH || this == CREDIT_CARD
}

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    /** Money leaving an account towards a debt (repaying principal or lending money). Not spending. */
    DEBT_OUT,
    /** Money entering an account from a debt (borrowing or being repaid). Not income. */
    DEBT_IN,
}

enum class CategoryKind { EXPENSE, INCOME }

enum class PaymentMethod(val label: String) {
    CARD("Card"),
    CASH("Cash"),
    BANK_TRANSFER("Bank transfer"),
    DIRECT_DEBIT("Direct debit"),
    MOBILE("Mobile payment"),
    OTHER("Other"),
}

enum class BillFrequency(val label: String) {
    WEEKLY("Weekly"),
    BIWEEKLY("Every 2 weeks"),
    MONTHLY("Monthly"),
    QUARTERLY("Every 3 months"),
    YEARLY("Yearly"),
}

enum class DebtKind(val label: String, val description: String) {
    LOAN("Loan", "Money you owe to a bank or lender"),
    BORROWED("Borrowed", "Money you borrowed from someone"),
    LENT("Lent", "Money someone owes you");

    val iOwe: Boolean get() = this != LENT
}

enum class DebtEntryKind {
    /** A repayment, split into principal and interest. */
    REPAYMENT,
    /** The amount owed increased (more borrowed or lent, or interest added to the balance). */
    INCREASE,
}

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalanceCents: Long,
    val colorIndex: Int = 0,
    val includeInSpendable: Boolean = type.spendableByDefault,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val colorIndex: Int,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
        Index("billId"),
        Index("debtEntryId"),
        Index("initialDebtId"),
    ],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    /** Always positive; the [type] decides the direction. */
    val amountCents: Long,
    val date: Long,
    val categoryId: Long? = null,
    val accountId: Long,
    val toAccountId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val description: String = "",
    val notes: String = "",
    /** File name inside the app's private receipts folder. */
    val receiptFile: String? = null,
    val billId: Long? = null,
    val debtEntryId: Long? = null,
    /** Set only on the optional account movement created with a new debt. */
    val initialDebtId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "budgets", indices = [Index("categoryId")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null means the overall monthly budget. */
    val categoryId: Long?,
    val amountCents: Long,
)

@Serializable
@Entity(tableName = "goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    /** Amount already saved when the goal was created. */
    val initialSavedCents: Long,
    val targetDate: Long?,
    val iconKey: String = "savings",
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Money set aside for a goal. Contributions are allocations of money that already sits in
 * your accounts, so they never count as income or spending. Optionally a transfer between two
 * accounts is recorded at the same time (e.g. current account to savings account).
 */
@Serializable
@Entity(tableName = "goal_contributions", indices = [Index("goalId")])
data class GoalContribution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** Positive for a contribution, negative for a withdrawal. */
    val amountCents: Long,
    val date: Long,
    val note: String = "",
    val transferTransactionId: Long? = null,
)

@Serializable
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val frequency: BillFrequency,
    val nextDueDate: Long,
    /** Day of month the bill was originally due on, so 31st-of-month bills stay at month end. */
    val anchorDay: Int,
    val categoryId: Long?,
    val accountId: Long?,
    val paymentMethod: PaymentMethod = PaymentMethod.DIRECT_DEBIT,
    val reminderDaysBefore: Int = 2,
    val isSubscription: Boolean = false,
    val active: Boolean = true,
    val notes: String = "",
)

@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: DebtKind,
    val counterparty: String = "",
    val principalCents: Long,
    /** Annual interest rate in basis points (525 = 5.25 %). */
    val annualRateBps: Int = 0,
    val startDate: Long,
    val monthlyPaymentCents: Long = 0,
    val targetPayoffDate: Long? = null,
    val accountId: Long? = null,
    val notes: String = "",
    val closed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "debt_entries", indices = [Index("debtId")])
data class DebtEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val kind: DebtEntryKind,
    val date: Long,
    val principalCents: Long,
    val interestCents: Long = 0,
    val accountId: Long? = null,
    val note: String = "",
)

data class AccountFlow(
    val accountId: Long,
    val delta: Long,
)

data class CategoryTotal(
    val categoryId: Long?,
    val total: Long,
)
