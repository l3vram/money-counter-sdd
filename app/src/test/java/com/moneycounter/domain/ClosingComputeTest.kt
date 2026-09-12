package com.moneycounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ClosingComputeTest {

    private fun movement(
        id: String,
        type: MovementType,
        amount: String,
        currencyId: String = "cup",
        closingId: String? = null,
        sellerUid: String = "",
        branchId: String = ""
    ) = Movement(
        id = id,
        at = 1000L,
        type = type,
        currencyId = currencyId,
        amount = BigDecimal(amount).setScale(Money.SCALE),
        closingId = closingId,
        sellerUid = sellerUid,
        branchId = branchId
    )

    private fun product(name: String, unit: String, stock: String) = Product(
        id = name,
        name = name,
        unit = unit,
        stock = BigDecimal(stock).setScale(Money.SCALE)
    )

    @Test
    fun `totalsByType sums amount per type`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("v2", MovementType.VENTA, "50.00"),
            movement("g1", MovementType.GASTO, "20.00"),
            movement("c1", MovementType.COBRO, "30.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup")

        assertEquals(BigDecimal("150.00"), closing.totalsByType.getValue(MovementType.VENTA))
        assertEquals(BigDecimal("20.00"), closing.totalsByType.getValue(MovementType.GASTO))
        assertEquals(BigDecimal("30.00"), closing.totalsByType.getValue(MovementType.COBRO))
        assertEquals(BigDecimal("0.00"), closing.totalsByType.getValue(MovementType.MERMA))
    }

    @Test
    fun `netCash is VENTA plus COBRO minus GASTO, excluding fiado merma alta entrada`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("c1", MovementType.COBRO, "40.00"),
            movement("g1", MovementType.GASTO, "10.00"),
            movement("f1", MovementType.VENTA_FIADO, "500.00"),
            movement("m1", MovementType.MERMA, "15.00"),
            movement("a1", MovementType.ALTA, "1000.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup")

        // 100 + 40 - 10 = 130, ignoring fiado/merma/alta entirely
        assertEquals(BigDecimal("130.00"), closing.netCash)
    }

    @Test
    fun `stockSnapshot mirrors products with nonzero stock`() {
        val products = listOf(
            product("Arroz", "Lb", "10.00"),
            product("Frijoles", "Lb", "0.00"),
            product("Azucar", "Lb", "-2.00")
        )
        val closing = computeClosing("cl1", 2000L, emptyList(), products, "cup")

        assertEquals(2, closing.stockSnapshot.size)
        assertTrue(closing.stockSnapshot.any { it.name == "Arroz" && it.quantity == BigDecimal("10.00") })
        assertTrue(closing.stockSnapshot.any { it.name == "Azucar" && it.quantity == BigDecimal("-2.00") })
        assertTrue(closing.stockSnapshot.none { it.name == "Frijoles" })
    }

    @Test
    fun `empty selection yields zero totals, zero netCash, movementIds empty`() {
        val closing = computeClosing("cl1", 2000L, emptyList(), emptyList(), "cup")

        assertTrue(closing.movementIds.isEmpty())
        assertTrue(closing.stockSnapshot.isEmpty())
        assertEquals(BigDecimal("0.00"), closing.netCash)
        MovementType.entries.forEach { type ->
            assertEquals(BigDecimal("0.00"), closing.totalsByType.getValue(type))
        }
    }

    @Test
    fun `movementIds carries the ids of the movements given`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("v2", MovementType.VENTA, "50.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup")
        assertEquals(listOf("v1", "v2"), closing.movementIds)
    }

    // ---- no double-close: openMovements pure filter ----

    @Test
    fun `openMovements excludes movements already stamped with a closingId`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00", closingId = null),
            movement("v2", MovementType.VENTA, "50.00", closingId = "cl0"),
            movement("v3", MovementType.VENTA, "10.00", currencyId = "usd", closingId = null)
        )
        val open = com.moneycounter.viewmodel.MoneyCounterViewModel.openMovementsPure(movements, "cup")
        assertEquals(listOf("v1"), open.map { it.id })
    }

    // ---- plan 023: scope stamping in computeClosing ----

    @Test
    fun `default scope is BRANCH`() {
        val closing = computeClosing("cl1", 2000L, listOf(movement("v1", MovementType.VENTA, "10.00")), emptyList(), "cup")
        assertEquals(ClosingScope.BRANCH, closing.scope)
    }

    @Test
    fun `scope SELLER stamps scope and tallies only the given seller-scoped movements`() {
        val closing = computeClosing(
            "cl1", 2000L,
            listOf(movement("v1", MovementType.VENTA, "100.00", sellerUid = "s1")),
            emptyList(), "cup",
            scope = ClosingScope.SELLER
        )
        assertEquals(ClosingScope.SELLER, closing.scope)
        assertEquals(listOf("v1"), closing.movementIds)
        assertEquals(BigDecimal("100.00"), closing.netCash)
    }

    @Test
    fun `scope BRANCH includes all given movements without changing totals semantics`() {
        val movements = listOf(
            movement("v1", MovementType.VENTA, "100.00"),
            movement("v2", MovementType.VENTA, "50.00"),
            movement("g1", MovementType.GASTO, "20.00")
        )
        val closing = computeClosing("cl1", 2000L, movements, emptyList(), "cup", scope = ClosingScope.BRANCH)
        assertEquals(ClosingScope.BRANCH, closing.scope)
        assertEquals(listOf("v1", "v2", "g1"), closing.movementIds)
        assertEquals(BigDecimal("150.00"), closing.totalsByType.getValue(MovementType.VENTA))
        assertEquals(BigDecimal("130.00"), closing.netCash)
    }

    // ---- plan 023: VM pure closing-scope resolution ----

    private fun closingWithTenant(
        id: String,
        organizationId: String = "",
        branchId: String = ""
    ) = Closing(
        id = id,
        at = 1L,
        currencyId = "cup",
        movementIds = listOf("m1"),
        totalsByType = emptyMap(),
        netCash = BigDecimal("0.00"),
        stockSnapshot = emptyList(),
        organizationId = organizationId,
        branchId = branchId
    )

    @Test
    fun `resolveClosingSelection seller request yields only own movements and scope SELLER`() {
        val own = movement("v1", MovementType.VENTA, "100.00", sellerUid = "s1")
        val other = movement("v2", MovementType.VENTA, "50.00", sellerUid = "s2")
        val legacy = movement("v3", MovementType.VENTA, "25.00")
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v1", "v2", "v3"),
            visibleMovements = listOf(own, other, legacy),
            canCreateBranchClosing = false,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(ClosingScope.SELLER, selection?.scope)
        assertEquals(listOf("v1", "v3"), selection?.selected?.map { it.id })
    }

    @Test
    fun `resolveClosingSelection seller cannot close another seller requests`() {
        val other = movement("v2", MovementType.VENTA, "50.00", sellerUid = "s2")
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v2"),
            visibleMovements = listOf(other),
            canCreateBranchClosing = false,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(null, selection)
    }

    @Test
    fun `resolveClosingSelection drops already-closed movements`() {
        val open = movement("v1", MovementType.VENTA, "100.00", sellerUid = "s1")
        val closed = movement("v2", MovementType.VENTA, "50.00", sellerUid = "s1", closingId = "cl0")
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v1", "v2"),
            visibleMovements = listOf(open, closed),
            canCreateBranchClosing = false,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(ClosingScope.SELLER, selection?.scope)
        assertEquals(listOf("v1"), selection?.selected?.map { it.id })
    }

    @Test
    fun `resolveClosingSelection branch-capable request yields scope BRANCH over requested movements`() {
        val a = movement("v1", MovementType.VENTA, "100.00", sellerUid = "s1", branchId = "br-1")
        val b = movement("v2", MovementType.VENTA, "50.00", sellerUid = "s2", branchId = "br-1")
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v1", "v2"),
            visibleMovements = listOf(a, b),
            canCreateBranchClosing = true,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(ClosingScope.BRANCH, selection?.scope)
        assertEquals(setOf("v1", "v2"), selection?.selected?.map { it.id }?.toSet())
    }

    @Test
    fun `resolveClosingSelection branch-capable caller excludes movements of another branch`() {
        val current = movement("v1", MovementType.VENTA, "100.00", sellerUid = "s1", branchId = "br-1")
        val otherBranch = movement("v2", MovementType.VENTA, "50.00", sellerUid = "s2", branchId = "br-2")
        val legacy = movement("v3", MovementType.VENTA, "25.00")
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v1", "v2", "v3"),
            visibleMovements = listOf(current, otherBranch, legacy),
            canCreateBranchClosing = true,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(ClosingScope.BRANCH, selection?.scope)
        assertEquals(listOf("v1", "v3"), selection?.selected?.map { it.id })
    }

    @Test
    fun `resolveClosingSelection branch-capable caller with only other-branch movements returns null`() {
        val otherBranch = movement("v2", MovementType.VENTA, "50.00", sellerUid = "s2", branchId = "br-2")
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v2"),
            visibleMovements = listOf(otherBranch),
            canCreateBranchClosing = true,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(null, selection)
    }

    @Test
    fun `resolveClosingSelection denies when no scope is permitted`() {
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v1"),
            visibleMovements = listOf(movement("v1", MovementType.VENTA, "100.00")),
            canCreateBranchClosing = false,
            canCreateSellerClosing = false,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(null, selection)
    }

    @Test
    fun `resolveClosingSelection returns null when nothing remains after scoping`() {
        val selection = com.moneycounter.viewmodel.MoneyCounterViewModel.resolveClosingSelection(
            requestedIds = setOf("v9"),
            visibleMovements = listOf(movement("v1", MovementType.VENTA, "100.00")),
            canCreateBranchClosing = true,
            canCreateSellerClosing = true,
            sellerUid = "s1",
            branchId = "br-1"
        )
        assertEquals(null, selection)
    }

    // ---- plan 023: closingsVisibleForRole ----

    @Test
    fun `closingsVisibleForRole null role sees everything`() {
        val closings = listOf(
            closingWithTenant("a", organizationId = "org-1", branchId = "br-1"),
            closingWithTenant("b", organizationId = "org-2", branchId = "br-2"),
            closingWithTenant("c")
        )
        val visible = com.moneycounter.viewmodel.MoneyCounterViewModel.closingsVisibleForRole(
            closings, null, "org-1", "br-1"
        )
        assertEquals(setOf("a", "b", "c"), visible.map { it.id }.toSet())
    }

    @Test
    fun `closingsVisibleForRole OWNER sees own org plus blank-tenant legacy`() {
        val closings = listOf(
            closingWithTenant("a", organizationId = "org-1", branchId = "br-1"),
            closingWithTenant("b", organizationId = "org-2", branchId = "br-2"),
            closingWithTenant("c")
        )
        val visible = com.moneycounter.viewmodel.MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.OWNER, "org-1", "br-1"
        )
        assertEquals(setOf("a", "c"), visible.map { it.id }.toSet())
    }

    @Test
    fun `closingsVisibleForRole ADMIN and SELLER see own branch plus blank-tenant legacy`() {
        val closings = listOf(
            closingWithTenant("a", organizationId = "org-1", branchId = "br-1"),
            closingWithTenant("b", organizationId = "org-1", branchId = "br-2"),
            closingWithTenant("c")
        )
        val admin = com.moneycounter.viewmodel.MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.ADMIN, "org-1", "br-1"
        )
        val seller = com.moneycounter.viewmodel.MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.SELLER, "org-1", "br-1"
        )
        assertEquals(setOf("a", "c"), admin.map { it.id }.toSet())
        assertEquals(setOf("a", "c"), seller.map { it.id }.toSet())
    }

    @Test
    fun `closingsVisibleForRole SUPERUSER sees nothing`() {
        val closings = listOf(closingWithTenant("a", organizationId = "org-1", branchId = "br-1"))
        val visible = com.moneycounter.viewmodel.MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.SUPERUSER, "org-1", "br-1"
        )
        assertTrue(visible.isEmpty())
    }
}
