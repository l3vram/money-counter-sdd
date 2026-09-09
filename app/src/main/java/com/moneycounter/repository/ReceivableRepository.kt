package com.moneycounter.repository

import com.moneycounter.domain.Receivable

interface ReceivableRepository {
    fun load(): List<Receivable>
    fun saveAll(receivables: List<Receivable>)
}
