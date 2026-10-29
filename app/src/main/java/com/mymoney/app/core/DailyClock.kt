package com.mymoney.app.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Emits immediately and again just after each local midnight while it has collectors. */
object DailyClock {
    fun dates(): Flow<LocalDate> = flow {
        while (currentCoroutineContext().isActive) {
            val now = ZonedDateTime.now(Dates.zone())
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(Dates.zone())
            val waitMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L) + 1_000L
            delay(waitMillis)
        }
    }.distinctUntilChanged()
}
