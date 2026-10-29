package com.mymoney.app

import com.mymoney.app.core.Money
import com.mymoney.app.core.Rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun parsesCommonFormats() {
        assertEquals(1200L, Money.parseToCents("12"))
        assertEquals(1250L, Money.parseToCents("12.5"))
        assertEquals(1250L, Money.parseToCents("12,50"))
        assertEquals(123456L, Money.parseToCents("1.234,56"))
        assertEquals(123456L, Money.parseToCents("1,234.56"))
        assertEquals(123400L, Money.parseToCents("1.234"))
        assertEquals(1999L, Money.parseToCents("€ 19.99"))
        assertEquals(5L, Money.parseToCents("0.05"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("-5"))
        assertNull(Money.parseToCents("1.2.3.4"))
        assertNull(Money.parseToCents("1.2345"))
    }

    @Test
    fun noFloatingPointDrift() {
        // 0.1 + 0.2 is the classic float trap; in cents it is exact.
        val a = Money.parseToCents("0.10")!!
        val b = Money.parseToCents("0.20")!!
        assertEquals(30L, a + b)
        assertEquals("0.30", Money.toInput(a + b))
    }

    @Test
    fun divisionRoundsTheRightWay() {
        assertEquals(333L, Money.divideFloor(1000, 3))
        assertEquals(334L, Money.divideCeil(1000, 3))
        assertEquals(-334L, Money.divideFloor(-1000, 3))
    }

    @Test
    fun percentages() {
        assertEquals(BigDecimal("33.3"), Money.percent(1, 3))
        assertNull(Money.percent(1, 0))
        assertEquals("25%", Money.percentText(Money.percent(25, 100)))
        assertEquals(0.5f, Money.fraction(50, 100))
        assertEquals(1f, Money.fraction(150, 100))
    }

    @Test
    fun ratesInBasisPoints() {
        assertEquals(525, Rates.parseToBps("5.25"))
        assertEquals(525, Rates.parseToBps("5,25 %"))
        assertEquals(0, Rates.parseToBps(""))
        assertNull(Rates.parseToBps("101"))
        assertEquals("5.25", Rates.toInput(525))
    }
}
