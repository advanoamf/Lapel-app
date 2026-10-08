package com.lapel.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lapel.app.data.local.dao.CustomerDao
import com.lapel.app.data.local.dao.OrderDao
import com.lapel.app.data.local.dao.PaymentDao
import com.lapel.app.data.local.dao.ReminderLogDao
import com.lapel.app.data.local.dao.ShipmentDao
import com.lapel.app.data.local.dao.StockDao
import com.lapel.app.data.local.entity.CustomerEntity
import com.lapel.app.data.local.entity.DesignEntity
import com.lapel.app.data.local.entity.OrderCostEntity
import com.lapel.app.data.local.entity.OrderEntity
import com.lapel.app.data.local.entity.OrderItemEntity
import com.lapel.app.data.local.entity.PaymentEntity
import com.lapel.app.data.local.entity.ReminderLogEntity
import com.lapel.app.data.local.entity.ShipmentEntity
import com.lapel.app.data.local.entity.StatusChangeEntity
import com.lapel.app.data.local.entity.StockBatchCostEntity
import com.lapel.app.data.local.entity.StockBatchEntity
import com.lapel.app.data.local.entity.StockSaleEntity
import com.lapel.app.data.local.entity.TrackingEventEntity

@Database(
    entities = [
        CustomerEntity::class,
        DesignEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OrderCostEntity::class,
        StatusChangeEntity::class,
        PaymentEntity::class,
        ShipmentEntity::class,
        TrackingEventEntity::class,
        ReminderLogEntity::class,
        StockBatchEntity::class,
        StockBatchCostEntity::class,
        StockSaleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LapelDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun paymentDao(): PaymentDao
    abstract fun shipmentDao(): ShipmentDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun stockDao(): StockDao

    companion object {
        const val NAME = "lapel.db"
    }
}
