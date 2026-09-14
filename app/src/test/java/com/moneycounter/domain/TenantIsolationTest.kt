package com.moneycounter.domain

import com.moneycounter.repository.ClosingJson
import com.moneycounter.repository.MovementJson
import com.moneycounter.viewmodel.MoneyCounterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Plan 024 (FASE 12 security hardening): deterministic proof that a tenant/branch
 * cannot read another tenant's data and a SELLER cannot escalate to stock
 * modification. Pure JVM — two orgs / two branches / two sellers fixtures, no
 * Android, no backend.
 */
class TenantIsolationTest {

    private val orgA = "org-a"
    private val orgB = "org-b"
    private val brA = "br-a"
    private val brB = "br-b"
    private val s1 = "seller-1"
    private val s2 = "seller-2"

    private fun movement(
        id: String,
        organizationId: String = "",
        branchId: String = "",
        sellerUid: String = "",
        type: MovementType = MovementType.VENTA
    ) = Movement(
        id = id,
        at = 1000L,
        type = type,
        currencyId = "cup",
        amount = BigDecimal("10.00").setScale(Money.SCALE),
        organizationId = organizationId,
        branchId = branchId,
        sellerUid = sellerUid
    )

    private fun closing(
        id: String,
        organizationId: String = "",
        branchId: String = "",
        sellerUid: String = ""
    ) = Closing(
        id = id,
        at = 1000L,
        currencyId = "cup",
        movementIds = listOf("m-1"),
        totalsByType = emptyMap(),
        netCash = BigDecimal("10.00").setScale(Money.SCALE),
        stockSnapshot = emptyList(),
        organizationId = organizationId,
        branchId = branchId,
        sellerUid = sellerUid,
        scope = ClosingScope.BRANCH
    )

    private fun stockItem(
        id: String,
        organizationId: String,
        branchId: String,
        productId: String,
        quantity: String
    ) = StockItem(
        id = id,
        organizationId = organizationId,
        branchId = branchId,
        productId = productId,
        quantity = BigDecimal(quantity).setScale(Money.SCALE),
        updatedAt = 1000L
    )

    // ---- 1. Cross-tenant: org A never leaks to an org-B OWNER view ----

    @Test
    fun `cross-tenant OWNER of org A sees org A movements only, never org B`() {
        val movements = listOf(
            movement("a-br-a", orgA, brA, s1),
            movement("a-br-b", orgA, brB, s2),
            movement("b-br-a", orgB, brA, s1)
        )
        val visible = visibleForRole(movements, Role.OWNER, orgId = orgA, branchId = brA, uid = s1)
        assertEquals(listOf("a-br-a", "a-br-b"), visible.map { it.id })
    }

    @Test
    fun `cross-tenant OWNER of org B never sees org A movements`() {
        val movements = listOf(
            movement("a-br-a", orgA, brA, s1),
            movement("b-br-a", orgB, brA, s1),
            movement("b-br-b", orgB, brB, s2)
        )
        val visible = visibleForRole(movements, Role.OWNER, orgId = orgB, branchId = brB, uid = s1)
        assertEquals(listOf("b-br-a", "b-br-b"), visible.map { it.id })
    }

    // ---- 2. Cross-branch: branch A views never include branch B ----

    @Test
    fun `cross-branch ADMIN of br-a never sees br-b movements or closings`() {
        val movements = listOf(
            movement("in-br-a", orgA, brA, s1),
            movement("in-br-a-2", orgA, brA, s2),
            movement("in-br-b", orgA, brB, s1),
            movement("other-org-br-a", orgB, brA, s1)
        )
        val visible = visibleForRole(movements, Role.ADMIN, orgId = orgA, branchId = brA, uid = "")
        assertEquals(listOf("in-br-a", "in-br-a-2", "other-org-br-a"), visible.map { it.id })

        val closings = listOf(
            closing("c-br-a", orgA, brA),
            closing("c-br-b", orgA, brB),
            closing("c-legacy")
        )
        val closingsVisible = MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.ADMIN, organizationId = orgA, branchId = brA
        )
        assertEquals(listOf("c-br-a", "c-legacy"), closingsVisible.map { it.id })
    }

    @Test
    fun `cross-branch SELLER of br-a never sees br-b closings`() {
        val closings = listOf(
            closing("c-br-a", orgA, brA),
            closing("c-br-b", orgA, brB),
            closing("c-legacy")
        )
        val closingsVisible = MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.SELLER, organizationId = orgA, branchId = brA
        )
        assertEquals(listOf("c-br-a", "c-legacy"), closingsVisible.map { it.id })
    }

    // ---- 3. Role escalation SELLER -> stock (each asserted false) ----

    @Test
    fun `SELLER permission service denies every stock-product-catalog-expense escalation`() {
        val p = RolePermissionService(Role.SELLER)
        assertFalse("SELLER must not add stock", p.canAddStock())
        assertFalse("SELLER must not edit stock", p.canEditStock())
        assertFalse("SELLER must not register writeoffs", p.canRegisterWriteoff())
        assertFalse("SELLER must not create products", p.canCreateProduct())
        assertFalse("SELLER must not edit products", p.canEditProduct())
        assertFalse("SELLER must not delete products", p.canDeleteProduct())
        assertFalse("SELLER must not register expenses", p.canRegisterExpense())
    }

    // ---- 4. Membership escalation ADMIN -> org ----

    @Test
    fun `ADMIN cannot view organization history and never crosses to another org`() {
        val p = RolePermissionService(Role.ADMIN)
        assertFalse("ADMIN must not view organization history", p.canViewOrganizationHistory())

        val movements = listOf(
            movement("a-br-a", orgA, brA, s1),
            movement("b-other", orgB, brB, s1)
        )
        val enclosingOrgs = listOf(
            visibleForRole(movements, Role.ADMIN, orgId = orgA, branchId = brA, uid = ""),
            visibleForRole(movements, Role.ADMIN, orgId = orgB, branchId = brB, uid = "")
        )
        assertEquals(listOf("a-br-a"), enclosingOrgs[0].map { it.id })
        assertEquals(listOf("b-other"), enclosingOrgs[1].map { it.id })

        val closings = listOf(closing("c-a", orgA, brA), closing("c-b", orgB, brB))
        val closingsVisible = MoneyCounterViewModel.closingsVisibleForRole(
            closings, Role.ADMIN, organizationId = orgA, branchId = brA
        )
        assertEquals(listOf("c-a"), closingsVisible.map { it.id })
    }

    // ---- 5. OWNER cross-organization: sees org A, not org B ----

    @Test
    fun `OWNER sees whole org A across branches but never org B`() {
        val movements = listOf(
            movement("a-br-a", orgA, brA, s1),
            movement("a-br-b", orgA, brB, s2),
            movement("b-br-a", orgB, brA, s1),
            movement("legacy-empty")
        )
        val visible = visibleForRole(movements, Role.OWNER, orgId = orgA, branchId = brA, uid = "")
        assertEquals(listOf("a-br-a", "a-br-b", "legacy-empty"), visible.map { it.id })
    }

    // ---- 6. Null role: no permissions and no visibility at all ----

    @Test
    fun `null role grants no permission and sees no history (plan 033)`() {
        val movements = listOf(
            movement("a", orgA, brA, s1),
            movement("b", orgB, brB, s2),
            movement("legacy-empty")
        )
        val visible = visibleForRole(movements, null, orgId = "", branchId = "", uid = "")
        assertTrue("a null role must not see any movement", visible.isEmpty())

        val closings = listOf(closing("c-a", orgA, brA), closing("c-b", orgB, brB))
        val closingsVisible = MoneyCounterViewModel.closingsVisibleForRole(
            closings, null, organizationId = "", branchId = ""
        )
        assertTrue("a null role must not see any closing", closingsVisible.isEmpty())

        // Plan 033 reversed this: a null role used to grant every operational permission,
        // which is the escalation the plan closes. It now grants none.
        val p: PermissionService = RolePermissionService(null)
        assertFalse("a null role must not sell", p.canSell())
        assertFalse("a null role must not touch stock", p.canAddStock())
        assertFalse("a null role must not manage the catalog", p.canManageCatalog())
    }

    // ---- 7. SELLER own-history: only own movements, other sellers excluded ----

    @Test
    fun `SELLER sees own movements only even with other sellers in the same branch`() {
        val movements = listOf(
            movement("own", orgA, brA, s1),
            movement("s2-same-branch", orgA, brA, s2),
            movement("s1-other-branch", orgA, brB, s1),
            movement("s2-other-org", orgB, brA, s2)
        )
        val visible = visibleForRole(movements, Role.SELLER, orgId = orgA, branchId = brA, uid = s1)
        assertEquals(listOf("own"), visible.map { it.id })
    }

    // ---- 8. Stamp idempotency: never overwrites a filled tenant stamp ----

    @Test
    fun `stampTenant never overwrites filled org and branch on movements`() {
        val stamped = movement("m", orgA, brA, s1)
        val again = MovementJson.stampTenant(listOf(stamped), orgB, brB).first()
        assertEquals(orgA, again.organizationId)
        assertEquals(brA, again.branchId)
    }

    @Test
    fun `stampTenant fills only the blank field and leaves the filled one alone`() {
        val oneFilled = movement("m", organizationId = orgA, branchId = "")
        val stamped = MovementJson.stampTenant(listOf(oneFilled), orgB, brB).first()
        assertEquals(orgA, stamped.organizationId)
        assertEquals(brB, stamped.branchId)

        val otherFilled = movement("m", organizationId = "", branchId = brA)
        val stamped2 = MovementJson.stampTenant(listOf(otherFilled), orgB, brB).first()
        assertEquals(orgB, stamped2.organizationId)
        assertEquals(brA, stamped2.branchId)
    }

    @Test
    fun `stampTenant never overwrites filled org and branch on closings`() {
        val stamped = closing("c", orgA, brA)
        val again = ClosingJson.stampTenant(listOf(stamped), orgB, brB).first()
        assertEquals(orgA, again.organizationId)
        assertEquals(brA, again.branchId)
    }

    // ---- 9. Stock isolation: branch-A rows never touch branch-B rows ----

    @Test
    fun `increaseStock touches only the matching product in the matching branch`() {
        val items = listOf(
            stockItem("si-a1", orgA, brA, "p1", "10"),
            stockItem("si-b1", orgA, brB, "p1", "10")
        )
        val increased = increaseStock(items, "p1", BigDecimal("2"), orgA, brA)
        assertEquals("12.00", increased.first { it.id == "si-a1" }.quantity.toPlainString())
        assertEquals("10.00", increased.first { it.id == "si-b1" }.quantity.toPlainString())
    }

    @Test
    fun `decreaseStock touches only the matching product in the matching branch`() {
        val items = listOf(
            stockItem("si-a1", orgA, brA, "p1", "10"),
            stockItem("si-b1", orgA, brB, "p1", "10")
        )
        val decreased = decreaseStock(items, "p1", BigDecimal("3"), orgA, brA)
        assertEquals("7.00", decreased.first { it.id == "si-a1" }.quantity.toPlainString())
        assertEquals("10.00", decreased.first { it.id == "si-b1" }.quantity.toPlainString())
    }

    @Test
    fun `adjustStock touches only the matching product in the matching branch`() {
        val items = listOf(
            stockItem("si-a1", orgA, brA, "p1", "10"),
            stockItem("si-b1", orgB, brB, "p1", "10")
        )
        val adjusted = adjustStock(items, "p1", BigDecimal("50"), orgA, brA)
        assertEquals("50.00", adjusted.first { it.id == "si-a1" }.quantity.toPlainString())
        assertEquals("10.00", adjusted.first { it.id == "si-b1" }.quantity.toPlainString())
    }

    // ---- SELLER sale path stays open (money recording, NOT to be gated) ----

    @Test
    fun `SELLER sale and fiado recording permissions stay enabled`() {
        val p = RolePermissionService(Role.SELLER)
        assertTrue("SELLER may record a cash sale", p.canSell())
        assertTrue("SELLER may record a credit sale and collect", p.canRegisterCreditSaleAndCollect())
        assertTrue("fiado pure guard accepts a named debtor with products", MoneyCounterViewModel.canRegisterCreditSale(true, "Juan"))
    }
}