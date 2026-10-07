package com.lapel.app.data

import androidx.room.withTransaction
import com.lapel.app.data.local.LapelDatabase

suspend fun <R> LapelDatabase.withTransactionForTest(block: suspend () -> R): R = withTransaction(block)
