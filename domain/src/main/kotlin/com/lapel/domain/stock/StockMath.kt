package com.lapel.domain.stock

import com.lapel.domain.model.Money
import com.lapel.domain.model.sum

/** One piece-by-piece sale of a stock design (e.g. 5 pins to one buyer, mailed). */
data class StockSaleLine(
    val quantity: Int,
    val unitPrice: Money,
    /** What the buyer paid for postage (e.g. ₪20), zero for pickup. */
    val shippingCharged: Money,
    /** What the post office actually cost. */
    val shippingCost: Money,
    val payments: List<Money>,
    val discount: Money = Money.ZERO,
) {
    val total: Money get() = unitPrice * quantity + shippingCharged - discount
    val paid: Money get() = payments.sum()
    val outstanding: Money get() = (total - paid).coerceAtLeastZero()
}

data class StockSummary(
    val remaining: Int,
    val sold: Int,
    val revenue: Money,
    val outstanding: Money,
    /** Revenue − shipping cost − batch costs; grows as more pins are sold. */
    val profitSoFar: Money,
)

object StockMath {
    fun summarize(
        quantityReceived: Int,
        writtenOff: Int,
        batchCosts: List<Money>,
        sales: List<StockSaleLine>,
    ): StockSummary {
        val sold = sales.sumOf { it.quantity }
        val revenue = sales.map { it.total }.sum()
        val shipping = sales.map { it.shippingCost }.sum()
        return StockSummary(
            remaining = quantityReceived - writtenOff - sold,
            sold = sold,
            revenue = revenue,
            outstanding = sales.map { it.outstanding }.sum(),
            profitSoFar = revenue - shipping - batchCosts.sum(),
        )
    }
}
