package com.tiyzi.odakbahcesi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.Mode
import com.tiyzi.odakbahcesi.data.FOCUS_OPTIONS
import com.tiyzi.odakbahcesi.data.PlantType
import com.tiyzi.odakbahcesi.data.Profile
import com.tiyzi.odakbahcesi.data.SUBJECTS
import com.tiyzi.odakbahcesi.data.SoundKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(vm: FocusViewModel) {
    val t by vm.timer.collectAsState()
    val p by vm.profile.collectAsState()
    val quote by vm.quote.collectAsState()
    var confirmGiveUp by remember { mutableStateOf(false) }

    val plant = if (t.mode == Mode.IDLE) PlantType.of(p.selectedPlant) else t.plant
    val emoji = when (t.mode) {
        Mode.IDLE -> plant.stages.first()
        Mode.FOCUS -> plant.stageFor(t.progress)
        Mode.BREAK -> "☕"
    }
    val seconds = if (t.mode == Mode.IDLE) p.focusMin * 60 else t.remainingSec
    val progress by animateFloatAsState(
        targetValue = if (t.mode == Mode.IDLE) 0f else t.progress, label = "progress"
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileHeader(p)
        Spacer(Modifier.height(10.dp))
        TodayCard(vm)
        Spacer(Modifier.height(14.dp))

        // Büyüyen bitki + ilerleme halkası
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(270.dp)) {
            ProgressRing(
                progress = progress,
                color = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GrowingEmoji(emoji)
                Text(
                    formatTime(seconds),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (t.mode == Mode.FOCUS) {
                    Text(t.subject, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                } else if (t.mode == Mode.BREAK) {
                    Text("Mola", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            quote,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))

        when (t.mode) {
            Mode.IDLE -> {
                SectionLabel("Ne çalışıyorsun?")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(SUBJECTS) { s ->
                        FilterChip(selected = p.subject == s, onClick = { vm.setSubject(s) }, label = { Text(s) })
                    }
                }
                SectionLabel("Süre (dakika)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FOCUS_OPTIONS) { m ->
                        FilterChip(selected = p.focusMin == m, onClick = { vm.setFocusMin(m) }, label = { Text("$m") })
                    }
                }
                SectionLabel("Odak sesi")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SoundKind.entries) { k ->
                        FilterChip(selected = SoundKind.of(p.sound) == k, onClick = { vm.setSound(k.name) }, label = { Text(k.label) })
                    }
                }
                SectionLabel("Hangi bitkiyi yetiştireceksin?")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PlantType.entries.filter { it.name in p.owned }) { pl ->
                        FilterChip(
                            selected = p.selectedPlant == pl.name,
                            onClick = { vm.buyOrSelectPlant(pl) },
                            label = { Text("${pl.final} ${pl.label}") }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("🔒 Katı mod", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (p.strictMode) "Başka uygulamaya geçersen bitkin solar."
                            else "Diğer uygulamaları kullanabilirsin, sayaç bildirimde görünür.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    Switch(checked = p.strictMode, onCheckedChange = { vm.setStrictMode(it) })
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.startFocus() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("🌱  Odaklanmaya Başla", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (p.strictMode) "Katı modda başka uygulamaya geçip 10 saniye içinde dönmezsen bitkin solar. Ekranı kilitlemek serbest."
                    else "Sayaç kilit ekranında ve bildirim panelinde geri sayar.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Mode.FOCUS -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SoundKind.entries) { k ->
                        FilterChip(selected = SoundKind.of(p.sound) == k, onClick = { vm.setSound(k.name) }, label = { Text(k.label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "🔒 Odak modu açık — telefonu bırak, bitkin büyüsün.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { confirmGiveUp = true }) { Text("Vazgeç") }
            }

            Mode.BREAK -> {
                Text(
                    "Gözlerini ekrandan ayır, biraz su iç ve esne. 🧘",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = { vm.stopBreak() }) { Text("Molayı bitir") }
            }
        }
        BottomSpacer()
    }

    if (confirmGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text("Emin misin? 🥀") },
            text = { Text("Şimdi bırakırsan ${plant.label} solacak ve bahçene solmuş olarak eklenecek.") },
            confirmButton = {
                TextButton(onClick = { confirmGiveUp = false; vm.giveUp() }) {
                    Text("Bırak", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                Button(onClick = { confirmGiveUp = false }) { Text("Devam ediyorum 💪") }
            }
        )
    }
}

@Composable
fun ProfileHeader(p: Profile) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Seviye ${p.level} · ${p.title}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${p.xp % Profile.XP_PER_LEVEL} / ${Profile.XP_PER_LEVEL} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
                Text("🪙 ${p.coins}", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { p.levelProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
private fun GrowingEmoji(emoji: String) {
    val scale = remember(emoji) { Animatable(0.5f) }
    LaunchedEffect(emoji) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Text(
        emoji,
        fontSize = 84.sp,
        modifier = Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value)
    )
}

@Composable
private fun ProgressRing(progress: Float, color: Color, track: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 16.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
        )
        drawArc(
            color = color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)
    )
}

/** Sınav geri sayımı + günlük hedef */
@Composable
fun TodayCard(vm: FocusViewModel) {
    val p by vm.profile.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val today = remember(sessions) { vm.todayMin() }
    val goal = p.dailyGoal.coerceAtLeast(1)
    val days = remember(p.examDate) { vm.examDaysLeft() }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯 Bugün: $today / $goal dk", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (days != null) {
                    Text(
                        "🎓 ${p.examName}'ye $days gün",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.shapes.extraLarge)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (today / goal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (today >= goal) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
            )
            if (today >= goal) {
                Text("🏅 Günlük hedefe ulaştın! Bundan sonrası bonus.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
