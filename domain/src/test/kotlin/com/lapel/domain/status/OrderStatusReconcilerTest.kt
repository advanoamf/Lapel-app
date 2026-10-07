package com.lapel.domain.status

import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.FulfillmentStatus.CANCELLED
import com.lapel.domain.model.FulfillmentStatus.COMPLETED
import com.lapel.domain.model.FulfillmentStatus.DELIVERED
import com.lapel.domain.model.FulfillmentStatus.ORDERED_FROM_ALIBABA
import com.lapel.domain.model.FulfillmentStatus.SHIPPED
import com.lapel.domain.model.ShipmentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderStatusReconcilerTest {

    @Test fun `in transit moves ordered to shipped`() {
        val t = OrderStatusReconciler.reconcile(ORDERED_FROM_ALIBABA, listOf(ShipmentStatus.IN_TRANSIT), fullyPaid = false)
        assertEquals(listOf(StatusTransition(ORDERED_FROM_ALIBABA, SHIPPED, ChangeSource.FEDEX_SYNC)), t)
    }

    @Test fun `label created is not dispatched`() {
        assertTrue(OrderStatusReconciler.reconcile(ORDERED_FROM_ALIBABA, listOf(ShipmentStatus.LABEL_CREATED), false).isEmpty())
    }

    @Test fun `delivered only when every shipment is delivered`() {
        val partial = OrderStatusReconciler.reconcile(
            SHIPPED, listOf(ShipmentStatus.DELIVERED, ShipmentStatus.IN_TRANSIT), fullyPaid = false,
        )
        assertTrue(partial.isEmpty())

        val all = OrderStatusReconciler.reconcile(
            SHIPPED, listOf(ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERED), fullyPaid = false,
        )
        assertEquals(DELIVERED, all.single().to)
    }

    @Test fun `never moves backwards`() {
        assertTrue(OrderStatusReconciler.reconcile(DELIVERED, listOf(ShipmentStatus.IN_TRANSIT), false).isEmpty())
    }

    @Test fun `manual final states are untouched`() {
        assertTrue(OrderStatusReconciler.reconcile(CANCELLED, listOf(ShipmentStatus.DELIVERED), true).isEmpty())
        assertTrue(OrderStatusReconciler.reconcile(COMPLETED, listOf(ShipmentStatus.DELIVERED), true).isEmpty())
    }

    @Test fun `delivered and paid completes automatically`() {
        val t = OrderStatusReconciler.reconcile(SHIPPED, listOf(ShipmentStatus.DELIVERED), fullyPaid = true)
        assertEquals(
            listOf(
                StatusTransition(SHIPPED, DELIVERED, ChangeSource.FEDEX_SYNC),
                StatusTransition(DELIVERED, COMPLETED, ChangeSource.SYSTEM),
            ),
            t,
        )
    }

    @Test fun `self pickup order delivered by hand completes when paid`() {
        val t = OrderStatusReconciler.reconcile(DELIVERED, emptyList(), fullyPaid = true)
        assertEquals(listOf(StatusTransition(DELIVERED, COMPLETED, ChangeSource.SYSTEM)), t)
    }
}
