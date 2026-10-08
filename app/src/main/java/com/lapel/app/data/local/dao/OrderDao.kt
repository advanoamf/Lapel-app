package com.lapel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.StatusChangeEntity
import com.lapel.domain.model.CostType
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("$SUMMARY_SELECT ORDER BY o.orderDate DESC, o.id DESC")
    fun observeSummaries(): Flow<List<OrderSummaryRow>>

    @Query("$SUMMARY_SELECT WHERE o.customerId = :customerId ORDER BY o.orderDate DESC, o.id DESC")
    fun observeSummariesForCustomer(customerId: Long): Flow<List<OrderSummaryRow>>

    @Query("$SUMMARY_SELECT WHERE o.id = :orderId")
    suspend fun getSummary(orderId: Long): OrderSummaryRow?

    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeOrder(orderId: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrder(orderId: Long): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun count(): Int

    @Insert suspend fun insertOrder(order: OrderEntity): Long
    @Update suspend fun updateOrder(order: OrderEntity)
    @Delete suspend fun deleteOrder(order: OrderEntity)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id")
    fun observeItems(orderId: Long): Flow<List<OrderItemEntity>>

    @Insert suspend fun insertItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItemsForOrder(orderId: Long)
    @Update suspend fun updateItem(item: OrderItemEntity)
    @Delete suspend fun deleteItem(item: OrderItemEntity)

    @Query("SELECT * FROM order_costs WHERE orderId = :orderId ORDER BY id")
    fun observeCosts(orderId: Long): Flow<List<OrderCostEntity>>

    @Insert suspend fun insertCosts(costs: List<OrderCostEntity>)
    @Update suspend fun updateCost(cost: OrderCostEntity)

    @Query("DELETE FROM order_costs WHERE orderId = :orderId AND type = :type")
    suspend fun deleteCostsOfType(orderId: Long, type: CostType)
    @Delete suspend fun deleteCost(cost: OrderCostEntity)

    @Query("SELECT * FROM status_changes WHERE orderId = :orderId ORDER BY at")
    fun observeStatusChanges(orderId: Long): Flow<List<StatusChangeEntity>>

    @Insert suspend fun insertStatusChange(change: StatusChangeEntity)

    /** Highest plain-number order number, used to suggest the next one. */
    @Query("SELECT MAX(CAST(orderNumber AS INTEGER)) FROM orders WHERE orderNumber GLOB '[0-9]*' AND orderNumber NOT GLOB '*[^0-9]*'")
    suspend fun maxNumericOrderNumber(): Long?

    companion object {
        const val SUMMARY_SELECT = """
            SELECT o.id AS orderId, o.orderNumber, o.title, o.orderDate, o.fulfillmentStatus,
                   o.deliveryMethod, o.depositPercent, o.alibabaOrderedOn, o.deliveredAt, o.createdAt,
                   (o.shippingAddress IS NOT NULL AND TRIM(o.shippingAddress) != '') AS hasAddress,
                   o.addressSentToSupplier,
                   c.id AS customerId, c.name AS customerName, c.organization AS customerOrganization,
                   c.phone AS customerPhone, c.paymentTermsDays,
                   (SELECT COALESCE(SUM(i.quantitySold * i.unitPriceAgorot), 0)
                      FROM order_items i WHERE i.orderId = o.id) - o.discountAgorot AS sellingTotal,
                   (SELECT COALESCE(SUM(k.amountAgorot), 0)
                      FROM order_costs k WHERE k.orderId = o.id) AS costTotal,
                   (SELECT COALESCE(SUM(p.amountAgorot), 0)
                      FROM payments p WHERE p.orderId = o.id) AS paidTotal,
                   (SELECT COALESCE(SUM(k.amountAgorot), 0)
                      FROM order_costs k WHERE k.orderId = o.id AND k.type = 'ALIBABA_PAYMENT') AS alibabaPayment,
                   (SELECT COALESCE(SUM(i.quantityOrdered), 0)
                      FROM order_items i WHERE i.orderId = o.id) AS pinsOrdered,
                   (SELECT COUNT(*) FROM shipments s
                      WHERE s.orderId = o.id AND s.trackingActive = 1) AS activeShipments
            FROM orders o
            JOIN customers c ON c.id = o.customerId
        """
    }
}
