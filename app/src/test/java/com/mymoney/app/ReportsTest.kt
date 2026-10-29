package com.mymoney.app

import com.mymoney.app.core.ReportBuilder
import com.mymoney.app.core.ReportPeriod
import com.mymoney.app.data.db.Budget
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReportsTest {
    private val day = LocalDate.of(2026, 3, 10)
    private val food = Category(id = 1, name = "Food", kind = CategoryKind.EXPENSE, iconKey = "grocery", colorIndex = 0)
    private val salary = Category(id = 2, name = "Salary", kind = CategoryKind.INCOME, iconKey = "work", colorIndex = 1)

    private fun tx(type: TransactionType, amount: Long, date: LocalDate = day, category: Long? = null) =
        Transaction(type = type, amountCents = amount, date = date.toEpochDay(), categoryId = category, accountId = 1, toAccountId = if (type == TransactionType.TRANSFER) 2 else null)

    @Test
    fun onlyIncomeAndExpensesCount() {
        val txs = listOf(
            tx(TransactionType.INCOME, 300_000, category = 2),
            tx(TransactionType.EXPENSE, 45_000, category = 1),
            tx(TransactionType.TRANSFER, 50_000),
            tx(TransactionType.DEBT_OUT, 20_000),
            tx(TransactionType.DEBT_IN, 100_000),
            tx(TransactionType.EXPENSE, 10_000, date = day.minusMonths(1), category = 1),
        )
        val r = ReportBuilder.build(ReportPeriod.MONTH, day, txs, listOf(food, salary), listOf(Budget(categoryId = 1, amountCents = 40_000)))
        assertEquals(300_000L, r.income)
        assertEquals(45_000L, r.expense)
        assertEquals(255_000L, r.net)
        assertEquals(BigDecimal("85.0"), r.savingsRate)
        assertEquals(10_000L, r.previousExpense)
        assertEquals(45_000L, r.expenseByCategory.single().total)
        assertEquals(10_000L, r.expenseByCategory.single().previousTotal)
        assertEquals(1, r.budgetLines.size)
        assertEquals(45_000L, r.budgetLines.single().spent)
        assertEquals(2, r.transactions.size)
        assertEquals(6, r.trend.size)
    }

    @Test
    fun weekAndYearRanges() {
        val week = ReportBuilder.build(ReportPeriod.WEEK, day, emptyList(), emptyList(), emptyList())
        assertEquals(LocalDate.of(2026, 3, 9), week.range.start)
        assertEquals(7, week.trend.size)
        val year = ReportBuilder.build(ReportPeriod.YEAR, day, emptyList(), emptyList(), emptyList())
        assertEquals(12, year.trend.size)
        assertEquals(LocalDate.of(2026, 12, 31), year.range.end)
    }
}
