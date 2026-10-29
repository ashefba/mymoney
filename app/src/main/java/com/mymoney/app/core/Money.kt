package com.mymoney.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * All money in MyMoney is stored as a [Long] number of minor units (cents).
 * Floating point is never used for stored amounts or for arithmetic on them.
 */
object Money {
    private const val MAX_CENTS = 999_999_999_999L

    val supportedCurrencies = listOf("EUR", "USD", "GBP", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK")

    fun toBigDecimal(cents: Long): BigDecimal = BigDecimal.valueOf(cents, 2)

    fun fromBigDecimal(value: BigDecimal, rounding: RoundingMode = RoundingMode.HALF_EVEN): Long =
        value.setScale(2, rounding).unscaledValue().toLong()

    /**
     * Parses user input such as "12", "12.5", "12,50", "1.234,56" or "1,234.56" into cents.
     * Returns null for invalid input, negative values or more than two decimals.
     */
    fun parseToCents(raw: String): Long? {
        var s = raw.trim()
            .replace("€", "").replace("$", "").replace("£", "")
            .replace(" ", "").replace("\u00A0", "").replace("'", "")
        if (s.isEmpty()) return null
        if (s.startsWith("-")) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 -> {
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            }
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (s.count { it == ',' } == 1 && decimals in 0..2) s.replace(',', '.')
                else s.replace(",", "")
            }
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                if (s.count { it == '.' } == 1 && decimals in 0..2) s
                else if (decimals == 3) s.replace(".", "")
                else return null
            }
            else -> s
        }
        if (s.endsWith(".")) s = s.dropLast(1)
        if (s.isEmpty() || s.count { it == '.' } > 1 || s.any { !it.isDigit() && it != '.' }) return null
        return try {
            val bd = BigDecimal(s)
            if (bd.scale() > 2) return null
            val cents = bd.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            if (cents > MAX_CENTS) null else cents
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Plain editable representation, e.g. 123456 -> "1234.56". */
    fun toInput(cents: Long): String = toBigDecimal(cents).toPlainString()

    fun format(
        cents: Long,
        currencyCode: String = "EUR",
        showPlus: Boolean = false,
        compact: Boolean = false,
    ): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        nf.currency = runCatching { Currency.getInstance(currencyCode) }.getOrDefault(Currency.getInstance("EUR"))
        if (compact && kotlin.math.abs(cents) >= 100_000) {
            nf.maximumFractionDigits = 0
            nf.minimumFractionDigits = 0
        }
        val text = nf.format(toBigDecimal(cents))
        return if (showPlus && cents > 0) "+$text" else text
    }

    fun symbol(currencyCode: String): String =
        runCatching { Currency.getInstance(currencyCode).getSymbol(Locale.getDefault()) }.getOrDefault("€")

    /** Percentage (0..100+) with one decimal, computed with BigDecimal. */
    fun percent(part: Long, whole: Long): BigDecimal? {
        if (whole == 0L) return null
        return BigDecimal.valueOf(part).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP)
    }

    /** Fraction in 0f..1f for progress indicators only (display, never stored). */
    fun fraction(part: Long, whole: Long): Float {
        if (whole <= 0L) return 0f
        val f = BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP).toFloat()
        return f.coerceIn(0f, 1f)
    }

    /** Divides an amount by an integer count, rounding down to the cent. */
    fun divideFloor(cents: Long, by: Long): Long {
        if (by <= 0) return cents
        return Math.floorDiv(cents, by)
    }

    /** Divides an amount by an integer count, rounding up to the cent. */
    fun divideCeil(cents: Long, by: Long): Long {
        if (by <= 0) return cents
        return -Math.floorDiv(-cents, by)
    }

    fun percentText(value: BigDecimal?): String = value?.let { "${it.stripTrailingZerosSafe()}%" } ?: "–"

    private fun BigDecimal.stripTrailingZerosSafe(): String {
        val s = setScale(1, RoundingMode.HALF_UP).toPlainString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}

/** Basis points helpers for interest rates: 525 bps = 5.25 %. */
object Rates {
    fun parseToBps(raw: String): Int? {
        val s = raw.replace("%", "").replace(',', '.').trim()
        if (s.isEmpty()) return 0
        return try {
            val bd = BigDecimal(s)
            if (bd.signum() < 0 || bd > BigDecimal(100)) null
            else bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun toInput(bps: Int): String = BigDecimal.valueOf(bps.toLong(), 2).stripTrailingZeros().toPlainString()

    fun format(bps: Int): String = "${toInput(bps)}%"
}
