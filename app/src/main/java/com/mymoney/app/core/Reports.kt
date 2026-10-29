package com.mymoney.app.core

import com.mymoney.app.data.db.Budget
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class ReportPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class PeriodRange(val start: LocalDate, val end: LocalDate, val label: String) {
    val startEpoch: Long get() = start.toEpochDay()
    val endEpoch: Long get() = end.toEpochDay()
    fun contains(epochDay: Long) = epochDay in startEpoch..endEpoch
    val days: Long get() = Dates.daysBetween(start, end) + 1
}

object Periods {
    fun range(period: ReportPeriod, anchor: LocalDate): PeriodRange = when (period) {
        ReportPeriod.WEEK -> {
            val s = Dates.weekStart(anchor)
            val e = s.plusDays(6)
            PeriodRange(s, e, "${Dates.short(s.toEpochDay())} – ${Dates.short(e.toEpochDay())} ${e.year}")
        }
        ReportPeriod.MONTH -> {
            val ym = YearMonth.from(anchor)
            PeriodRange(ym.atDay(1), ym.atEndOfMonth(), Dates.month(ym))
        }
        ReportPeriod.YEAR -> PeriodRange(LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31), anchor.year.toString())
    }

    fun shift(period: ReportPeriod, anchor: LocalDate, by: Long): LocalDate = when (period) {
        ReportPeriod.WEEK -> anchor.plusWeeks(by)
        ReportPeriod.MONTH -> anchor.plusMonths(by)
        ReportPeriod.YEAR -> anchor.plusYears(by)
    }
}

data class CategoryAmount(
    val categoryId: Long?,
    val name: String,
    val colorIndex: Int,
    val iconKey: String,
    val total: Long,
    val previousTotal: Long,
)

data class TrendPoint(val label: String, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

data class BudgetLine(val name: String, val budget: Long, val spent: Long)

data class ReportData(
    val period: ReportPeriod,
    val range: PeriodRange,
    val income: Long,
    val expense: Long,
    val previousIncome: Long,
    val previousExpense: Long,
    val expenseByCategory: List<CategoryAmount>,
    val incomeByCategory: List<CategoryAmount>,
    val trend: List<TrendPoint>,
    val budgetLines: List<BudgetLine>,
    val transactions: List<Transaction>,
    val averageDailySpend: Long,
) {
    val net: Long get() = income - expense
    val savingsRate: BigDecimal? get() = if (income > 0) Money.percent(net, income) else null
}

/** Sums of income and expenses only. Transfers and debt movements are never income or spending. */
fun List<Transaction>.incomeTotal(): Long = filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
fun List<Transaction>.expenseTotal(): Long = filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }

object ReportBuilder {
    fun build(
        period: ReportPeriod,
        anchor: LocalDate,
        all: List<Transaction>,
        categories: List<Category>,
        budgets: List<Budget>,
    ): ReportData {
        val range = Periods.range(period, anchor)
        val prev = Periods.range(period, Periods.shift(period, anchor, -1))
        val inRange = all.filter { range.contains(it.date) }
        val inPrev = all.filter { prev.contains(it.date) }
        val catMap = categories.associateBy { it.id }

        fun breakdown(type: TransactionType): List<CategoryAmount> {
            val now = inRange.filter { it.type == type }.groupBy { it.categoryId }
            val before = inPrev.filter { it.type == type }.groupBy { it.categoryId }
            return (now.keys + before.keys).distinct().map { id ->
                val c = id?.let { catMap[it] }
                CategoryAmount(
                    categoryId = id,
                    name = c?.name ?: "Uncategorised",
                    colorIndex = c?.colorIndex ?: 0,
                    iconKey = c?.iconKey ?: "more",
                    total = now[id]?.sumOf { it.amountCents } ?: 0L,
                    previousTotal = before[id]?.sumOf { it.amountCents } ?: 0L,
                )
            }.filter { it.total > 0 || it.previousTotal > 0 }.sortedByDescending { it.total }
        }

        val trend: List<TrendPoint> = when (period) {
            ReportPeriod.WEEK -> (0L..6L).map { i ->
                val d = range.start.plusDays(i)
                val txs = all.filter { it.date == d.toEpochDay() }
                TrendPoint(Dates.dayName(d), txs.incomeTotal(), txs.expenseTotal())
            }
            ReportPeriod.MONTH -> (5L downTo 0L).map { back ->
                val ym = YearMonth.from(anchor).minusMonths(back)
                val txs = all.filter { it.date in Dates.monthStart(ym)..Dates.monthEnd(ym) }
                TrendPoint(Dates.shortMonth(ym), txs.incomeTotal(), txs.expenseTotal())
            }
            ReportPeriod.YEAR -> (1..12).map { m ->
                val ym = YearMonth.of(anchor.year, m)
                val txs = all.filter { it.date in Dates.monthStart(ym)..Dates.monthEnd(ym) }
                TrendPoint(Dates.shortMonth(ym), txs.incomeTotal(), txs.expenseTotal())
            }
        }

        val expenseByCat = breakdown(TransactionType.EXPENSE)
        val budgetLines = if (period == ReportPeriod.MONTH) {
            val months = 1L
            budgets.mapNotNull { b ->
                if (b.categoryId == null) {
                    BudgetLine("Overall budget", b.amountCents * months, inRange.expenseTotal())
                } else {
                    val c = catMap[b.categoryId] ?: return@mapNotNull null
                    BudgetLine(c.name, b.amountCents * months, expenseByCat.firstOrNull { it.categoryId == c.id }?.total ?: 0L)
                }
            }.sortedWith(compareBy({ it.name != "Overall budget" }, { it.name }))
        } else emptyList()

        val today = Dates.today()
        val elapsedDays = when {
            today.isBefore(range.start) -> 0L
            today.isAfter(range.end) -> range.days
            else -> Dates.daysBetween(range.start, today) + 1
        }
        val expense = inRange.expenseTotal()
        return ReportData(
            period = period,
            range = range,
            income = inRange.incomeTotal(),
            expense = expense,
            previousIncome = inPrev.incomeTotal(),
            previousExpense = inPrev.expenseTotal(),
            expenseByCategory = expenseByCat,
            incomeByCategory = breakdown(TransactionType.INCOME),
            trend = trend,
            budgetLines = budgetLines,
            transactions = inRange.filter { it.type == TransactionType.INCOME || it.type == TransactionType.EXPENSE },
            averageDailySpend = if (elapsedDays > 0) Money.divideFloor(expense, elapsedDays) else 0L,
        )
    }
}
