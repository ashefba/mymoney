package com.mymoney.app.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.core.ReportData
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Creates CSV and PDF files entirely on the device using Android's built-in PdfDocument. */
class ReportExporter(private val context: Context) {

    private fun open(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Could not open the selected file.")

    private fun csv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains(';')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun plainAmount(cents: Long): String = Money.toBigDecimal(cents).toPlainString()

    private fun typeLabel(t: TransactionType): String = when (t) {
        TransactionType.EXPENSE -> "Expense"
        TransactionType.INCOME -> "Income"
        TransactionType.TRANSFER -> "Transfer"
        TransactionType.DEBT_OUT -> "Debt payment out"
        TransactionType.DEBT_IN -> "Debt money in"
    }

    suspend fun writeTransactionsCsv(
        uri: Uri,
        transactions: List<Transaction>,
        categories: List<Category>,
        accounts: List<Account>,
        currency: String,
    ) = withContext(Dispatchers.IO) {
        val cat = categories.associateBy { it.id }
        val acc = accounts.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("Date,Type,Amount ($currency),Signed amount,Category,Account,To account,Payment method,Description,Notes\n")
        transactions.sortedBy { it.date }.forEach { t ->
            val signed = when (t.type) {
                TransactionType.INCOME, TransactionType.DEBT_IN -> t.amountCents
                TransactionType.EXPENSE, TransactionType.DEBT_OUT -> -t.amountCents
                TransactionType.TRANSFER -> 0L
            }
            sb.append(Dates.iso(Dates.of(t.date))).append(',')
                .append(typeLabel(t.type)).append(',')
                .append(plainAmount(t.amountCents)).append(',')
                .append(plainAmount(signed)).append(',')
                .append(csv(t.categoryId?.let { cat[it]?.name } ?: "")).append(',')
                .append(csv(acc[t.accountId]?.name ?: "")).append(',')
                .append(csv(t.toAccountId?.let { acc[it]?.name } ?: "")).append(',')
                .append(csv(t.paymentMethod.label)).append(',')
                .append(csv(t.description)).append(',')
                .append(csv(t.notes)).append('\n')
        }
        open(uri).use { it.write(("\uFEFF" + sb.toString()).toByteArray(Charsets.UTF_8)) }
    }

    suspend fun writeReportCsv(
        uri: Uri,
        report: ReportData,
        categories: List<Category>,
        accounts: List<Account>,
        currency: String,
    ) = withContext(Dispatchers.IO) {
        val cat = categories.associateBy { it.id }
        val acc = accounts.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("MyMoney report,${csv(report.range.label)}\n")
        sb.append("Currency,$currency\n\n")
        sb.append("Summary\n")
        sb.append("Income,${plainAmount(report.income)}\n")
        sb.append("Expenses,${plainAmount(report.expense)}\n")
        sb.append("Net savings,${plainAmount(report.net)}\n")
        sb.append("Savings rate,${report.savingsRate?.toPlainString()?.plus("%") ?: "n/a"}\n")
        sb.append("Previous period income,${plainAmount(report.previousIncome)}\n")
        sb.append("Previous period expenses,${plainAmount(report.previousExpense)}\n\n")

        sb.append("Expenses by category\nCategory,Amount,Previous period,Change\n")
        report.expenseByCategory.forEach {
            sb.append("${csv(it.name)},${plainAmount(it.total)},${plainAmount(it.previousTotal)},${plainAmount(it.total - it.previousTotal)}\n")
        }
        sb.append("\nIncome by category\nCategory,Amount,Previous period\n")
        report.incomeByCategory.forEach {
            sb.append("${csv(it.name)},${plainAmount(it.total)},${plainAmount(it.previousTotal)}\n")
        }
        sb.append("\nTrend\nPeriod,Income,Expenses,Net\n")
        report.trend.forEach { sb.append("${csv(it.label)},${plainAmount(it.income)},${plainAmount(it.expense)},${plainAmount(it.net)}\n") }
        if (report.budgetLines.isNotEmpty()) {
            sb.append("\nBudget performance\nBudget,Planned,Spent,Remaining\n")
            report.budgetLines.forEach {
                sb.append("${csv(it.name)},${plainAmount(it.budget)},${plainAmount(it.spent)},${plainAmount(it.budget - it.spent)}\n")
            }
        }
        sb.append("\nTransactions\nDate,Type,Amount,Category,Account,Description\n")
        report.transactions.sortedBy { it.date }.forEach { t ->
            sb.append(Dates.iso(Dates.of(t.date))).append(',')
                .append(typeLabel(t.type)).append(',')
                .append(plainAmount(t.amountCents)).append(',')
                .append(csv(t.categoryId?.let { cat[it]?.name } ?: "")).append(',')
                .append(csv(acc[t.accountId]?.name ?: "")).append(',')
                .append(csv(t.description)).append('\n')
        }
        open(uri).use { it.write(("\uFEFF" + sb.toString()).toByteArray(Charsets.UTF_8)) }
    }

    suspend fun writeReportPdf(
        uri: Uri,
        report: ReportData,
        categories: List<Category>,
        currency: String,
    ) = withContext(Dispatchers.IO) {
        val cat = categories.associateBy { it.id }
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        val brand = Color.rgb(11, 122, 92)
        val title = Paint().apply { isAntiAlias = true; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 28, 25) }
        val h2 = Paint().apply { isAntiAlias = true; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = brand }
        val body = Paint().apply { isAntiAlias = true; textSize = 10.5f; color = Color.rgb(40, 48, 45) }
        val muted = Paint().apply { isAntiAlias = true; textSize = 9.5f; color = Color.rgb(110, 120, 116) }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val boldRight = Paint(right).apply { typeface = Typeface.DEFAULT_BOLD }
        val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val line = Paint().apply { color = Color.rgb(225, 230, 228); strokeWidth = 1f }

        fun finishPage() {
            page?.let { p ->
                p.canvas.drawText("MyMoney · generated offline on ${Dates.medium(Dates.todayEpoch())} · page $pageNumber", margin, pageHeight - 20f, muted)
                doc.finishPage(p)
            }
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin + 10f
        }

        fun ensure(space: Float) {
            if (page == null || y + space > pageHeight - 50f) newPage()
        }

        fun money(c: Long) = Money.format(c, currency)

        newPage()
        val canvas0 = page!!.canvas
        canvas0.drawText("MyMoney financial report", margin, y + 12f, title)
        y += 34f
        canvas0.drawText("${report.period.label} · ${report.range.label}", margin, y, muted)
        y += 26f

        // Summary cards
        val cardW = (pageWidth - margin * 2 - 24f) / 4f
        val summary = listOf(
            "Income" to money(report.income),
            "Expenses" to money(report.expense),
            "Net savings" to money(report.net),
            "Savings rate" to Money.percentText(report.savingsRate),
        )
        summary.forEachIndexed { i, (label, value) ->
            val x = margin + i * (cardW + 8f)
            fill.color = Color.rgb(239, 246, 243)
            canvas0.drawRoundRect(RectF(x, y, x + cardW, y + 52f), 10f, 10f, fill)
            canvas0.drawText(label, x + 10f, y + 18f, muted)
            canvas0.drawText(value, x + 10f, y + 38f, Paint(body).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 12f })
        }
        y += 72f
        page!!.canvas.drawText(
            "Previous period: income ${money(report.previousIncome)}, expenses ${money(report.previousExpense)}. " +
                "Average daily spending: ${money(report.averageDailySpend)}.",
            margin, y, muted,
        )
        y += 26f

        // Expenses by category
        ensure(40f)
        page!!.canvas.drawText("Where the money went", margin, y, h2)
        y += 18f
        val maxCat = report.expenseByCategory.maxOfOrNull { it.total } ?: 0L
        if (report.expenseByCategory.isEmpty()) {
            page!!.canvas.drawText("No expenses recorded in this period.", margin, y, body); y += 18f
        }
        report.expenseByCategory.forEach { c ->
            ensure(22f)
            val cv = page!!.canvas
            cv.drawText(c.name, margin, y + 10f, body)
            val barX = margin + 150f
            val barMax = 230f
            fill.color = Color.rgb(230, 236, 233)
            cv.drawRoundRect(RectF(barX, y + 2f, barX + barMax, y + 12f), 5f, 5f, fill)
            fill.color = brand
            val w = if (maxCat > 0) barMax * Money.fraction(c.total, maxCat) else 0f
            if (w > 0f) cv.drawRoundRect(RectF(barX, y + 2f, barX + w.coerceAtLeast(6f), y + 12f), 5f, 5f, fill)
            val pct = Money.percentText(Money.percent(c.total, report.expense))
            cv.drawText(pct, pageWidth - margin - 90f, y + 10f, right)
            cv.drawText(money(c.total), pageWidth - margin, y + 10f, boldRight)
            y += 20f
        }
        y += 12f

        // Trend
        ensure(60f)
        page!!.canvas.drawText("Income vs expenses", margin, y, h2)
        y += 18f
        page!!.canvas.drawText("Period", margin, y, muted)
        page!!.canvas.drawText("Income", pageWidth - margin - 180f, y, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
        page!!.canvas.drawText("Expenses", pageWidth - margin - 90f, y, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
        page!!.canvas.drawText("Net", pageWidth - margin, y, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
        y += 6f
        report.trend.forEach { t ->
            ensure(18f)
            val cv = page!!.canvas
            cv.drawLine(margin, y, pageWidth - margin, y, line)
            y += 13f
            cv.drawText(t.label, margin, y, body)
            cv.drawText(money(t.income), pageWidth - margin - 180f, y, right)
            cv.drawText(money(t.expense), pageWidth - margin - 90f, y, right)
            cv.drawText(money(t.net), pageWidth - margin, y, boldRight)
            y += 5f
        }
        y += 20f

        if (report.budgetLines.isNotEmpty()) {
            ensure(60f)
            page!!.canvas.drawText("Budget performance", margin, y, h2)
            y += 18f
            report.budgetLines.forEach { b ->
                ensure(18f)
                val cv = page!!.canvas
                val over = b.spent > b.budget
                cv.drawText(b.name, margin, y, body)
                cv.drawText("${money(b.spent)} of ${money(b.budget)}", pageWidth - margin - 120f, y, right)
                val status = if (over) "Over by ${money(b.spent - b.budget)}" else "${money(b.budget - b.spent)} left"
                cv.drawText(status, pageWidth - margin, y, Paint(boldRight).apply { color = if (over) Color.rgb(200, 50, 50) else brand })
                y += 18f
            }
            y += 12f
        }

        // Transactions
        ensure(60f)
        page!!.canvas.drawText("Transactions", margin, y, h2)
        y += 16f
        if (report.transactions.isEmpty()) {
            page!!.canvas.drawText("No income or expenses in this period.", margin, y, body)
            y += 16f
        }
        report.transactions.sortedBy { it.date }.forEach { t ->
            ensure(16f)
            val cv = page!!.canvas
            cv.drawText(Dates.short(t.date), margin, y, muted)
            val desc = (t.description.ifBlank { t.categoryId?.let { cat[it]?.name } ?: "" }).take(48)
            cv.drawText(desc, margin + 50f, y, body)
            cv.drawText(t.categoryId?.let { cat[it]?.name } ?: "", margin + 300f, y, muted)
            val signed = if (t.type == TransactionType.INCOME) t.amountCents else -t.amountCents
            cv.drawText(Money.format(signed, currency, showPlus = true), pageWidth - margin, y,
                Paint(right).apply { color = if (signed >= 0) brand else Color.rgb(40, 48, 45) })
            y += 15f
        }

        finishPage()
        try {
            open(uri).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
