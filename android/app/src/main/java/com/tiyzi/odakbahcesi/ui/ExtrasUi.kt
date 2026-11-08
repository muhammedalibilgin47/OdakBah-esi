package com.tiyzi.odakbahcesi.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.data.Achievements
import com.tiyzi.odakbahcesi.data.Quests
import com.tiyzi.odakbahcesi.data.Wheel
import com.tiyzi.odakbahcesi.data.computeStats
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.cos
import kotlin.math.sin

/** Günlük görevler + şans çarkı (Odak ekranı) */
@Composable
fun QuestsCard(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState()
    val p by vm.profile.collectAsState()
    val quests = remember(sessions) { Quests.today(sessions) }
    var showWheel by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📋 Günlük görevler", fontWeight = FontWeight.Bold)
            quests.forEach { q ->
                val claimed = p.questDay == com.tiyzi.odakbahcesi.data.todayKey() && q.id in p.questClaimed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            q.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            textDecoration = if (claimed) TextDecoration.LineThrough else null
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (q.current.toFloat() / q.target).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (q.done) Color(0xFFFF9800) else primary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    when {
                        claimed -> Text("✅", fontSize = 20.sp)
                        q.done -> Button(
                            onClick = { vm.claimQuest(q) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        ) { Text("🪙 ${q.reward}", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        else -> Text("🪙 ${q.reward}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
            if (remember(sessions, p.wheelDay) { vm.canSpinWheel() }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF9800), Color(0xFFE91E63))))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { showWheel = true }) {
                        Text("🎡 Şans çarkını çevir!", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showWheel) WheelDialog(vm, onClose = { showWheel = false })
}

@Composable
private fun WheelDialog(vm: FocusViewModel, onClose: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    val n = Wheel.PRIZES.size
    val seg = 360f / n

    AlertDialog(
        onDismissRequest = { if (!spinning) onClose() },
        title = { Text("🎡 Şans Çarkı", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("▼", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(250.dp).rotate(rotation.value)) {
                        for (i in 0 until n) {
                            drawArc(
                                color = Color(Wheel.COLORS[i]),
                                startAngle = i * seg - 90f, sweepAngle = seg, useCenter = true,
                            )
                        }
                    }
                    // Dilim üzerindeki ödül yazıları
                    for (i in 0 until n) {
                        val ang = Math.toRadians((i * seg + seg / 2 - 90f + rotation.value).toDouble())
                        Text(
                            "${Wheel.PRIZES[i]}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.offset(x = (90 * cos(ang)).dp, y = (90 * sin(ang)).dp)
                        )
                    }
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) { Text("🪙", fontSize = 24.sp) }
                }
                Spacer(Modifier.height(12.dp))
                result?.let {
                    Text("🎉 +${Wheel.PRIZES[it]} altın kazandın!", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            if (result != null) {
                Button(onClick = onClose) { Text("Harika!") }
            } else {
                Button(
                    enabled = !spinning,
                    onClick = {
                        val idx = vm.spinWheel() ?: return@Button
                        spinning = true
                        // Oku (üstte) seçilen dilimin ortasına getir
                        val target = 360f * 6 + (360f - (idx * seg + seg / 2))
                        scope.launch {
                            rotation.animateTo(target, tween(4000, easing = CubicBezierEasing(0.15f, 0.85f, 0.2f, 1f)))
                            result = idx
                            spinning = false
                        }
                    }
                ) { Text(if (spinning) "Dönüyor…" else "Çevir!") }
            }
        }
    )
}

/** Son 12 haftanın ısı haritası */
@Composable
fun HeatmapCard(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState()
    val primary = MaterialTheme.colorScheme.primary
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val byDay = remember(sessions) {
        sessions.filter { it.success }
            .groupBy { Instant.ofEpochMilli(it.endAt).atZone(zone).toLocalDate() }
            .mapValues { e -> e.value.sumOf { it.minutes } }
    }
    val days = (83 downTo 0).map { today.minusDays(it.toLong()) }
    val active = days.count { (byDay[it] ?: 0) > 0 }
    fun alpha(m: Int) = when {
        m < 30 -> 0.3f
        m < 60 -> 0.5f
        m < 120 -> 0.75f
        else -> 1f
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("🗓️ Son 12 hafta", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (w in 0 until 12) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 0 until 7) {
                            val day = days[w * 7 + d]
                            val m = byDay[day] ?: 0
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (m == 0) Color.Gray.copy(alpha = 0.15f) else primary.copy(alpha = alpha(m)))
                                    .then(
                                        if (day == today) Modifier.border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(3.dp))
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$active aktif gün", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("Az ", fontSize = 10.sp)
                listOf(0.3f, 0.5f, 0.75f, 1f).forEach {
                    Box(Modifier.padding(horizontal = 1.dp).size(12.dp).clip(RoundedCornerShape(2.dp)).background(primary.copy(alpha = it)))
                }
                Text(" Çok", fontSize = 10.sp)
            }
        }
    }
}

/** Kişisel rekorlar + haftalık rapor paylaşma */
@Composable
fun RecordsCard(vm: FocusViewModel) {
    val sessions by vm.sessions.collectAsState()
    val p by vm.profile.collectAsState()
    val context = LocalContext.current
    val ok = sessions.filter { it.success }
    val st = remember(sessions) { computeStats(sessions) }
    val zone = ZoneId.systemDefault()
    val bestWeek = remember(sessions) {
        val wf = WeekFields.ISO
        ok.groupBy {
            val d = Instant.ofEpochMilli(it.endAt).atZone(zone).toLocalDate()
            "${d.get(wf.weekBasedYear())}-${d.get(wf.weekOfWeekBasedYear())}"
        }.values.maxOfOrNull { l -> l.sumOf { it.minutes } } ?: 0
    }
    val longest = ok.maxOfOrNull { it.minutes } ?: 0

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🏆 Kişisel rekorlar", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("En uzun seans" to "$longest dk", "En iyi gün" to "${st.bestDayMin} dk", "En iyi hafta" to "$bestWeek dk")
                    .forEach { (t, v) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(v, fontWeight = FontWeight.Bold)
                            Text(t, fontSize = 10.sp)
                        }
                    }
            }
            Button(
                onClick = {
                    val report = """
                        🌱 Odak Bahçesi haftalık raporum
                        ⏱️ Bu hafta: ${st.weekTotal} dk (${"%.1f".format(st.weekTotal / 60f)} saat)
                        🔥 Seri: ${st.streak} gün
                        🌳 Toplam bitki: ${ok.size}
                        🏅 Rozet: ${p.badges.size}/${Achievements.all.size}
                        Sen de benimle çalış! 💪
                    """.trimIndent()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(send, "Haftalık raporu paylaş"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("📤 Haftalık raporu paylaş") }
        }
    }
}

