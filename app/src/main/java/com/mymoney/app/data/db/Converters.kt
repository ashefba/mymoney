package com.mymoney.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun transactionTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTransactionType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun categoryKindToString(v: CategoryKind): String = v.name
    @TypeConverter fun stringToCategoryKind(v: String): CategoryKind = CategoryKind.valueOf(v)

    @TypeConverter fun paymentMethodToString(v: PaymentMethod): String = v.name
    @TypeConverter fun stringToPaymentMethod(v: String): PaymentMethod = PaymentMethod.valueOf(v)

    @TypeConverter fun billFrequencyToString(v: BillFrequency): String = v.name
    @TypeConverter fun stringToBillFrequency(v: String): BillFrequency = BillFrequency.valueOf(v)

    @TypeConverter fun debtKindToString(v: DebtKind): String = v.name
    @TypeConverter fun stringToDebtKind(v: String): DebtKind = DebtKind.valueOf(v)

    @TypeConverter fun debtEntryKindToString(v: DebtEntryKind): String = v.name
    @TypeConverter fun stringToDebtEntryKind(v: String): DebtEntryKind = DebtEntryKind.valueOf(v)
}
