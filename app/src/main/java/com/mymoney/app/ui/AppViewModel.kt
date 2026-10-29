package com.mymoney.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymoney.app.AppContainer
import com.mymoney.app.MyMoneyApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MyMoneyApp).container

/** Creates a screen ViewModel with access to the app's dependency container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline factory: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory(container) as T
        },
    )
}
