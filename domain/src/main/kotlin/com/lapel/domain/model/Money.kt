package com.lapel.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** An amount in Israeli shekels, stored as whole agorot (₪1 = 100) to avoid floating-point errors. */
@JvmInline
value class Money(val agorot: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(agorot + other.agorot)
    operator fun minus(other: Money) = Money(agorot - other.agorot)
    operator fun times(quantity: Int) = Money(agorot * quantity)
    operator fun unaryMinus() = Money(-agorot)
    override fun compareTo(other: Money) = agorot.compareTo(other.agorot)

    val isPositive: Boolean get() = agorot > 0
    val isNegative: Boolean get() = agorot < 0

    /** [percent]% of this amount, rounded half-up to a whole agora. */
    fun percent(percent: Int): Money = Money(
        BigDecimal.valueOf(agorot)
            .multiply(BigDecimal.valueOf(percent.toLong()))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .longValueExact(),
    )

    /** Divides evenly, rounding half-up; null when dividing by zero. */
    fun dividedBy(count: Int): Money? = if (count == 0) null else Money(
        BigDecimal.valueOf(agorot)
            .divide(BigDecimal.valueOf(count.toLong()), 0, RoundingMode.HALF_UP)
            .longValueExact(),
    )

    fun coerceAtLeastZero(): Money = if (agorot < 0) ZERO else this

    override fun toString(): String {
        val sign = if (agorot < 0) "-" else ""
        val abs = Math.abs(agorot)
        return "$sign₪${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Money(0)
        fun shekels(shekels: Long) = Money(shekels * 100)
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

fun maxOf(a: Money, b: Money): Money = if (a >= b) a else b
