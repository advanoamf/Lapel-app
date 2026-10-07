package com.lapel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lapel.app.data.local.entity.DesignEntity
import com.lapel.app.data.local.entity.StockBatchCostEntity
import com.lapel.app.data.local.entity.StockBatchEntity
import com.lapel.app.data.local.entity.StockSaleEntity
import kotlinx.coroutines.flow.Flow

data class StockBatchSummaryRow(
    val batchId: Long,
    val designId: Long,
    val designName: String,
    val quantityReceived: Int,
    val writtenOff: Int,
    val sold: Int,
    val revenue: Long,
    val paid: Long,
    val costs: Long,
    val shippingCost: Long,
)

val StockBatchSummaryRow.remaining: Int get() = quantityReceived - writtenOff - sold
val StockBatchSummaryRow.outstandingAgorot: Long get() = maxOf(0L, revenue - paid)

@Dao
interface StockDao {
    @Query("SELECT * FROM designs ORDER BY name COLLATE NOCASE")
    fun observeDesigns(): Flow<List<DesignEntity>>

    @Insert suspend fun insertDesign(design: DesignEntity): Long
    @Update suspend fun updateDesign(design: DesignEntity)

    @Insert suspend fun insertBatch(batch: StockBatchEntity): Long
    @Update suspend fun updateBatch(batch: StockBatchEntity)
    @Insert suspend fun insertBatchCosts(costs: List<StockBatchCostEntity>)

    @Query("SELECT * FROM stock_sales WHERE batchId = :batchId ORDER BY soldOn DESC, id DESC")
    fun observeSales(batchId: Long): Flow<List<StockSaleEntity>>

    @Insert suspend fun insertSale(sale: StockSaleEntity): Long
    @Update suspend fun updateSale(sale: StockSaleEntity)

    @Query(
        """
        SELECT b.id AS batchId, d.id AS designId, d.name AS designName, b.quantityReceived, b.writtenOff,
               (SELECT COALESCE(SUM(s.quantity), 0) FROM stock_sales s WHERE s.batchId = b.id) AS sold,
               (SELECT COALESCE(SUM(s.quantity * s.unitPriceAgorot + s.shippingChargedAgorot), 0)
                  FROM stock_sales s WHERE s.batchId = b.id) AS revenue,
               (SELECT COALESCE(SUM(p.amountAgorot), 0) FROM payments p
                  JOIN stock_sales s ON s.id = p.stockSaleId WHERE s.batchId = b.id) AS paid,
               (SELECT COALESCE(SUM(k.amountAgorot), 0) FROM stock_batch_costs k WHERE k.batchId = b.id) AS costs,
               (SELECT COALESCE(SUM(s.shippingCostAgorot), 0) FROM stock_sales s WHERE s.batchId = b.id) AS shippingCost
        FROM stock_batches b
        JOIN designs d ON d.id = b.designId
        ORDER BY b.purchasedOn DESC
        """,
    )
    fun observeBatchSummaries(): Flow<List<StockBatchSummaryRow>>
}
