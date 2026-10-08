package com.lapel.domain.model

/** Where the pins are. Ordered: later entries are further along (except [CANCELLED]). */
enum class FulfillmentStatus {
    DRAFT,
    ORDERED_FROM_ALIBABA,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
}

/** How much the customer has paid. Always derived from payments, never stored. */
enum class PaymentStatus { UNPAID, DEPOSIT_PAID, FULLY_PAID, OVERPAID }

enum class PaymentMethod { BIT, PAYBOX, BANK_TRANSFER, CASH, OTHER }

enum class PaymentMilestone { DEPOSIT, BALANCE, OTHER }

enum class CostType {
    /** The ₪ amount actually charged for the Alibaba order (pins + mold + supplier shipping). */
    ALIBABA_PAYMENT,
    CUSTOMS,
    REFERRAL_COMMISSION,
    /** Added automatically to new orders (₪8 by default). */
    BANK_FEE,
    FEDEX,
    OTHER,
}

enum class DeliveryMethod { FEDEX, SELF_PICKUP }

enum class PinType { SOFT_ENAMEL, HARD_ENAMEL, DIE_STRUCK, PRINTED, OTHER }

enum class ShipmentStatus { PENDING, LABEL_CREATED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION, UNKNOWN }

enum class ChangeSource { MANUAL, FEDEX_SYNC, SYSTEM }

enum class ReminderType {
    DEPOSIT_DUE,
    BALANCE_DUE,
    BALANCE_OVERDUE,
    STALE_DRAFT,
    /** No shipping address yet – ask the customer. */
    ADDRESS_MISSING,
    /** Address known but not yet passed to the manufacturer. */
    ADDRESS_NOT_SENT,
    SHIPPED,
    DELIVERED,
    SYNC_FAILED,
}
