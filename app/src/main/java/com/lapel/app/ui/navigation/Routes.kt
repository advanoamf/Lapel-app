package com.lapel.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object DashboardRoute
@Serializable object OrdersRoute
@Serializable object StockRoute
@Serializable object CustomersRoute
@Serializable object SettingsRoute

@Serializable data class CustomerDetailRoute(val id: Long)
@Serializable data class CustomerEditRoute(val id: Long = 0)
@Serializable data class OrderDetailRoute(val id: Long)
@Serializable data class OrderEditRoute(val id: Long = 0, val customerId: Long = 0)
