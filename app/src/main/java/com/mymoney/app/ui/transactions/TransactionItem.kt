package com.mymoney.app.ui.transactions

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mymoney.app.core.Dates
import com.mymoney.app.core.Money
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.db.TransactionType
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.IconBadge
import com.mymoney.app.ui.components.paletteColor
import com.mymoney.app.ui.theme.FinanceTheme
import com.mymoney.app.ui.theme.MoneyTextStyle

/** A transaction with its related names resolved for display. */
data class TxItem(
    val tx: Transaction,
    val category: Category?,
    val account: Account?,
    val toAccount: Account?,
) {
    val title: String
        get() = tx.description.ifBlank {
            when (tx.type) {
                TransactionType.TRANSFER -> "Transfer"
                TransactionType.DEBT_IN -> "Debt money in"
                TransactionType.DEBT_OUT -> "Debt repayment"
                else -> category?.name ?: "Uncategorised"
            }
        }

    val subtitle: String
        get() = when (tx.type) {
            TransactionType.TRANSFER -> "${account?.name ?: "?"} → ${toAccount?.name ?: "?"}"
            TransactionType.DEBT_IN, TransactionType.DEBT_OUT -> "Debt · ${account?.name ?: ""}"
            else -> listOfNotNull(
                category?.name?.takeIf { tx.description.isNotBlank() },
                account?.name,
            ).joinToString(" · ")
        }
}

fun buildTxItems(txs: List<Transaction>, categories: List<Category>, accounts: List<Account>): List<TxItem> {
    val c = categories.associateBy { it.id }
    val a = accounts.associateBy { it.id }
    return txs.map { t -> TxItem(t, t.categoryId?.let { c[it] }, a[t.accountId], t.toAccountId?.let { a[it] }) }
}

@Composable
fun TransactionRow(item: TxItem, currency: String, onClick: () -> Unit, showDate: Boolean = false) {
    val colors = FinanceTheme.colors
    val (icon, tint) = when (item.tx.type) {
        TransactionType.TRANSFER -> Icons.Rounded.SwapHoriz to colors.transfer
        TransactionType.DEBT_IN -> Icons.Rounded.CallReceived to colors.transfer
        TransactionType.DEBT_OUT -> Icons.Rounded.CallMade to colors.transfer
        else -> AppIcons.category(item.category?.iconKey) to paletteColor(item.category?.colorIndex ?: 0)
    }
    val amountColor: Color = when (item.tx.type) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val signed = when (item.tx.type) {
        TransactionType.INCOME, TransactionType.DEBT_IN -> item.tx.amountCents
        TransactionType.EXPENSE, TransactionType.DEBT_OUT -> -item.tx.amountCents
        TransactionType.TRANSFER -> item.tx.amountCents
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (showDate) listOf(Dates.relative(item.tx.date), item.subtitle).filter { it.isNotBlank() }.joinToString(" · ") else item.subtitle
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.tx.receiptFile != null) {
            Icon(Icons.Rounded.Receipt, contentDescription = "Has receipt", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            Money.format(signed, currency, showPlus = item.tx.type == TransactionType.INCOME || item.tx.type == TransactionType.DEBT_IN),
            style = MaterialTheme.typography.bodyLarge.merge(MoneyTextStyle),
            color = amountColor,
        )
    }
}
