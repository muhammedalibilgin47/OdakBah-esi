package com.tiyzi.odakbahcesi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.data.Achievements
import com.tiyzi.odakbahcesi.data.DEAD_PLANT
import androidx.compose.ui.text.style.TextAlign
import com.tiyzi.odakbahcesi.data.PlantType
import com.tiyzi.odakbahcesi.data.computeStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState()
    val p by vm.profile.collectAsState()
    val st = remember(sessions) { computeStats(sessions) }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("tr")) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("İstatistikler", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Bugün", "${st.todayMin} dk", "⏱️", Modifier.weight(1f))
            StatCard("Seri", "${st.streak} gün", "🔥", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Toplam", "%.1f sa".format(st.totalMin / 60f), "📚", Modifier.weight(1f))
            StatCard("Başarı", "%${st.successRate}", "🎯", Modifier.weight(1f))
        }

        // Son 7 gün grafiği
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Son 7 gün", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                val max = (st.week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)
                Row(
                    Modifier.fillMaxWidth().height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    st.week.forEachIndexed { i, (day, min) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text(if (min > 0) "$min" else "", fontSize = 11.sp)
                            Box(
                                Modifier
                                    .width(26.dp)
                                    .fillMaxHeight(fraction = (min / max.toFloat() * 0.65f).coerceIn(0.02f, 0.65f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (i == st.week.lastIndex) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                            )
                            Text(day, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text(
                    "En iyi günün: ${st.bestDayMin} dk",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        HeatmapCard(vm)
        RecordsCard(vm)

        // Rozetler
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("🏅 Rozetler (${p.badges.size}/${Achievements.all.size})", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Achievements.all.chunked(4).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        line.forEach { a ->
                            val has = a.id in p.badges
                            Column(
                                Modifier
                                    .weight(1f)
                                    .height(104.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (has) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(if (has) a.emoji else "🔒", fontSize = 26.sp)
                                Text(a.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center, maxLines = 2)
                                Text(a.detail, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Ders bazlı dağılım — Premium özellik
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Ders bazlı dağılım", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (!p.premium) {
                    Text("🔒 Detaylı istatistikler Premium üyelere özel. Mağaza sekmesinden açabilirsin.",
                        style = MaterialTheme.typography.bodyMedium)
                } else if (st.bySubject.isEmpty()) {
                    Text("Henüz veri yok.")
                } else {
                    val total = st.bySubject.sumOf { it.second }.coerceAtLeast(1)
                    st.bySubject.forEach { (subject, min) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(subject, modifier = Modifier.width(90.dp))
                            LinearProgressIndicator(
                                progress = { min / total.toFloat() },
                                modifier = Modifier.weight(1f).height(10.dp)
                            )
                            Text("  $min dk", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Son seanslar
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Son seanslar", fontWeight = FontWeight.Bold)
                if (sessions.isEmpty()) {
                    Text("Henüz seans yok.", modifier = Modifier.padding(top = 8.dp))
                }
                sessions.take(10).forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text(if (s.success) PlantType.of(s.plant).final else DEAD_PLANT, fontSize = 24.sp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("${s.subject} · ${s.minutes} dk", fontWeight = FontWeight.SemiBold)
                            Text(
                                Instant.ofEpochMilli(s.endAt).atZone(ZoneId.systemDefault()).format(fmt),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(if (s.success) "Tamamlandı" else "Soldu", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        BottomSpacer()
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("$icon  $title", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
