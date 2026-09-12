package com.moneycounter.domain

import java.math.BigDecimal

/** Branch-scoped stock row (master plan FASE 3/4, sections 19-26).
 *  One row per (organization, branch, product). `Product.stock` stays as a
 *  compatibility copy until every consumer migrates. Quantity is warn-and-allow:
 *  over-selling may push it negative (consistent with `applyStockDeduction`), and
 *  [StockJson] refuses to persist negative rows. */
data class StockItem(
    val id: String,
    val organizationId: String,
    val branchId: String,
    val productId: String,
    val quantity: BigDecimal,
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank())
        require(organizationId.isNotBlank())
        require(branchId.isNotBlank())
        require(productId.isNotBlank())
    }
}

/** Increases the matched item's quantity by [amount] (warn-and-allow: the result may
 *  go negative, consistent with `applyStockDeduction`). Rows are keyed by
 *  (organizationId, branchId, productId): only the single row matching ALL three is
 *  touched, so a branch-A operation never leaks into branch-B rows of the same
 *  product. No match is a no-op — callers create the row first. Only the touched
 *  item gets a bumped [StockItem.updatedAt]. */
fun increaseStock(
    items: List<StockItem>,
    productId: String,
    amount: BigDecimal,
    organizationId: String,
    branchId: String
): List<StockItem> =
    items.map { item ->
        if (item.productId != productId || item.organizationId != organizationId || item.branchId != branchId) item
        else item.copy(
            quantity = item.quantity.add(amount).setScale(Money.SCALE),
            updatedAt = System.currentTimeMillis()
        )
    }

/** Decreases the matched item's quantity by [amount] (warn-and-allow: the result may
 *  go negative, consistent with `applyStockDeduction`). Rows are keyed by
 *  (organizationId, branchId, productId): only the single row matching ALL three is
 *  touched; other branch/org rows of the same product never change. No match is a
 *  no-op. Only the touched item gets a bumped [StockItem.updatedAt]. */
fun decreaseStock(
    items: List<StockItem>,
    productId: String,
    amount: BigDecimal,
    organizationId: String,
    branchId: String
): List<StockItem> =
    items.map { item ->
        if (item.productId != productId || item.organizationId != organizationId || item.branchId != branchId) item
        else item.copy(
            quantity = item.quantity.subtract(amount).setScale(Money.SCALE),
            updatedAt = System.currentTimeMillis()
        )
    }

/** Sets the matched item's quantity to the absolute value [absoluteQuantity].
 *  Rows are keyed by (organizationId, branchId, productId): only the single row
 *  matching ALL three is touched; other branch/org rows of the same product never
 *  change. No match is a no-op. Only the touched item gets a bumped
 *  [StockItem.updatedAt]. */
fun adjustStock(
    items: List<StockItem>,
    productId: String,
    absoluteQuantity: BigDecimal,
    organizationId: String,
    branchId: String
): List<StockItem> =
    items.map { item ->
        if (item.productId != productId || item.organizationId != organizationId || item.branchId != branchId) item
        else item.copy(
            quantity = absoluteQuantity.setScale(Money.SCALE),
            updatedAt = System.currentTimeMillis()
        )
    }

/** Idempotent backfill: for every [products] item with no [StockItem] row in the
 *  given (organization, branch), creates one row seeded with `Product.stock` as the
 *  source (quantity). Rows for other orgs/branches are never touched, and existing
 *  rows are never overwritten. Persists nothing — returns the extended list; the
 *  caller persists only when rows were actually created. */
fun backfillStock(
    items: List<StockItem>,
    products: List<Product>,
    organizationId: String,
    branchId: String,
    now: Long = System.currentTimeMillis()
): List<StockItem> {
    val existingProductIds = items
        .filter { it.organizationId == organizationId && it.branchId == branchId }
        .mapTo(mutableSetOf()) { it.productId }
    val created = products.mapNotNull { product ->
        if (product.id in existingProductIds) null
        else StockItem(
            id = "si-${product.id}",
            organizationId = organizationId,
            branchId = branchId,
            productId = product.id,
            quantity = product.stock,
            updatedAt = now
        )
    }
    if (created.isEmpty()) return items
    return items + created
}

/** Pure merged read: maps `productId -> quantity` for the [branchId]'s rows.
 *  Rows are looked up by branch only; org scoping lives in the calling read path
 *  ([resolveBranchStock] gates on the context org as well). */
fun stockForBranch(items: List<StockItem>, branchId: String): Map<String, BigDecimal> =
    items
        .filter { it.branchId == branchId }
        .associate { it.productId to it.quantity }

/** Pure merged read (master sections 19-26) for one product: the [StockItem]
 *  quantity for the (organization, branch) when a row exists — org and branch are
 *  mandatory filters, so rows from other orgs/branches never leak — else the
 *  `Product.stock` compatibility copy. Unknown product → Money.ZERO. */
fun resolveBranchStock(
    items: List<StockItem>,
    products: List<Product>,
    organizationId: String,
    branchId: String,
    productId: String
): BigDecimal {
    val row = items.firstOrNull {
        it.organizationId == organizationId && it.branchId == branchId && it.productId == productId
    }
    if (row != null) return row.quantity
    return products.firstOrNull { it.id == productId }?.stock ?: Money.ZERO
}