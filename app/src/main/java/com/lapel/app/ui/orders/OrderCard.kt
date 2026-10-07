package com.lapel.app.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lapel.app.R
import com.lapel.app.data.local.dao.OrderSummaryRow
import com.lapel.app.ui.common.FulfillmentChip
import com.lapel.app.ui.common.OverdueChip
import com.lapel.app.ui.common.PaymentChip
import com.lapel.app.ui.common.formatted
import com.lapel.domain.finance.OrderFinancials
import com.lapel.domain.model.FulfillmentStatus

@Composable
fun OrderCard(row: OrderSummaryRow, financials: OrderFinancials, overdue: Boolean, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${row.orderNumber} · ${row.title}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(row.orderDate.formatted(), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                listOfNotNull(row.customerName, row.customerOrganization?.takeIf { it != row.title }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FulfillmentChip(row.fulfillmentStatus)
                if (row.fulfillmentStatus != FulfillmentStatus.DRAFT && row.fulfillmentStatus != FulfillmentStatus.CANCELLED) {
                    PaymentChip(financials.paymentStatus)
                }
                if (overdue) OverdueChip()
                if (row.activeShipments > 0) Icon(Icons.Outlined.LocalShipping, stringResource(R.string.status_shipped))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.profit_value, financials.netProfit.formatted()), style = MaterialTheme.typography.bodyMedium)
                if (financials.outstanding.isPositive) {
                    Text(
                        stringResource(R.string.owes_value, financials.outstanding.formatted()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
