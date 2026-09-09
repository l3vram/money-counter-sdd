package com.moneycounter.repository

import com.moneycounter.domain.Payment

interface PaymentRepository {
    fun load(): List<Payment>
    fun saveAll(payments: List<Payment>)
}
