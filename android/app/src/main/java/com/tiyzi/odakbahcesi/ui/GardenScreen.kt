package com.tiyzi.odakbahcesi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.data.DEAD_PLANT
import com.tiyzi.odakbahcesi.data.PlantType

@Composable
fun GardenScreen(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState()
    val grown = sessions.count { it.success }
    val withered = sessions.size - grown

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Bahçem", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "🌳 $grown büyüdü   ·   $DEAD_PLANT $withered soldu",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        if (sessions.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🌰", fontSize = 64.sp)
                    Text(
                        "Bahçen henüz boş.\nİlk odak seansını tamamla, ilk bitkin burada filizlensin!",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        items(sessions.reversed(), key = { it.id }) { s ->
            val emoji = if (s.success) PlantType.of(s.plant).final else DEAD_PLANT
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (s.success) Color(0xFF8D6E63).copy(alpha = 0.25f)
                        else Color(0xFF9E9E9E).copy(alpha = 0.25f)
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emoji, fontSize = 28.sp)
                    Text("${s.minutes}dk", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { BottomSpacer() }
    }
}
