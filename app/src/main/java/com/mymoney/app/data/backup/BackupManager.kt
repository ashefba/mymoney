package com.mymoney.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.mymoney.app.data.db.Account
import com.mymoney.app.data.db.Bill
import com.mymoney.app.data.db.Budget
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.Debt
import com.mymoney.app.data.db.DebtEntry
import com.mymoney.app.data.db.GoalContribution
import com.mymoney.app.data.db.SavingsGoal
import com.mymoney.app.data.db.Transaction
import com.mymoney.app.data.prefs.SettingsRepository
import com.mymoney.app.data.repo.DataSnapshot
import com.mymoney.app.data.repo.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val format: String = "mymoney-backup",
    val version: Int = 2,
    val createdAt: Long,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val goals: List<SavingsGoal>,
    val goalContributions: List<GoalContribution>,
    val bills: List<Bill>,
    val debts: List<Debt>,
    val debtEntries: List<DebtEntry>,
    val settings: Map<String, String> = emptyMap(),
    /** Receipt file name to base64 JPEG. Only filled in encrypted backups. */
    val receipts: Map<String, String> = emptyMap(),
    /** Distinguishes an encrypted backup with no photos from a JSON export that intentionally omits them. */
    val includesReceipts: Boolean = false,
)

class PasswordRequiredException : Exception("This backup is encrypted. Enter its password.")

class BackupManager(
    private val context: Context,
    private val repository: FinanceRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private suspend fun collect(includeReceipts: Boolean): BackupData {
        val s = repository.snapshot()
        val receipts = if (includeReceipts) {
            s.transactions.mapNotNull { it.receiptFile }.distinct().associateWith { name ->
                val bytes = repository.receipts.read(name)
                    ?: throw IllegalStateException("Receipt $name is missing; repair or remove it before backing up.")
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.toMap()
        } else emptyMap()
        return BackupData(
            createdAt = System.currentTimeMillis(),
            accounts = s.accounts,
            categories = s.categories,
            transactions = s.transactions,
            budgets = s.budgets,
            goals = s.goals,
            goalContributions = s.contributions,
            bills = s.bills,
            debts = s.debts,
            debtEntries = s.debtEntries,
            settings = settings.exportForBackup(),
            receipts = receipts,
            includesReceipts = includeReceipts,
        )
    }

    suspend fun writeEncryptedBackup(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val data = collect(includeReceipts = true)
        val plain = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(plain, password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
            ?: throw IllegalStateException("Could not open the selected file.")
        settings.setLastBackup(System.currentTimeMillis())
    }

    /** Unencrypted, human-readable export of all data (receipt images are not included). */
    suspend fun writePlainExport(uri: Uri) = withContext(Dispatchers.IO) {
        val data = collect(includeReceipts = false)
        val text = prettyJson.encodeToString(BackupData.serializer(), data)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Could not open the selected file.")
    }

    suspend fun isEncrypted(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
        BackupCrypto.isEncrypted(bytes)
    }

    suspend fun read(uri: Uri, password: CharArray?): BackupData = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw InvalidBackupException("Could not open the selected file.")
        val plain = if (BackupCrypto.isEncrypted(bytes)) {
            if (password == null) throw PasswordRequiredException()
            BackupCrypto.decrypt(bytes, password)
        } else bytes
        val data = try {
            json.decodeFromString(BackupData.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw InvalidBackupException("This file is not a MyMoney backup.")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This file is not a MyMoney backup.")
        }
        if (data.format != "mymoney-backup") throw InvalidBackupException("This file is not a MyMoney backup.")
        if (data.version !in SUPPORTED_VERSIONS) {
            throw InvalidBackupException("This backup was created by an unsupported MyMoney version.")
        }
        validateStructure(data)
        data
    }

    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        validateStructure(data)
        val includesReceipts = data.includesReceipts || (data.version == 1 && data.receipts.isNotEmpty())
        val decodedReceipts = if (includesReceipts) {
            data.receipts.mapValues { (name, b64) ->
                if (!repository.receipts.isValidName(name)) {
                    throw InvalidBackupException("The backup contains an unsafe receipt file name.")
                }
                try {
                    Base64.decode(b64, Base64.NO_WRAP)
                } catch (e: IllegalArgumentException) {
                    throw InvalidBackupException("A receipt image in the backup is damaged.")
                }
            }
        } else {
            emptyMap()
        }
        val staging = if (includesReceipts) {
            try {
                repository.receipts.stageRestore(decodedReceipts)
            } catch (e: Exception) {
                throw InvalidBackupException(e.message ?: "Receipt images in the backup could not be prepared.")
            }
        } else {
            null
        }
        try {
            repository.replaceAll(
                DataSnapshot(
                    accounts = data.accounts,
                    categories = data.categories,
                    transactions = data.transactions,
                    budgets = data.budgets,
                    goals = data.goals,
                    contributions = data.goalContributions,
                    bills = data.bills,
                    debts = data.debts,
                    debtEntries = data.debtEntries,
                )
            )
            staging?.let { repository.receipts.commitStagedRestore(it) }
            repository.receipts.prune(repository.referencedReceipts())
            repository.ensureDefaults()
            settings.importFromBackup(data.settings)
        } finally {
            staging?.takeIf { it.exists() }?.deleteRecursively()
        }
    }

    private fun validateStructure(data: BackupData) {
        val accountIds = data.accounts.map { it.id }.toSet()
        val categoryIds = data.categories.map { it.id }.toSet()
        val goalIds = data.goals.map { it.id }.toSet()
        val billIds = data.bills.map { it.id }.toSet()
        val debtIds = data.debts.map { it.id }.toSet()
        val debtEntryIds = data.debtEntries.map { it.id }.toSet()
        fun invalid(message: String): Nothing = throw InvalidBackupException(message)

        if (data.accounts.any { it.id <= 0 } || accountIds.size != data.accounts.size) invalid("The backup has invalid account identifiers.")
        if (data.categories.any { it.id <= 0 } || categoryIds.size != data.categories.size) invalid("The backup has invalid category identifiers.")
        if (data.goals.any { it.id <= 0 } || goalIds.size != data.goals.size) invalid("The backup has invalid goal identifiers.")
        if (data.bills.any { it.id <= 0 } || billIds.size != data.bills.size) invalid("The backup has invalid bill identifiers.")
        if (data.debts.any { it.id <= 0 } || debtIds.size != data.debts.size) invalid("The backup has invalid debt identifiers.")
        if (data.debtEntries.any { it.id <= 0 } || debtEntryIds.size != data.debtEntries.size) invalid("The backup has invalid debt-entry identifiers.")
        if (data.transactions.any { it.id <= 0 } || data.transactions.map { it.id }.toSet().size != data.transactions.size) {
            invalid("The backup has invalid transaction identifiers.")
        }
        if (data.goalContributions.any { it.id <= 0 } ||
            data.goalContributions.map { it.id }.toSet().size != data.goalContributions.size
        ) invalid("The backup has invalid goal-contribution identifiers.")

        data.transactions.forEach { tx ->
            if (tx.accountId !in accountIds || (tx.toAccountId != null && tx.toAccountId !in accountIds)) {
                invalid("A transaction refers to an account that is missing.")
            }
            if (tx.categoryId != null && tx.categoryId !in categoryIds) invalid("A transaction refers to a category that is missing.")
            if (tx.billId != null && tx.billId !in billIds) invalid("A transaction refers to a bill that is missing.")
            if (tx.debtEntryId != null && tx.debtEntryId !in debtEntryIds) invalid("A transaction refers to a debt entry that is missing.")
            if (tx.initialDebtId != null && tx.initialDebtId !in debtIds) invalid("A transaction refers to a debt that is missing.")
            if (tx.receiptFile != null && !repository.receipts.isValidName(tx.receiptFile)) {
                invalid("A transaction contains an unsafe receipt file name.")
            }
        }
        data.goalContributions.forEach {
            if (it.goalId !in goalIds) invalid("A contribution refers to a savings goal that is missing.")
        }
        data.bills.forEach {
            if (it.accountId != null && it.accountId !in accountIds) invalid("A bill refers to an account that is missing.")
            if (it.categoryId != null && it.categoryId !in categoryIds) invalid("A bill refers to a category that is missing.")
        }
        data.debts.forEach {
            if (it.accountId != null && it.accountId !in accountIds) invalid("A debt refers to an account that is missing.")
        }
        data.debtEntries.forEach {
            if (it.debtId !in debtIds) invalid("A debt entry refers to a debt that is missing.")
            if (it.accountId != null && it.accountId !in accountIds) invalid("A debt entry refers to an account that is missing.")
        }
    }

    private companion object {
        val SUPPORTED_VERSIONS = setOf(1, 2)
    }
}
