package com.mymoney.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived ASC, createdAt ASC")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    /** Signed effect of every transaction on its source account. */
    @Query(
        """
        SELECT accountId AS accountId,
               SUM(CASE WHEN type IN ('INCOME', 'DEBT_IN') THEN amountCents ELSE -amountCents END) AS delta
        FROM transactions GROUP BY accountId
        """
    )
    fun observeOutgoingFlows(): Flow<List<AccountFlow>>

    /** Incoming side of transfers. */
    @Query(
        """
        SELECT toAccountId AS accountId, SUM(amountCents) AS delta
        FROM transactions WHERE type = 'TRANSFER' AND toAccountId IS NOT NULL
        GROUP BY toAccountId
        """
    )
    fun observeIncomingTransfers(): Flow<List<AccountFlow>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id) +
            (SELECT COUNT(*) FROM bills WHERE accountId = :id) +
            (SELECT COUNT(*) FROM debts WHERE accountId = :id) +
            (SELECT COUNT(*) FROM debt_entries WHERE accountId = :id)
        """
    )
    suspend fun transactionCount(id: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query(
        "SELECT (SELECT COUNT(*) FROM transactions WHERE categoryId = :id) + " +
            "(SELECT COUNT(*) FROM bills WHERE categoryId = :id)"
    )
    suspend fun usageCount(id: Long): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    suspend fun getBetween(start: Long, end: Long): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE initialDebtId = :debtId LIMIT 1")
    suspend fun getInitialDebtTransaction(debtId: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE billId = :billId ORDER BY date DESC")
    fun observeForBill(billId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE billId IS NOT NULL")
    fun observeBillPayments(): Flow<List<Transaction>>

    @Query("SELECT MIN(date) FROM transactions")
    suspend fun firstDate(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE debtEntryId = :entryId")
    suspend fun deleteForDebtEntry(entryId: Long)

    @Query("UPDATE transactions SET billId = NULL WHERE billId = :billId")
    suspend fun unlinkBill(billId: Long)

    @Query("UPDATE transactions SET debtEntryId = NULL WHERE debtEntryId IN (:entryIds)")
    suspend fun unlinkDebtEntries(entryIds: List<Long>)

    @Query("UPDATE transactions SET initialDebtId = NULL WHERE initialDebtId = :debtId")
    suspend fun unlinkInitialDebtTransaction(debtId: Long)

    @Query("SELECT receiptFile FROM transactions WHERE receiptFile IS NOT NULL")
    suspend fun allReceiptFiles(): List<String>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<Budget>

    @Query("SELECT * FROM budgets WHERE categoryId IS NULL LIMIT 1")
    suspend fun getOverall(): Budget?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getForCategory(categoryId: Long): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Budget>)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY archived ASC, createdAt ASC")
    fun observeGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM goals")
    suspend fun getGoals(): List<SavingsGoal>

    @Query("SELECT * FROM goal_contributions ORDER BY date DESC, id DESC")
    fun observeContributions(): Flow<List<GoalContribution>>

    @Query("SELECT * FROM goal_contributions")
    suspend fun getContributions(): List<GoalContribution>

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId")
    suspend fun contributionsFor(goalId: Long): List<GoalContribution>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(items: List<SavingsGoal>)

    @Update
    suspend fun updateGoal(goal: SavingsGoal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(c: GoalContribution): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(items: List<GoalContribution>)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId")
    suspend fun deleteContributionsFor(goalId: Long)

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM goal_contributions")
    suspend fun deleteAllContributions()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY active DESC, nextDueDate ASC")
    fun observeAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    suspend fun getAll(): List<Bill>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): Bill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: Bill): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Bill>)

    @Update
    suspend fun update(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY closed ASC, createdAt ASC")
    fun observeDebts(): Flow<List<Debt>>

    @Query("SELECT * FROM debts")
    suspend fun getDebts(): List<Debt>

    @Query("SELECT * FROM debt_entries ORDER BY date DESC, id DESC")
    fun observeEntries(): Flow<List<DebtEntry>>

    @Query("SELECT * FROM debt_entries")
    suspend fun getEntries(): List<DebtEntry>

    @Query("SELECT * FROM debt_entries WHERE debtId = :debtId ORDER BY date ASC, id ASC")
    suspend fun entriesFor(debtId: Long): List<DebtEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(items: List<Debt>)

    @Update
    suspend fun updateDebt(debt: Debt)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DebtEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(items: List<DebtEntry>)

    @Query("DELETE FROM debt_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM debt_entries WHERE debtId = :debtId")
    suspend fun deleteEntriesFor(debtId: Long)

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()

    @Query("DELETE FROM debt_entries")
    suspend fun deleteAllEntries()
}
