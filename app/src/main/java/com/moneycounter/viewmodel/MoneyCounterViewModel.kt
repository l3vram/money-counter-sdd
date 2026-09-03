package com.moneycounter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneycounter.domain.CounterResult
import com.moneycounter.domain.CounterStatus
import com.moneycounter.domain.Denomination
import com.moneycounter.domain.Money
import com.moneycounter.domain.MoneyCounterCalculator
import com.moneycounter.repository.DenominationRepository
import com.moneycounter.repository.JsonDenominationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class MoneyCounterUiState(
    val targetAmount: BigDecimal? = null,
    val denominations: List<Denomination> = emptyList(),
    val quantities: Map<String, Long> = emptyMap(),
    val result: CounterResult = CounterResult(
        Money.ZERO, Money.ZERO, Money.ZERO, CounterStatus.EMPTY
    ),
    val hasActiveCount: Boolean = false
)

class MoneyCounterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DenominationRepository = JsonDenominationRepository(application)

    private val _uiState = MutableStateFlow(MoneyCounterUiState())
    val uiState: StateFlow<MoneyCounterUiState> = _uiState.asStateFlow()

    init {
        loadDenominations()
    }

    private fun loadDenominations() {
        viewModelScope.launch {
            val denominations = repository.load()
            val quantities = denominations.associate { it.id to 0L }
            _uiState.update {
                it.copy(denominations = denominations, quantities = quantities)
            }
            recalculate()
        }
    }

    fun setTargetAmount(amount: BigDecimal?) {
        _uiState.update { it.copy(targetAmount = amount) }
        recalculate()
    }

    fun updateQuantity(denominationId: String, quantity: Long) {
        if (quantity < 0) return
        _uiState.update { state ->
            state.copy(quantities = state.quantities.toMutableMap().apply {
                put(denominationId, quantity)
            })
        }
        recalculate()
    }

    fun incrementQuantity(denominationId: String) {
        val current = _uiState.value.quantities[denominationId] ?: 0L
        updateQuantity(denominationId, current + 1)
    }

    fun decrementQuantity(denominationId: String) {
        val current = _uiState.value.quantities[denominationId] ?: 0L
        if (current > 0) {
            updateQuantity(denominationId, current - 1)
        }
    }

    fun addDenomination(value: Long): Boolean {
        if (value <= 0) return false
        val state = _uiState.value
        if (state.denominations.any { it.value == value }) return false

        val newId = generateNewId(state.denominations)
        val newDenomination = Denomination(newId, value)
        val newDenominations = state.denominations + newDenomination
        val newQuantities = state.quantities + (newId to 0L)

        _uiState.update { it.copy(denominations = newDenominations, quantities = newQuantities) }
        persistDenominations(newDenominations)
        return true
    }

    fun editDenomination(id: String, newValue: Long): Boolean {
        if (newValue <= 0) return false
        val state = _uiState.value
        if (state.hasActiveCount) return false
        if (state.denominations.any { it.value == newValue && it.id != id }) return false

        val newDenominations = state.denominations.map {
            if (it.id == id) it.copy(value = newValue) else it
        }
        _uiState.update { it.copy(denominations = newDenominations) }
        persistDenominations(newDenominations)
        return true
    }

    fun deleteDenomination(id: String): Boolean {
        val state = _uiState.value
        if (state.hasActiveCount) return false

        val newDenominations = state.denominations.filter { it.id != id }
        val newQuantities = state.quantities.toMutableMap().apply { remove(id) }

        _uiState.update { it.copy(denominations = newDenominations, quantities = newQuantities) }
        persistDenominations(newDenominations)
        recalculate()
        return true
    }

    fun moveDenominationUp(id: String) {
        val state = _uiState.value
        val index = state.denominations.indexOfFirst { it.id == id }
        if (index <= 0) return

        val newDenominations = state.denominations.toMutableList()
        val item = newDenominations.removeAt(index)
        newDenominations.add(index - 1, item)

        _uiState.update { it.copy(denominations = newDenominations) }
        persistDenominations(newDenominations)
    }

    fun moveDenominationDown(id: String) {
        val state = _uiState.value
        val index = state.denominations.indexOfFirst { it.id == id }
        if (index < 0 || index >= state.denominations.size - 1) return

        val newDenominations = state.denominations.toMutableList()
        val item = newDenominations.removeAt(index)
        newDenominations.add(index + 1, item)

        _uiState.update { it.copy(denominations = newDenominations) }
        persistDenominations(newDenominations)
    }

    fun clearAll() {
        val quantities = _uiState.value.denominations.associate { it.id to 0L }
        _uiState.update { it.copy(targetAmount = null, quantities = quantities) }
        recalculate()
    }

    private fun recalculate() {
        val state = _uiState.value
        val result = MoneyCounterCalculator.calculate(
            targetAmount = state.targetAmount,
            denominations = state.denominations,
            quantities = state.quantities
        )
        val hasData = (state.targetAmount != null && state.targetAmount > BigDecimal.ZERO) ||
                state.quantities.values.any { it > 0 }
        _uiState.update { it.copy(result = result, hasActiveCount = hasData) }
    }

    private fun persistDenominations(denominations: List<Denomination>) {
        viewModelScope.launch { repository.save(denominations) }
    }

    private fun generateNewId(existing: List<Denomination>): String {
        val existingIds = existing.map { it.id }.toSet()
        var counter = 1
        while (true) {
            val candidate = "d$counter"
            if (candidate !in existingIds) return candidate
            counter++
        }
    }
}
