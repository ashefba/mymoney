package com.mymoney.app.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mymoney.app.AppContainer
import com.mymoney.app.data.db.Category
import com.mymoney.app.data.db.CategoryKind
import com.mymoney.app.ui.appViewModel
import com.mymoney.app.ui.components.AppCard
import com.mymoney.app.ui.components.AppIcons
import com.mymoney.app.ui.components.AppTopBar
import com.mymoney.app.ui.components.ColorPicker
import com.mymoney.app.ui.components.ListRow
import com.mymoney.app.ui.components.IconPicker
import com.mymoney.app.ui.components.SectionHeader
import com.mymoney.app.ui.components.paletteColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val categories: StateFlow<List<Category>?> = repo.categories
        .map { list -> list.sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.name.lowercase() })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(category: Category) = viewModelScope.launch { repo.saveCategory(category) }

    /** Returns true if the category was archived because transactions, bills or budgets still use it. */
    suspend fun delete(category: Category): Boolean = repo.deleteOrArchiveCategory(category)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val kinds = listOf(CategoryKind.EXPENSE to "Expenses", CategoryKind.INCOME to "Income")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Categories", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Category(name = "", kind = kind, iconKey = "more", colorIndex = 0) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New category") },
            )
        },
    ) { padding ->
        val list = categories.orEmpty().filter { it.kind == kind }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, (k, label) ->
                        SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, kinds.size)) {
                            Text(label)
                        }
                    }
                }
            }
            val active = list.filter { !it.archived }
            val archived = list.filter { it.archived }
            items(active, key = { it.id }) { c -> CategoryRow(c) { editing = c } }
            if (archived.isNotEmpty()) {
                item { SectionHeader("Archived") }
                items(archived, key = { it.id }) { c -> CategoryRow(c) { editing = c } }
            }
        }
    }

    editing?.let { c ->
        CategoryDialog(
            initial = c,
            onSave = { vm.save(it); editing = null },
            onDelete = if (c.id != 0L) {
                {
                    scope.launch {
                        val archived = vm.delete(c)
                        snackbar.showSnackbar(
                            if (archived) "“${c.name}” is in use, so it was archived. Past transactions keep it."
                            else "“${c.name}” deleted.",
                        )
                    }
                    editing = null
                }
            } else null,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryRow(c: Category, onClick: () -> Unit) {
    AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
        ListRow(
            title = c.name,
            subtitle = if (c.archived) "Archived" else if (c.isDefault) "Default" else "Custom",
            icon = AppIcons.category(c.iconKey),
            iconTint = paletteColor(c.colorIndex),
            onClick = onClick,
        )
    }
}

@Composable
private fun CategoryDialog(initial: Category, onSave: (Category) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.iconKey) }
    var color by remember { mutableStateOf(initial.colorIndex) }
    var archived by remember { mutableStateOf(initial.archived) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New category" else "Edit category") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { if (it.length <= 30) name = it }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                IconPicker(AppIcons.categoryIcons.keys.toList(), icon, paletteColor(color)) { icon = it }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
                if (initial.archived) {
                    TextButton(onClick = { archived = !archived }) { Text(if (archived) "Restore this category" else "Keep archived") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), iconKey = icon, colorIndex = color, archived = archived))
            }) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
