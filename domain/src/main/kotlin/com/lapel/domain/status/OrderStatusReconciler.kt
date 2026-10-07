package com.lapel.domain.status

import com.lapel.domain.model.ChangeSource
import com.lapel.domain.model.FulfillmentStatus
import com.lapel.domain.model.FulfillmentStatus.CANCELLED
import com.lapel.domain.model.FulfillmentStatus.COMPLETED
import com.lapel.domain.model.FulfillmentStatus.DELIVERED
import com.lapel.domain.model.FulfillmentStatus.SHIPPED
import com.lapel.domain.model.ShipmentStatus

data class StatusTransition(
    val from: FulfillmentStatus,
    val to: FulfillmentStatus,
    val source: ChangeSource,
)

/**
 * Moves an order's fulfillment status forward from its FedEx shipments and payments.
 *
 * Rules: never moves backwards, never touches COMPLETED or CANCELLED, marks DELIVERED only
 * when every shipment is delivered, and completes a delivered order once it is fully paid.
 */
object OrderStatusReconciler {

    private val moving = setOf(
        ShipmentStatus.IN_TRANSIT,
        ShipmentStatus.OUT_FOR_DELIVERY,
        ShipmentStatus.DELIVERED,
        ShipmentStatus.EXCEPTION,
    )

    /** Returns the transitions to apply, in order (at most two: shipment step, then completion). */
    fun reconcile(
        current: FulfillmentStatus,
        shipments: List<ShipmentStatus>,
        fullyPaid: Boolean,
    ): List<StatusTransition> {
        if (current == COMPLETED || current == CANCELLED) return emptyList()

        val transitions = mutableListOf<StatusTransition>()
        var status = current

        val fromShipments = when {
            shipments.isEmpty() -> null
            shipments.all { it == ShipmentStatus.DELIVERED } -> DELIVERED
            shipments.any { it in moving } -> SHIPPED
            else -> null
        }
        if (fromShipments != null && fromShipments.ordinal > status.ordinal) {
            transitions += StatusTransition(status, fromShipments, ChangeSource.FEDEX_SYNC)
            status = fromShipments
        }

        if (status == DELIVERED && fullyPaid) {
            transitions += StatusTransition(status, COMPLETED, ChangeSource.SYSTEM)
        }
        return transitions
    }
}
