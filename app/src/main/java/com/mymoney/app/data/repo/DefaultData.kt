package com.mymoney.app.data.repo

import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind

object DefaultData {
    const val INTEREST_EXPENSE_ICON = "percent"
    const val INTEREST_INCOME_ICON = "trending_up"

    fun categories(): List<Category> {
        val expense = listOf(
            "Groceries" to "grocery",
            "Eating out" to "restaurant",
            "Transport" to "car",
            "Rent & housing" to "home",
            "Utilities" to "bolt",
            "Phone & internet" to "wifi",
            "Health" to "health",
            "Shopping" to "shopping",
            "Entertainment" to "movie",
            "Subscriptions" to "subscriptions",
            "Travel" to "flight",
            "Education" to "school",
            "Personal care" to "spa",
            "Gifts & donations" to "gift",
            "Kids" to "child",
            "Pets" to "pets",
            "Insurance" to "shield",
            "Interest & fees" to INTEREST_EXPENSE_ICON,
            "Other" to "more",
        )
        val income = listOf(
            "Salary" to "work",
            "Freelance & side jobs" to "payments",
            "Gifts received" to "redeem",
            "Refunds" to "undo",
            "Interest & returns" to INTEREST_INCOME_ICON,
            "Other income" to "money",
        )
        return expense.mapIndexed { i, (name, icon) ->
            Category(name = name, kind = CategoryKind.EXPENSE, iconKey = icon, colorIndex = i, isDefault = true, sortOrder = i)
        } + income.mapIndexed { i, (name, icon) ->
            Category(name = name, kind = CategoryKind.INCOME, iconKey = icon, colorIndex = i + 3, isDefault = true, sortOrder = 100 + i)
        }
    }
}
