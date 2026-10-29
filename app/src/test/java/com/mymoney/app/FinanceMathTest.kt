package com.mymoney.app

import com.mymoney.app.core.BillSchedule
import com.mymoney.app.core.DebtCalculator
import com.mymoney.app.core.Forecast
import com.mymoney.app.core.GoalCalculator
import com.mymoney.app.core.SafeToSpendCalculator
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.BillFrequency
import com.mymoney.app.data.db.Debt
import com.mymoney.app.data.db.DebtEntry
import com.mymoney.app.data.db.DebtEntryKind
import com.mymoney.app.data.db.DebtKind
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BillScheduleTest {
    private fun bill(due: LocalDate, freq: BillFrequency, amount: Long = 1000, anchor: Int = due.dayOfMonth) =
        Bill(id = 1, name = "Rent", amountCents = amount, frequency = freq, nextDueDate = due.toEpochDay(),
            anchorDay = anchor, categoryId = null, accountId = null)

    @Test
    fun monthlyBillsKeepMonthEnd() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = BillSchedule.next(jan31, BillFrequency.MONTHLY, 31)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), BillSchedule.next(feb, BillFrequency.MONTHLY, 31))
    }

    @Test
    fun occurrencesIncludeOverdueAndRepeats() {
        val b = bill(LocalDate.of(2026, 3, 1), BillFrequency.WEEKLY)
        val list = BillSchedule.occurrences(b, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 22))
        assertEquals(listOf(1, 8, 15, 22), list.map { it.dayOfMonth })
        val noOverdue = BillSchedule.occurrences(b, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 22), includeOverdue = false)
        assertEquals(listOf(15, 22), noOverdue.map { it.dayOfMonth })
    }

    @Test
    fun inactiveBillsHaveNoOccurrences() {
        val b = bill(LocalDate.of(2026, 3, 1), BillFrequency.MONTHLY).copy(active = false)
        assertTrue(BillSchedule.occurrences(b, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 1)).isEmpty())
    }

    @Test
    fun monthlyEquivalent() {
        assertEquals(4333L, BillSchedule.monthlyEquivalent(1000, BillFrequency.WEEKLY))
        assertEquals(1000L, BillSchedule.monthlyEquivalent(12000, BillFrequency.YEARLY))
        assertEquals(1000L, BillSchedule.monthlyEquivalent(3000, BillFrequency.QUARTERLY))
    }
}

class SafeToSpendTest {
    private val today = LocalDate.of(2026, 3, 10)

    @Test
    fun periodEndsDayBeforePayday() {
        assertEquals(LocalDate.of(2026, 3, 24), SafeToSpendCalculator.periodEnd(today, 25))
        assertEquals(LocalDate.of(2026, 4, 4), SafeToSpendCalculator.periodEnd(today, 5))
        assertEquals(LocalDate.of(2026, 3, 31), SafeToSpendCalculator.periodEnd(today, 0))
        // Payday on the 31st in a 30-day month falls on the 30th.
        assertEquals(LocalDate.of(2026, 4, 29), SafeToSpendCalculator.periodEnd(LocalDate.of(2026, 4, 10), 31))
    }

    @Test
    fun reservesBillsAndPlannedSavings() {
        val rent = Bill(name = "Rent", amountCents = 80_000, frequency = BillFrequency.MONTHLY,
            nextDueDate = LocalDate.of(2026, 3, 15).toEpochDay(), anchorDay = 15, categoryId = null, accountId = null)
        val r = SafeToSpendCalculator.calculate(
            today = today, paydayDayOfMonth = 0, spendableBalance = 200_000, bills = listOf(rent),
            plannedMonthlySavings = 30_000, savedThisMonth = 10_000, overallBudget = null, expensesThisMonth = 0,
        )
        assertEquals(22, r.daysRemaining)
        assertEquals(80_000L, r.upcomingBills)
        assertEquals(20_000L, r.savingsReserve)
        assertEquals(100_000L, r.safeTotal)
        assertEquals(4545L, r.dailyAllowance) // 1000.00 / 22 rounded down
        assertFalse(r.limitedByBudget)
    }

    @Test
    fun budgetCanLimitSpending() {
        val r = SafeToSpendCalculator.calculate(
            today = today, paydayDayOfMonth = 0, spendableBalance = 500_000, bills = emptyList(),
            plannedMonthlySavings = 0, savedThisMonth = 0, overallBudget = 150_000, expensesThisMonth = 100_000,
        )
        assertTrue(r.limitedByBudget)
        assertEquals(50_000L, r.safeTotal)
    }

    @Test
    fun negativeMoneyGivesZeroDaily() {
        val r = SafeToSpendCalculator.calculate(
            today = today, paydayDayOfMonth = 0, spendableBalance = -5_000, bills = emptyList(),
            plannedMonthlySavings = 0, savedThisMonth = 0, overallBudget = null, expensesThisMonth = 0,
        )
        assertEquals(-5_000L, r.safeTotal)
        assertEquals(0L, r.dailyAllowance)
    }
}

class GoalCalculatorTest {
    private val today = LocalDate.of(2026, 3, 10)
    private fun millis(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun progressAndRequiredMonthly() {
        val goal = SavingsGoal(id = 7, name = "Holiday", targetCents = 120_000, initialSavedCents = 20_000,
            targetDate = LocalDate.of(2026, 8, 10).toEpochDay(), createdAt = millis(LocalDate.of(2026, 1, 1)))
        val contributions = listOf(
            GoalContribution(goalId = 7, amountCents = 30_000, date = LocalDate.of(2026, 2, 1).toEpochDay()),
            GoalContribution(goalId = 7, amountCents = -10_000, date = LocalDate.of(2026, 3, 1).toEpochDay()),
            GoalContribution(goalId = 99, amountCents = 999_999, date = LocalDate.of(2026, 3, 1).toEpochDay()),
        )
        val p = GoalCalculator.progress(goal, contributions, today)
        assertEquals(40_000L, p.saved)
        assertEquals(80_000L, p.remaining)
        assertEquals(5L, p.monthsLeft)
        assertEquals(16_000L, p.requiredMonthly)
        assertFalse(p.reached)
        assertNotNull(p.estimatedCompletion)
    }

    @Test
    fun reachedGoal() {
        val goal = SavingsGoal(id = 1, name = "Fund", targetCents = 10_000, initialSavedCents = 10_000, targetDate = null)
        val p = GoalCalculator.progress(goal, emptyList(), today)
        assertTrue(p.reached)
        assertEquals(1f, p.fraction)
        assertNull(p.requiredMonthly)
    }
}

class DebtCalculatorTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun interestFreeRepaymentsReduceBalance() {
        val debt = Debt(id = 1, name = "Alex", kind = DebtKind.BORROWED, principalCents = 100_000, startDate = start.toEpochDay(),
            monthlyPaymentCents = 25_000)
        val entries = listOf(
            DebtEntry(debtId = 1, kind = DebtEntryKind.REPAYMENT, date = start.plusMonths(1).toEpochDay(), principalCents = 25_000),
            DebtEntry(debtId = 1, kind = DebtEntryKind.INCREASE, date = start.plusMonths(1).toEpochDay(), principalCents = 5_000),
        )
        val s = DebtCalculator.status(debt, entries, start.plusMonths(2))
        assertEquals(80_000L, s.outstanding)
        assertEquals(25_000L, s.principalRepaid)
        assertEquals(4, s.projection!!.months)
        assertEquals(0L, s.projection!!.totalInterest)
    }

    @Test
    fun interestIsPaidBeforePrincipal() {
        // 10 000.00 at 3.65 % for 30 days = 30.00 interest (ACT/365).
        val (principal, interest) = DebtCalculator.suggestSplit(50_000, 1_000_000, 365, start, start.plusDays(30))
        assertEquals(3_000L, interest)
        assertEquals(47_000L, principal)
    }

    @Test
    fun addedPrincipalOnlyAccruesFromItsOwnDate() {
        val debt = Debt(
            id = 1, name = "Loan", kind = DebtKind.LOAN, principalCents = 1_000_000,
            annualRateBps = 365, startDate = start.toEpochDay(),
        )
        val entries = listOf(
            DebtEntry(
                debtId = 1, kind = DebtEntryKind.INCREASE, date = start.plusDays(20).toEpochDay(),
                principalCents = 1_000_000,
            )
        )
        // First 20 days: 20.00, then 10 days on the doubled balance: 20.00.
        assertEquals(4_000L, DebtCalculator.accruedSinceLastRepayment(debt, entries, start.plusDays(30)))
        val (principal, interest) = DebtCalculator.suggestSplit(10_000, debt, entries, start.plusDays(30))
        assertEquals(4_000L, interest)
        assertEquals(6_000L, principal)
    }

    @Test
    fun paymentBelowInterestNeverPaysOff() {
        assertNull(DebtCalculator.project(1_000_000, 1200, 5_000, start)) // 1 % a month = 100.00 > 50.00
    }

    @Test
    fun amortizationMatchesAnnuity() {
        val payment = DebtCalculator.requiredPayment(1_000_000, 600, 12)
        assertEquals(86_067L, payment) // 10 000 at 6 % over 12 months ≈ 860.66 rounded up
        val p = DebtCalculator.project(1_000_000, 600, payment, start)!!
        assertEquals(12, p.months)
        assertEquals(0L, p.balances.last())
        assertTrue(p.totalInterest in 32_000L..33_000L)
    }
}

class ForecastTest {
    @Test
    fun linearProjection() {
        val p = Forecast.project(100_000, 20_000, 12)
        assertEquals(13, p.size)
        assertEquals(220_000L, p[6])
        assertEquals(340_000L, p[12])
    }

    @Test
    fun monthsToReachGoal() {
        assertEquals(5, Forecast.monthsToReach(100_000, 0, 20_000))
        assertEquals(0, Forecast.monthsToReach(100, 200, 0))
        assertNull(Forecast.monthsToReach(100_000, 0, 0))
        assertNull(Forecast.monthsToReach(100_000, 0, -5_000))
    }

    @Test
    fun interestHelpsGrowth() {
        val withInterest = Forecast.project(1_000_000, 0, 12, 1200)
        assertTrue(withInterest[12] > 1_120_000L) // monthly compounding beats simple 12 %
    }

    @Test
    fun reducePercent() {
        assertEquals(90_000L, Forecast.reduceBy(100_000, 10))
        assertEquals(100_000L, Forecast.reduceBy(100_000, 0))
    }
}
