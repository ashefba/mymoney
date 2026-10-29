package com.mymoney.app.core

import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.BillFrequency
import com.mymoney.app.data.db.Debt
import com.mymoney.app.data.db.DebtEntry
import com.mymoney.app.data.db.DebtEntryKind
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// ---------------------------------------------------------------------------------------------
// Bills
// ---------------------------------------------------------------------------------------------

object BillSchedule {
    fun next(date: LocalDate, frequency: BillFrequency, anchorDay: Int): LocalDate = when (frequency) {
        BillFrequency.WEEKLY -> date.plusDays(7)
        BillFrequency.BIWEEKLY -> date.plusDays(14)
        BillFrequency.MONTHLY -> clampDay(date.plusMonths(1), anchorDay)
        BillFrequency.QUARTERLY -> clampDay(date.plusMonths(3), anchorDay)
        BillFrequency.YEARLY -> clampDay(date.plusYears(1), anchorDay)
    }

    private fun clampDay(date: LocalDate, anchorDay: Int): LocalDate {
        val ym = YearMonth.from(date)
        return ym.atDay(anchorDay.coerceIn(1, ym.lengthOfMonth()))
    }

    /**
     * Unpaid occurrences of [bill] up to and including [to]. Starts from the bill's next due date,
     * so overdue occurrences are included. Occurrences before [from] are skipped unless overdue
     * inclusion is requested.
     */
    fun occurrences(bill: Bill, from: LocalDate, to: LocalDate, includeOverdue: Boolean = true): List<LocalDate> {
        if (!bill.active) return emptyList()
        val result = mutableListOf<LocalDate>()
        var d = Dates.of(bill.nextDueDate)
        var guard = 0
        while (!d.isAfter(to) && guard < 1000) {
            if (includeOverdue || !d.isBefore(from)) result += d
            d = next(d, bill.frequency, bill.anchorDay)
            guard++
        }
        return result
    }

    fun monthlyEquivalent(amountCents: Long, frequency: BillFrequency): Long {
        val a = BigDecimal.valueOf(amountCents)
        val monthly = when (frequency) {
            BillFrequency.WEEKLY -> a.multiply(BigDecimal(52)).divide(BigDecimal(12), 0, RoundingMode.HALF_EVEN)
            BillFrequency.BIWEEKLY -> a.multiply(BigDecimal(26)).divide(BigDecimal(12), 0, RoundingMode.HALF_EVEN)
            BillFrequency.MONTHLY -> a
            BillFrequency.QUARTERLY -> a.divide(BigDecimal(3), 0, RoundingMode.HALF_EVEN)
            BillFrequency.YEARLY -> a.divide(BigDecimal(12), 0, RoundingMode.HALF_EVEN)
        }
        return monthly.toLong()
    }
}

// ---------------------------------------------------------------------------------------------
// Safe to spend
// ---------------------------------------------------------------------------------------------

data class SafeToSpend(
    val periodEnd: LocalDate,
    val daysRemaining: Int,
    val spendableBalance: Long,
    val upcomingBills: Long,
    val savingsReserve: Long,
    val moneyAvailable: Long,
    val budgetRemaining: Long?,
    val safeTotal: Long,
    val dailyAllowance: Long,
    val limitedByBudget: Boolean,
    val untilPayday: Boolean,
)

object SafeToSpendCalculator {
    /** Last day (inclusive) of the current spending period. */
    fun periodEnd(today: LocalDate, paydayDayOfMonth: Int): LocalDate {
        if (paydayDayOfMonth !in 1..31) return YearMonth.from(today).atEndOfMonth()
        val thisMonth = YearMonth.from(today)
        val paydayThisMonth = thisMonth.atDay(paydayDayOfMonth.coerceAtMost(thisMonth.lengthOfMonth()))
        val nextPayday = if (paydayThisMonth.isAfter(today)) {
            paydayThisMonth
        } else {
            val nm = thisMonth.plusMonths(1)
            nm.atDay(paydayDayOfMonth.coerceAtMost(nm.lengthOfMonth()))
        }
        return nextPayday.minusDays(1)
    }

    /**
     * @param spendableBalance sum of balances of accounts marked as spendable.
     * @param bills all bills; unpaid occurrences until the period end are reserved.
     * @param plannedMonthlySavings how much the user wants to put aside every month.
     * @param savedThisMonth net amount already moved from spendable to non-spendable accounts this month.
     * @param overallBudget overall monthly budget, or null if none.
     * @param expensesThisMonth expenses recorded so far in the current month.
     */
    fun calculate(
        today: LocalDate,
        paydayDayOfMonth: Int,
        spendableBalance: Long,
        bills: List<Bill>,
        plannedMonthlySavings: Long,
        savedThisMonth: Long,
        overallBudget: Long?,
        expensesThisMonth: Long,
    ): SafeToSpend {
        val end = periodEnd(today, paydayDayOfMonth)
        val days = (Dates.daysBetween(today, end) + 1).toInt().coerceAtLeast(1)
        val upcomingBills = bills.sumOf { b -> BillSchedule.occurrences(b, today, end).size * b.amountCents }
        val reserve = (plannedMonthlySavings - savedThisMonth).coerceAtLeast(0)
        val moneyAvailable = spendableBalance - upcomingBills - reserve

        val budgetRemaining = overallBudget?.let { budget ->
            val monthEnd = YearMonth.from(today).atEndOfMonth()
            val billsThisMonth = bills.sumOf { b -> BillSchedule.occurrences(b, today, monthEnd).size * b.amountCents }
            budget - expensesThisMonth - billsThisMonth
        }
        val limitedByBudget = budgetRemaining != null && budgetRemaining < moneyAvailable
        val safe = if (limitedByBudget) budgetRemaining!! else moneyAvailable
        val daily = Money.divideFloor(safe.coerceAtLeast(0), days.toLong())
        return SafeToSpend(
            periodEnd = end,
            daysRemaining = days,
            spendableBalance = spendableBalance,
            upcomingBills = upcomingBills,
            savingsReserve = reserve,
            moneyAvailable = moneyAvailable,
            budgetRemaining = budgetRemaining,
            safeTotal = safe,
            dailyAllowance = daily,
            limitedByBudget = limitedByBudget,
            untilPayday = paydayDayOfMonth in 1..31,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Savings goals
// ---------------------------------------------------------------------------------------------

data class GoalProgress(
    val saved: Long,
    val target: Long,
    val remaining: Long,
    val fraction: Float,
    val requiredMonthly: Long?,
    val monthsLeft: Long?,
    val averageMonthly: Long,
    val estimatedCompletion: LocalDate?,
    val reached: Boolean,
    val onTrack: Boolean?,
)

object GoalCalculator {
    fun saved(goal: SavingsGoal, contributions: List<GoalContribution>): Long =
        goal.initialSavedCents + contributions.filter { it.goalId == goal.id }.sumOf { it.amountCents }

    fun progress(goal: SavingsGoal, contributions: List<GoalContribution>, today: LocalDate): GoalProgress {
        val own = contributions.filter { it.goalId == goal.id }
        val saved = goal.initialSavedCents + own.sumOf { it.amountCents }
        val remaining = (goal.targetCents - saved).coerceAtLeast(0)
        val reached = remaining == 0L && goal.targetCents > 0

        val target = goal.targetDate?.let { Dates.of(it) }
        val monthsLeft = target?.let { Dates.monthsUntil(today, it) }
        val required = if (remaining > 0 && monthsLeft != null) Money.divideCeil(remaining, monthsLeft) else null

        val created = Instant.ofEpochMilli(goal.createdAt).atZone(Dates.zone()).toLocalDate()
        val windowStart = maxOf(today.minusDays(90), created)
        val windowDays = Dates.daysBetween(windowStart, today).coerceAtLeast(0)
        val windowMonths = ((windowDays + 29) / 30).coerceAtLeast(1)
        val net = own.filter { !Dates.of(it.date).isBefore(windowStart) && !Dates.of(it.date).isAfter(today) }
            .sumOf { it.amountCents }
        val average = Money.divideFloor(net, windowMonths)

        val estimate = when {
            reached -> today
            average > 0 -> today.plusMonths(Money.divideCeil(remaining, average))
            else -> null
        }
        val onTrack = if (reached) true else if (required != null) average >= required else null
        return GoalProgress(
            saved = saved,
            target = goal.targetCents,
            remaining = remaining,
            fraction = Money.fraction(saved, goal.targetCents),
            requiredMonthly = required,
            monthsLeft = monthsLeft,
            averageMonthly = average,
            estimatedCompletion = estimate,
            reached = reached,
            onTrack = onTrack,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Debts
// ---------------------------------------------------------------------------------------------

data class PayoffProjection(
    val months: Int,
    val payoffDate: LocalDate,
    val totalInterest: Long,
    /** Balance at the start and after every month. */
    val balances: List<Long>,
)

data class DebtStatus(
    val outstanding: Long,
    val principalRepaid: Long,
    val interestPaid: Long,
    val increases: Long,
    val lastInterestDate: LocalDate,
    val accruedInterest: Long,
    val fraction: Float,
    val projection: PayoffProjection?,
    val projectionProblem: String?,
    val requiredMonthlyForTarget: Long?,
)

object DebtCalculator {
    private val mc = MathContext.DECIMAL128

    /** Simple daily interest (actual/365) on [balance] between two dates, rounded to the cent. */
    fun accruedInterest(balance: Long, annualRateBps: Int, from: LocalDate, to: LocalDate): Long {
        if (balance <= 0 || annualRateBps <= 0 || !to.isAfter(from)) return 0
        val days = Dates.daysBetween(from, to)
        return BigDecimal.valueOf(balance)
            .multiply(BigDecimal.valueOf(annualRateBps.toLong()))
            .multiply(BigDecimal.valueOf(days))
            .divide(BigDecimal.valueOf(10_000L * 365L), 0, RoundingMode.HALF_EVEN)
            .toLong()
    }

    fun monthlyInterest(balance: Long, annualRateBps: Int): Long {
        if (balance <= 0 || annualRateBps <= 0) return 0
        return BigDecimal.valueOf(balance)
            .multiply(BigDecimal.valueOf(annualRateBps.toLong()))
            .divide(BigDecimal.valueOf(120_000L), 0, RoundingMode.HALF_EVEN)
            .toLong()
    }

    /** Splits a repayment into (principal, interest): accrued interest is paid first. */
    fun suggestSplit(payment: Long, balance: Long, annualRateBps: Int, lastDate: LocalDate, date: LocalDate): Pair<Long, Long> {
        val interest = minOf(payment, accruedInterest(balance, annualRateBps, lastDate, date))
        return (payment - interest) to interest
    }

    /**
     * Interest accrued since the latest repayment, applying each dated balance increase only from
     * its own date instead of retroactively to the whole period.
     */
    fun accruedSinceLastRepayment(debt: Debt, entries: List<DebtEntry>, to: LocalDate): Long {
        val own = entries.filter { it.debtId == debt.id }.sortedWith(compareBy({ it.date }, { it.id }))
        val lastRepaymentDate = own
            .filter { it.kind == DebtEntryKind.REPAYMENT && !Dates.of(it.date).isAfter(to) }
            .maxOfOrNull { it.date }
            ?.let(Dates::of)
            ?: Dates.of(debt.startDate)
        var balance = debt.principalCents
        own.filter { !Dates.of(it.date).isAfter(lastRepaymentDate) }.forEach { entry ->
            balance += if (entry.kind == DebtEntryKind.INCREASE) entry.principalCents else -entry.principalCents
        }
        balance = balance.coerceAtLeast(0)
        var cursor = lastRepaymentDate
        var interest = 0L
        own.filter {
            it.kind == DebtEntryKind.INCREASE &&
                Dates.of(it.date).isAfter(lastRepaymentDate) &&
                !Dates.of(it.date).isAfter(to)
        }.forEach { increase ->
            val date = Dates.of(increase.date)
            interest += accruedInterest(balance, debt.annualRateBps, cursor, date)
            balance += increase.principalCents
            cursor = date
        }
        return interest + accruedInterest(balance, debt.annualRateBps, cursor, to)
    }

    fun suggestSplit(payment: Long, debt: Debt, entries: List<DebtEntry>, date: LocalDate): Pair<Long, Long> {
        val interest = minOf(payment, accruedSinceLastRepayment(debt, entries, date))
        return (payment - interest) to interest
    }

    fun status(debt: Debt, entries: List<DebtEntry>, today: LocalDate): DebtStatus {
        val own = entries.filter { it.debtId == debt.id }.sortedWith(compareBy({ it.date }, { it.id }))
        val effective = own.filter { !Dates.of(it.date).isAfter(today) }
        val increases = effective.filter { it.kind == DebtEntryKind.INCREASE }.sumOf { it.principalCents }
        val repayments = effective.filter { it.kind == DebtEntryKind.REPAYMENT }
        val principalRepaid = repayments.sumOf { it.principalCents }
        val interestPaid = repayments.sumOf { it.interestCents }
        val outstanding = (debt.principalCents + increases - principalRepaid).coerceAtLeast(0)
        val lastDate = repayments.maxOfOrNull { it.date }?.let { Dates.of(it) } ?: Dates.of(debt.startDate)
        val accrued = accruedSinceLastRepayment(debt, effective, today)
        val totalOwedEver = debt.principalCents + increases

        var projection: PayoffProjection? = null
        var problem: String? = null
        if (outstanding > 0) {
            if (debt.monthlyPaymentCents <= 0) {
                problem = "Set a monthly repayment to see when this will be paid off."
            } else {
                projection = project(outstanding, debt.annualRateBps, debt.monthlyPaymentCents, today)
                if (projection == null) problem = "The monthly repayment does not cover the interest, so the balance never goes down."
            }
        }
        val requiredForTarget = debt.targetPayoffDate?.let { t ->
            if (outstanding <= 0) null
            else requiredPayment(outstanding, debt.annualRateBps, Dates.monthsUntil(today, Dates.of(t)).toInt())
        }
        return DebtStatus(
            outstanding = outstanding,
            principalRepaid = principalRepaid,
            interestPaid = interestPaid,
            increases = increases,
            lastInterestDate = lastDate,
            accruedInterest = accrued,
            fraction = Money.fraction(totalOwedEver - outstanding, totalOwedEver),
            projection = projection,
            projectionProblem = problem,
            requiredMonthlyForTarget = requiredForTarget,
        )
    }

    /** Month-by-month amortization. Returns null if the payment never reduces the balance. */
    fun project(balance: Long, annualRateBps: Int, monthlyPayment: Long, start: LocalDate, maxMonths: Int = 1200): PayoffProjection? {
        if (balance <= 0) return PayoffProjection(0, start, 0, listOf(0))
        if (monthlyPayment <= 0) return null
        var b = balance
        var totalInterest = 0L
        val balances = mutableListOf(b)
        var month = 0
        while (b > 0 && month < maxMonths) {
            val interest = monthlyInterest(b, annualRateBps)
            if (monthlyPayment <= interest) return null
            totalInterest += interest
            b = (b + interest - monthlyPayment).coerceAtLeast(0)
            balances += b
            month++
        }
        if (b > 0) return null
        return PayoffProjection(month, start.plusMonths(month.toLong()), totalInterest, balances)
    }

    /** Fixed monthly payment that clears [balance] in [months] (standard annuity formula). */
    fun requiredPayment(balance: Long, annualRateBps: Int, months: Int): Long {
        val n = months.coerceAtLeast(1)
        if (annualRateBps <= 0) return Money.divideCeil(balance, n.toLong())
        val r = BigDecimal.valueOf(annualRateBps.toLong()).divide(BigDecimal.valueOf(120_000L), mc)
        val onePlusRPowN = BigDecimal.ONE.add(r).pow(n, mc)
        val payment = BigDecimal.valueOf(balance).multiply(r).multiply(onePlusRPowN)
            .divide(onePlusRPowN.subtract(BigDecimal.ONE), mc)
        return payment.setScale(0, RoundingMode.CEILING).toLong()
    }
}

// ---------------------------------------------------------------------------------------------
// Forecasting
// ---------------------------------------------------------------------------------------------

object Forecast {
    /**
     * Projects a balance month by month. Index 0 is the starting balance. Interest (if any) is
     * compounded monthly on positive balances before adding the monthly amount.
     */
    fun project(start: Long, monthlyAmount: Long, months: Int, annualReturnBps: Int = 0): List<Long> {
        val result = ArrayList<Long>(months + 1)
        var b = start
        result += b
        repeat(months) {
            b += DebtCalculator.monthlyInterest(b, annualReturnBps) + monthlyAmount
            result += b
        }
        return result
    }

    /** Months needed to grow [current] to [target], or null if it never gets there (within 100 years). */
    fun monthsToReach(target: Long, current: Long, monthlyAmount: Long, annualReturnBps: Int = 0): Int? {
        if (current >= target) return 0
        if (monthlyAmount <= 0 && (annualReturnBps <= 0 || current <= 0)) return null
        var b = current
        var m = 0
        while (b < target && m < 1200) {
            b += DebtCalculator.monthlyInterest(b, annualReturnBps) + monthlyAmount
            m++
        }
        return if (b >= target) m else null
    }

    /** Applies a whole-percent reduction (10 = 10 %) to an amount. */
    fun reduceBy(amount: Long, percent: Int): Long =
        BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf((100 - percent).toLong()))
            .divide(BigDecimal(100), 0, RoundingMode.HALF_EVEN).toLong()
}
