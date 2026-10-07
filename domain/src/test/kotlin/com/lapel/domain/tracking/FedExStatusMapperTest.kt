package com.lapel.domain.tracking

import com.lapel.domain.model.ShipmentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FedExStatusMapperTest {
    @Test fun `maps known codes`() {
        assertEquals(ShipmentStatus.LABEL_CREATED, FedExStatusMapper.map("OC"))
        assertEquals(ShipmentStatus.IN_TRANSIT, FedExStatusMapper.map("PU"))
        assertEquals(ShipmentStatus.IN_TRANSIT, FedExStatusMapper.map("it"))
        assertEquals(ShipmentStatus.OUT_FOR_DELIVERY, FedExStatusMapper.map("OD"))
        assertEquals(ShipmentStatus.DELIVERED, FedExStatusMapper.map("DL"))
        assertEquals(ShipmentStatus.EXCEPTION, FedExStatusMapper.map("DE"))
        assertEquals(ShipmentStatus.UNKNOWN, FedExStatusMapper.map("ZZ"))
        assertEquals(ShipmentStatus.UNKNOWN, FedExStatusMapper.map(null))
    }

    @Test fun `validates tracking numbers`() {
        assertTrue(FedExStatusMapper.isValidTrackingNumber("7946 1234 5678"))
        assertFalse(FedExStatusMapper.isValidTrackingNumber("RR0001485077P"))
        assertFalse(FedExStatusMapper.isValidTrackingNumber("12345"))
    }
}
