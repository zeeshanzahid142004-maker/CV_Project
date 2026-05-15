package com.example.cvproject.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cvproject.ui.theme.F1Colors
import com.example.cvproject.viewmodel.TripViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripLogScreen(viewModel: TripViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(F1Colors.Background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            items(uiState.sessions, key = { it.id }) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = F1Colors.Surface),
                    border = BorderStroke(1.dp, F1Colors.TextMuted.copy(alpha = 0.15f)),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(90.dp)
                                .background(F1Colors.Red.copy(alpha = 0.7f))
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        ) {
                            Text(
                                text = formatTripDate(session.timestamp, dateFormatter),
                                color = F1Colors.TextMuted,
                                fontSize = 12.sp,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TripStatColumn(
                                    modifier = Modifier.weight(1f),
                                    label = "MAX",
                                    value = "%.0f".format(session.maxSpeed),
                                    unit = "km/h",
                                )
                                TripStatColumn(
                                    modifier = Modifier.weight(1f),
                                    label = "AVG",
                                    value = "%.0f".format(session.avgSpeed),
                                    unit = "km/h",
                                )
                                TripStatColumn(
                                    modifier = Modifier.weight(1f),
                                    label = "DIST",
                                    value = "%.1f".format(session.distance),
                                    unit = "km",
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.sessions.isEmpty()) {
            Text(
                text = "No trips yet",
                color = F1Colors.TextMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun TripStatColumn(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = F1Colors.TextMuted, fontSize = 11.sp)
        Text(
            text = value,
            color = F1Colors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = unit, color = F1Colors.TextMuted, fontSize = 11.sp)
    }
}

private fun formatTripDate(timestamp: Long, formatter: SimpleDateFormat): String {
    return formatter.format(Date(timestamp))
}
