package com.lapel.domain.tracking

import com.lapel.domain.model.ShipmentStatus

/**
 * Maps FedEx Track API `latestStatusDetail.code` values to the app's shipment status.
 * Unrecognised codes map to UNKNOWN; the FedEx description is still shown to the user.
 */
object FedExStatusMapper {

    private val inTransit = setOf("PU", "IT", "AR", "DP", "AF", "CC", "CD", "PL", "HL")
    private val exception = setOf("DE", "SE", "CA", "RS", "DY")

    fun map(code: String?): ShipmentStatus = when (code?.trim()?.uppercase()) {
        null, "" -> ShipmentStatus.UNKNOWN
        "OC" -> ShipmentStatus.LABEL_CREATED
        "OD" -> ShipmentStatus.OUT_FOR_DELIVERY
        "DL" -> ShipmentStatus.DELIVERED
        in inTransit -> ShipmentStatus.IN_TRANSIT
        in exception -> ShipmentStatus.EXCEPTION
        else -> ShipmentStatus.UNKNOWN
    }

    /** FedEx tracking numbers are 12, 15, 20 or 22 digits. */
    fun isValidTrackingNumber(input: String): Boolean {
        val digits = input.filterNot { it.isWhitespace() }
        return digits.all { it.isDigit() } && digits.length in setOf(12, 15, 20, 22)
    }
}
