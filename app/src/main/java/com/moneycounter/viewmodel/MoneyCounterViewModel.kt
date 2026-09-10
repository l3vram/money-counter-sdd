package com.moneycounter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneycounter.domain.CounterResult
import com.moneycounter.domain.CounterStatus
import com.moneycounter.domain.Currency
import com.moneycounter.domain.DefaultCurrencies
import com.moneycounter.domain.Denomination
import com.moneycounter.domain.InventoryWriteoff
import com.moneycounter.domain.MeasurementUnit
import com.moneycounter.domain.Money
import com.moneycounter.domain.MoneyCounterCalculator
import com.moneycounter.domain.Movement
import com.moneycounter.domain.Payment
import com.moneycounter.domain.Product
import com.moneycounter.domain.ProductPrice
import com.moneycounter.domain.ProductSelection
import com.moneycounter.domain.Receivable
import com.moneycounter.domain.ReceivableStatus
import com.moneycounter.domain.SavedCount
import com.moneycounter.domain.SavedCountItem
import com.moneycounter.domain.SavedProductItem
import com.moneycounter.repository.CurrencyRepository
import com.moneycounter.repository.CurrencySettings
import com.moneycounter.repository.DenominationRepository
import com.moneycounter.repository.JsonCurrencyRepository
import com.moneycounter.repository.JsonDenominationRepository
import com.moneycounter.repository.JsonPaymentRepository
import com.moneycounter.repository.JsonMovementRepository
import com.moneycounter.repository.JsonProductRepository
import com.moneycounter.repository.JsonSavedCountRepository
import com.moneycounter.repository.JsonReceivableRepository
import com.moneycounter.repository.JsonUnitRepository
import com.moneycounter.repository.JsonWriteoffRepository
import com.moneycounter.repository.MovementRepository
import com.moneycounter.repository.PaymentRepository
import com.moneycounter.repository.ProductRepository
import com.moneycounter.repository.ReceivableRepository
import com.moneycounter.repository.SavedCountRepository
import com.moneycounter.repository.UnitRepository
import com.moneycounter.repository.WriteoffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class MoneyCounterUiState(
    val targetAmount: BigDecimal? = null,
    val denominations: List<Denomination> = emptyList(),
    val quantities: Map<String, Long> = emptyMap(),
    val result: CounterResult = CounterResult(
        Money.ZERO, Money.ZERO, Money.ZERO, CounterStatus.EMPTY
    ),
    val hasActiveCount: Boolean = false,
    val history: List<SavedCount> = emptyList(),
    val lastSavedId: String? = null,
    val savedCountId: String? = null,
    val currencies: List<Currency> = DefaultCurrencies.get(),
    val selectedCurrencyId: String = DefaultCurrencies.CUP.id,
    val products: List<Product> = emptyList(),
    val productSelections: List<ProductSelection> = listOf(ProductSelection()),
    val units: List<MeasurementUnit> = emptyList(),
    val writeoffs: List<InventoryWriteoff> = emptyList(),
    val receivables: List<Receivable> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val movements: List<Movement> = emptyList()
)

class MoneyCounterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DenominationRepository = JsonDenominationRepository(application)
    private val historyRepository: SavedCountRepository = JsonSavedCountRepository(application)
    private val currencyRepository: CurrencyRepository = JsonCurrencyRepository(application)
    private val productRepository: ProductRepository = JsonProductRepository(application)
    private val unitRepository: UnitRepository = JsonUnitRepository(application)
    private val writeoffRepository: WriteoffRepository = JsonWriteoffRepository(application)
    private val receivableRepository: ReceivableRepository = JsonReceivableRepository(application)
    private val paymentRepository: PaymentRepository = JsonPaymentRepository(application)
    private val movementRepository: MovementRepository = JsonMovementRepository(application)

    private val _uiState = MutableStateFlow(MoneyCounterUiState())
    val uiState: StateFlow<MoneyCounterUiState> = _uiState.asStateFlow()

    init {
        loadDenominations()
        loadHistory()
        loadCurrencySettings()
        loadProducts()
        loadUnits()
        loadWriteoffs()
        loadReceivables()
        loadPayments()
        loadMovements()
    }

    val selectedCurrency: Currency
        get() = _uiState.value.currencies.firstOrNull { it.id == _uiState.value.selectedCurrencyId }
            ?: DefaultCurrencies.CUP

    fun currencySymbol(): String = selectedCurrency.symbol

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

    private fun loadCurrencySettings() {
        viewModelScope.launch {
            val settings: CurrencySettings = currencyRepository.load()
            _uiState.update {
                it.copy(
                    currencies = settings.currencies,
                    selectedCurrencyId = settings.selectedCurrencyId
                        .takeIf { id -> settings.currencies.any { c -> c.id == id } }
                        ?: DefaultCurrencies.CUP.id
                )
            }
            recalculate()
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            val products = productRepository.load()
            _uiState.update { it.copy(products = products) }
            recalculate()
        }
    }

    private fun loadUnits() {
        viewModelScope.launch {
            val units = unitRepository.load()
            _uiState.update { it.copy(units = units) }
        }
    }

    fun addUnit(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false
        val state = _uiState.value
        if (state.units.any { it.name.equals(cleanName, ignoreCase = true) }) return false

        val new = MeasurementUnit(generateUnitId(state.units), cleanName)
        val newUnits = state.units + new
        _uiState.update { it.copy(units = newUnits) }
        persistUnits(newUnits)
        return true
    }

    fun editUnit(id: String, name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false
        val state = _uiState.value
        if (state.units.any { it.name.equals(cleanName, ignoreCase = true) && it.id != id }) return false
        val oldUnit = state.units.firstOrNull { it.id == id } ?: return false

        val newUnits = state.units.map {
            if (it.id == id) it.copy(name = cleanName) else it
        }
        val newProducts = state.products.map {
            if (it.unit.equals(oldUnit.name, ignoreCase = true)) it.copy(unit = cleanName) else it
        }
        _uiState.update { it.copy(units = newUnits, products = newProducts) }
        persistUnits(newUnits)
        persistProducts(newProducts)
        return true
    }

    fun deleteUnit(id: String): Boolean {
        val state = _uiState.value
        if (state.units.size <= 1) return false

        val unitName = state.units.firstOrNull { it.id == id }?.name ?: return false
        if (state.products.any { it.unit.equals(unitName, ignoreCase = true) }) return false

        val newUnits = state.units.filterNot { it.id == id }
        _uiState.update { it.copy(units = newUnits) }
        persistUnits(newUnits)
        return true
    }

    private fun persistUnits(units: List<MeasurementUnit>) {
        viewModelScope.launch { unitRepository.save(units) }
    }

    private fun generateUnitId(existing: List<MeasurementUnit>): String {
        val existingIds = existing.map { it.id }.toSet()
        var counter = 1
        while (true) {
            val candidate = "u$counter"
            if (candidate !in existingIds) return candidate
            counter++
        }
    }

    fun setTargetAmount(amount: BigDecimal?) {
        _uiState.update { it.copy(targetAmount = amount, savedCountId = null) }
        recalculate()
    }

    fun updateQuantity(denominationId: String, quantity: Long) {
        if (quantity < 0) return
        _uiState.update { state ->
            state.copy(
                quantities = state.quantities.toMutableMap().apply {
                    put(denominationId, quantity)
                },
                savedCountId = null
            )
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

        val newId = generateDenominationId(state.denominations)
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
        _uiState.update { it.copy(targetAmount = null, quantities = quantities, savedCountId = null) }
        recalculate()
    }

    fun clearProductSelections() {
        _uiState.update {
            it.copy(productSelections = listOf(ProductSelection()), targetAmount = null, savedCountId = null)
        }
        recalculate()
    }

    fun selectCurrency(currencyId: String) {
        _uiState.update { st ->
            if (st.currencies.none { it.id == currencyId }) return@update st
            st.copy(selectedCurrencyId = currencyId)
        }
        persistCurrencySettings()
    }

    fun addCurrency(code: String, name: String, symbol: String): Boolean {
        val cleanCode = code.trim().uppercase()
        val cleanName = name.trim()
        val cleanSymbol = symbol.trim()
        if (cleanCode.isEmpty() || cleanCode.length != 3) return false
        if (cleanName.isEmpty() || cleanSymbol.isEmpty()) return false
        val state = _uiState.value
        if (state.currencies.any { it.code == cleanCode }) return false

        val new = listOf(Currency(generateCurrencyId(state.currencies), cleanCode, cleanName, cleanSymbol))
        val newCurrencies = state.currencies + new
        _uiState.update { it.copy(currencies = newCurrencies) }
        persistCurrencySettings()
        return true
    }

    fun editCurrency(id: String, code: String, name: String, symbol: String): Boolean {
        val cleanCode = code.trim().uppercase()
        val cleanName = name.trim()
        val cleanSymbol = symbol.trim()
        if (cleanCode.isEmpty() || cleanCode.length != 3) return false
        if (cleanName.isEmpty() || cleanSymbol.isEmpty()) return false
        val state = _uiState.value
        if (state.currencies.any { it.code == cleanCode && it.id != id }) return false
        val target = state.currencies.firstOrNull { it.id == id } ?: return false

        val newCurrencies = state.currencies.map {
            if (it.id == id) it.copy(code = cleanCode, name = cleanName, symbol = cleanSymbol) else it
        }
        _uiState.update { it.copy(currencies = newCurrencies) }
        persistCurrencySettings()
        return true
    }

    fun deleteCurrency(id: String): Boolean {
        val state = _uiState.value
        if (state.currencies.size <= 1) return false
        if (state.selectedCurrencyId == id) return false

        val newCurrencies = state.currencies.filterNot { it.id == id }
        _uiState.update { it.copy(currencies = newCurrencies) }
        persistCurrencySettings()
        return true
    }

    fun addProduct(name: String, unit: String, stock: BigDecimal, prices: Map<String, ProductPrice>): Boolean {
        val cleanName = name.trim()
        val cleanUnit = unit.trim()
        if (cleanName.isEmpty() || cleanUnit.isEmpty()) return false
        if (stock.signum() < 0) return false
        if (prices.isEmpty()) return false
        if (prices.values.all { it.unitPrice.signum() == 0 && it.surcharge.signum() == 0 }) return false
        val state = _uiState.value

        val new = Product(generateProductId(state.products), cleanName, cleanUnit, stock, prices)
        val newProducts = state.products + new
        _uiState.update { it.copy(products = newProducts) }
        persistProducts(newProducts)
        return true
    }

    fun editProduct(id: String, name: String, unit: String, stock: BigDecimal, prices: Map<String, ProductPrice>): Boolean {
        val cleanName = name.trim()
        val cleanUnit = unit.trim()
        if (cleanName.isEmpty() || cleanUnit.isEmpty()) return false
        if (stock.signum() < 0) return false
        val state = _uiState.value
        if (state.products.none { it.id == id }) return false

        val newProducts = state.products.map {
            if (it.id == id) it.copy(name = cleanName, unit = cleanUnit, stock = stock, prices = prices) else it
        }
        _uiState.update { it.copy(products = newProducts) }
        persistProducts(newProducts)
        return true
    }

    fun deleteProduct(id: String): Boolean {
        val state = _uiState.value
        val newProducts = state.products.filterNot { it.id == id }
        val newSelections = state.productSelections.map {
            if (it.productId == id) it.copy(productId = null, quantityText = "") else it
        }
        _uiState.update {
            it.copy(products = newProducts, productSelections = newSelections)
        }
        persistProducts(newProducts)
        recalculate()
        return true
    }

    fun addProductRow() {
        _uiState.update {
            it.copy(productSelections = it.productSelections + ProductSelection())
        }
    }

    fun removeProductRow(index: Int) {
        val state = _uiState.value
        if (index !in state.productSelections.indices) return
        val newSelections = state.productSelections.toMutableList().apply { removeAt(index) }
        _uiState.update { it.copy(productSelections = newSelections) }
        recalculate()
    }

    fun productsWithPrice(currencyId: String): List<Product> =
        productsWithPrice(_uiState.value.products, currencyId)

    fun updateProductSelection(index: Int, productId: String) {
        val state = _uiState.value
        if (index !in state.productSelections.indices) return
        if (state.products.none { it.id == productId }) return
        val newSelections = state.productSelections.toMutableList().apply {
            set(index, this[index].copy(productId = productId))
        }
        _uiState.update { it.copy(productSelections = newSelections, savedCountId = null) }
        recalculate()
    }

    fun updateProductQuantity(index: Int, quantityText: String) {
        val state = _uiState.value
        if (index !in state.productSelections.indices) return
        val cleaned = quantityText.trim().replace(',', '.')
        if (!cleaned.isEmpty() && !cleaned.matches(Regex("\\d*\\.?\\d*"))) return
        val newSelections = state.productSelections.toMutableList().apply {
            set(index, this[index].copy(quantityText = quantityText))
        }
        _uiState.update { it.copy(productSelections = newSelections, savedCountId = null) }
        recalculate()
    }

    fun productsTotal(): BigDecimal {
        val state = _uiState.value
        var total = Money.ZERO
        for (selection in state.productSelections) {
            val product = state.products.firstOrNull { it.id == selection.productId } ?: continue
            val quantity = selection.quantity()
            if (quantity.signum() < 0) continue
            val price = product.effectiveUnitPriceFor(state.selectedCurrencyId) ?: continue
            total = total.add(price.multiply(quantity))
        }
        return total.setScale(Money.SCALE)
    }

    fun productLineTotal(selection: ProductSelection): BigDecimal {
        val product = _uiState.value.products.firstOrNull { it.id == selection.productId } ?: return Money.ZERO
        val quantity = selection.quantity()
        if (quantity.signum() < 0) return Money.ZERO
        return product.effectiveUnitPriceFor(_uiState.value.selectedCurrencyId)
            ?.multiply(quantity)?.setScale(Money.SCALE) ?: Money.ZERO
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = historyRepository.load()
            _uiState.update { it.copy(history = history) }
        }
    }

    private fun loadWriteoffs() {
        viewModelScope.launch {
            val writeoffs = writeoffRepository.load()
            _uiState.update { it.copy(writeoffs = writeoffs) }
        }
    }

    /** Registers a "baja por merma": reduces stock and records a valued loss.
     *  Moves NO cash. Returns false on invalid product/quantity/missing price. */
    fun registerWriteoff(productId: String, quantityText: String, reason: String?): Boolean {
        val state = _uiState.value
        val product = state.products.firstOrNull { it.id == productId } ?: return false
        val quantity = ProductSelection.parseQuantity(quantityText)
        if (quantity.signum() <= 0) return false
        val unitPrice = product.effectiveUnitPriceFor(state.selectedCurrencyId) ?: return false
        val lossValue = unitPrice.multiply(quantity).setScale(Money.SCALE)

        val writeoff = InventoryWriteoff(
            id = UUID.randomUUID().toString(),
            at = System.currentTimeMillis(),
            productId = product.id,
            name = product.name,
            unit = product.unit,
            quantity = quantity,
            unitPrice = unitPrice,
            lossValue = lossValue,
            currencyId = state.selectedCurrencyId,
            reason = reason?.trim()?.takeIf { it.isNotEmpty() }
        )

        val newProducts = applyWriteoff(state.products, product.id, quantity)
        _uiState.update {
            it.copy(
                writeoffs = listOf(writeoff) + it.writeoffs,
                products = newProducts
            )
        }
        persistProducts(newProducts)
        persistWriteoffs()
        return true
    }

    private fun persistWriteoffs() {
        val writeoffs = _uiState.value.writeoffs
        viewModelScope.launch { writeoffRepository.saveAll(writeoffs) }
    }

    fun saveCount(): String? {
        val state = _uiState.value
        if (state.result.status != CounterStatus.COMPLETED) return null
        val target = productsTotal()
        if (target.signum() <= 0) return null

        val items = state.denominations
            .mapNotNull { den ->
                val qty = state.quantities[den.id] ?: 0L
                if (qty <= 0) null
                else SavedCountItem(den.value, qty, Money.fromLong(den.value * qty))
            }

        val savedProducts = state.productSelections.mapNotNull { selection ->
            val product = state.products.firstOrNull { it.id == selection.productId } ?: return@mapNotNull null
            val quantity = selection.quantity()
            if (quantity.signum() <= 0) return@mapNotNull null
            val pp = product.priceFor(state.selectedCurrencyId) ?: return@mapNotNull null
            SavedProductItem(
                name = product.name,
                unit = product.unit,
                quantity = quantity,
                unitPrice = pp.unitPrice,
                surcharge = pp.surcharge,
                subtotal = pp.effectiveUnitPrice.multiply(quantity).setScale(Money.SCALE)
            )
        }

        val saved = SavedCount(
            id = UUID.randomUUID().toString(),
            savedAt = System.currentTimeMillis(),
            targetAmount = target,
            items = items,
            currency = currencySymbol(),
            products = savedProducts,
            currencyId = state.selectedCurrencyId
        )

        _uiState.update { st ->
            st.copy(
                history = listOf(saved) + st.history,
                lastSavedId = saved.id,
                savedCountId = saved.id
            )
        }
        val newProducts = applyStockDeduction(state.products, state.productSelections)
        _uiState.update { it.copy(products = newProducts) }
        persistProducts(newProducts)
        persistHistory()
        return saved.id
    }

    fun deleteSavedCount(id: String) {
        _uiState.update { st ->
            st.copy(history = st.history.filterNot { it.id == id })
        }
        persistHistory()
    }

    fun deleteSavedCounts(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _uiState.update { st -> st.copy(history = st.history.filterNot { it.id in idSet }) }
        persistHistory()
    }

    private fun persistHistory() {
        val history = _uiState.value.history
        viewModelScope.launch { historyRepository.saveAll(history) }
    }

    private fun loadReceivables() {
        viewModelScope.launch {
            val receivables = receivableRepository.load()
            _uiState.update { it.copy(receivables = receivables) }
        }
    }

    /** Registers a credit sale ("venta a crédito" / fiado): goods leave (stock deducted)
     *  but NO cash comes in. Records an OPEN receivable for later collection. Does NOT
     *  create a SavedCount and does NOT add to any cash total. Returns the new
     *  receivable id, or null on invalid input (not completed, zero total, blank debtor). */
    fun registerCreditSale(debtorName: String): String? {
        val state = _uiState.value
        if (state.result.status != CounterStatus.COMPLETED) return null
        val target = productsTotal()
        if (target.signum() <= 0) return null
        val trimmedName = debtorName.trim()
        if (trimmedName.isBlank()) return null

        val savedProducts = state.productSelections.mapNotNull { selection ->
            val product = state.products.firstOrNull { it.id == selection.productId } ?: return@mapNotNull null
            val quantity = selection.quantity()
            if (quantity.signum() <= 0) return@mapNotNull null
            val pp = product.priceFor(state.selectedCurrencyId) ?: return@mapNotNull null
            SavedProductItem(
                name = product.name,
                unit = product.unit,
                quantity = quantity,
                unitPrice = pp.unitPrice,
                surcharge = pp.surcharge,
                subtotal = pp.effectiveUnitPrice.multiply(quantity).setScale(Money.SCALE)
            )
        }

        val receivable = Receivable(
            id = UUID.randomUUID().toString(),
            at = System.currentTimeMillis(),
            debtorName = trimmedName,
            amount = target,
            currencyId = state.selectedCurrencyId,
            products = savedProducts,
            status = ReceivableStatus.OPEN
        )

        _uiState.update { st ->
            st.copy(
                receivables = listOf(receivable) + st.receivables,
                savedCountId = receivable.id
            )
        }
        val newProducts = applyStockDeduction(state.products, state.productSelections)
        _uiState.update { it.copy(products = newProducts) }
        persistProducts(newProducts)
        persistReceivables()
        return receivable.id
    }

    private fun persistReceivables() {
        val receivables = _uiState.value.receivables
        viewModelScope.launch { receivableRepository.saveAll(receivables) }
    }

    private fun loadPayments() {
        viewModelScope.launch {
            val payments = paymentRepository.load()
            _uiState.update { it.copy(payments = payments) }
        }
    }

    private fun persistPayments() {
        val payments = _uiState.value.payments
        viewModelScope.launch { paymentRepository.saveAll(payments) }
    }

    private fun loadMovements() {
        viewModelScope.launch {
            val movements = movementRepository.load()
            _uiState.update { it.copy(movements = movements) }
        }
    }

    /** Settles ("cobra") an OPEN receivable: records a Payment (cash in) for its full
     *  amount and flips the receivable to SETTLED. Does NOT touch stock (already deducted
     *  at the credit sale) and does NOT create a SavedCount or re-create the debt.
     *  Returns false with no changes if the receivable is unknown or not OPEN. */
    fun settleReceivable(receivableId: String): Boolean {
        val state = _uiState.value
        val (updatedReceivables, payment) = settleReceivablePure(
            receivables = state.receivables,
            receivableId = receivableId,
            paymentId = UUID.randomUUID().toString(),
            now = System.currentTimeMillis()
        )
        if (payment == null) return false

        _uiState.update { st ->
            st.copy(
                receivables = updatedReceivables,
                payments = listOf(payment) + st.payments
            )
        }
        persistReceivables()
        persistPayments()
        return true
    }

    private fun persistCurrencySettings() {
        val state = _uiState.value
        viewModelScope.launch {
            currencyRepository.save(CurrencySettings(state.currencies, state.selectedCurrencyId))
        }
    }

    private fun persistProducts(products: List<Product>) {
        viewModelScope.launch { productRepository.save(products) }
    }

    private fun recalculate() {
        val state = _uiState.value
        val target = productsTotal().takeIf { it.signum() > 0 }
        val result = MoneyCounterCalculator.calculate(
            targetAmount = target,
            denominations = state.denominations,
            quantities = state.quantities
        )
        val hasData = (target != null && target > BigDecimal.ZERO) ||
                state.quantities.values.any { it > 0 }
        _uiState.update { it.copy(result = result, hasActiveCount = hasData) }
    }

    private fun persistDenominations(denominations: List<Denomination>) {
        viewModelScope.launch { repository.save(denominations) }
    }

    private fun generateDenominationId(existing: List<Denomination>): String {
        val existingIds = existing.map { it.id }.toSet()
        var counter = 1
        while (true) {
            val candidate = "d$counter"
            if (candidate !in existingIds) return candidate
            counter++
        }
    }

    private fun generateCurrencyId(existing: List<Currency>): String {
        val existingIds = existing.map { it.id }.toSet()
        var counter = 1
        while (true) {
            val candidate = "c$counter"
            if (candidate !in existingIds) return candidate
            counter++
        }
    }

    private fun generateProductId(existing: List<Product>): String {
        val existingIds = existing.map { it.id }.toSet()
        var counter = 1
        while (true) {
            val candidate = "p$counter"
            if (candidate !in existingIds) return candidate
            counter++
        }
    }

    companion object {
        /** Returns products that have a price in the given currency. */
        fun productsWithPrice(products: List<Product>, currencyId: String): List<Product> =
            products.filter { it.hasPriceIn(currencyId) }

        /** Returns products with stock reduced by the sold quantity per selection.
         *  Quantities never restore; over-selling may push stock negative (warn-and-allow). */
        fun applyStockDeduction(
            products: List<Product>,
            selections: List<ProductSelection>
        ): List<Product> {
            val soldByProduct: Map<String, BigDecimal> = selections
                .filter { !it.productId.isNullOrBlank() }
                .groupingBy { it.productId!! }
                .fold(BigDecimal.ZERO) { acc, s -> acc.add(s.quantity()) }

            return products.map { product ->
                val sold = soldByProduct[product.id] ?: BigDecimal.ZERO
                if (sold.signum() <= 0) product
                else product.copy(stock = product.stock.subtract(sold).setScale(Money.SCALE))
            }
        }

        /** Returns products with stock reduced by a merma (write-off) quantity for one product.
         *  Other products untouched. Over-write-off may push stock negative (consistent with
         *  applyStockDeduction's warn-and-allow behavior). Unknown id is a no-op. */
        fun applyWriteoff(
            products: List<Product>,
            productId: String,
            quantity: BigDecimal
        ): List<Product> {
            return products.map { product ->
                if (product.id != productId) product
                else product.copy(stock = product.stock.subtract(quantity).setScale(Money.SCALE))
            }
        }

        /** Pure settlement logic for a "cobro": if [receivableId] names an OPEN receivable,
         *  returns the receivables list with that one flipped to SETTLED (settledAt = [now])
         *  plus the Payment recording the cash collected (amount = receivable.amount).
         *  Does not touch products/stock. If the id is unknown or the receivable is not
         *  OPEN, returns the original list unchanged and a null Payment (no-op). */
        fun settleReceivablePure(
            receivables: List<Receivable>,
            receivableId: String,
            paymentId: String,
            now: Long
        ): Pair<List<Receivable>, Payment?> {
            val receivable = receivables.firstOrNull { it.id == receivableId && it.status == ReceivableStatus.OPEN }
                ?: return receivables to null

            val payment = Payment(
                id = paymentId,
                at = now,
                receivableId = receivable.id,
                debtorName = receivable.debtorName,
                amount = receivable.amount,
                currencyId = receivable.currencyId
            )

            val updated = receivables.map {
                if (it.id == receivableId) it.copy(status = ReceivableStatus.SETTLED, settledAt = now) else it
            }
            return updated to payment
        }
    }
}