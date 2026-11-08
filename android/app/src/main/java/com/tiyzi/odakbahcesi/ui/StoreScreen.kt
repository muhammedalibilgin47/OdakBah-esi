package com.tiyzi.odakbahcesi.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.data.BREAK_OPTIONS
import com.tiyzi.odakbahcesi.data.PlantType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(vm: FocusViewModel) {
    val p by vm.profile.collectAsState()
    val context = LocalContext.current
    var adSeconds by remember { mutableIntStateOf(-1) }
    var showPremium by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Mağaza", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)

        // Bakiye + ödüllü reklam
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bakiyen", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("🪙 ${p.coins}", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Her odak dakikası = 1 altın", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
                Button(onClick = { adSeconds = 5 }) { Text("🎬 Reklam izle\n+${FocusViewModel.AD_REWARD} 🪙") }
            }
        }

        // Premium
        Card(
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(if (p.premium) "👑 Premium üyesin" else "👑 Odak Bahçesi Premium",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "🎨 4 özel tema (Okyanus, Gün Batımı, Lavanta, Gece)",
                    "📊 Ders bazlı detaylı istatistikler",
                    "👥 Sınırsız arkadaş, çalışma grubu ve ortak bahçe",
                    "💧 Liquid Glass alt çubuk",
                    "🚫 Reklamsız deneyim",
                ).forEach { Text(it, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(10.dp))
                if (p.premium) {
                    OutlinedButton(onClick = { vm.setPremium(false) }) { Text("Premium'u kapat (demo)") }
                } else {
                    Button(onClick = { showPremium = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Premium'a geç · ₺49,99/ay")
                    }
                }
            }
        }

        // Bitkiler
        SectionLabel("Bitkiler")
        PlantType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { plant ->
                    val owned = plant.name in p.owned
                    val selected = p.selectedPlant == plant.name
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val ok = vm.buyOrSelectPlant(plant)
                                val msg = when {
                                    !ok -> "Yetersiz altın. ${plant.price - p.coins} 🪙 daha lazım."
                                    owned -> "${plant.label} seçildi"
                                    else -> "${plant.label} satın alındı! 🎉"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(plant.final, fontSize = 40.sp)
                            Text(plant.label, fontWeight = FontWeight.SemiBold)
                            Text(plant.stages.joinToString(" → "), fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    selected -> "✓ Seçili"
                                    owned -> "Sahipsin"
                                    else -> "🪙 ${plant.price}"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Temalar
        SectionLabel("Temalar")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppTheme.entries) { th ->
                val locked = th.premium && !p.premium
                FilterChip(
                    selected = p.theme == th.name,
                    onClick = {
                        if (!vm.selectTheme(th)) {
                            Toast.makeText(context, "Bu tema Premium'a özel 👑", Toast.LENGTH_SHORT).show()
                        }
                    },
                    label = { Text("${th.emoji} ${th.label}${if (locked) " 🔒" else ""}") }
                )
            }
        }

        // Ayarlar
        SectionLabel("⚙️ Ayarlar")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎯 Günlük hedef", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(30, 60, 90, 120, 180, 240, 360)) { g ->
                        FilterChip(selected = p.dailyGoal == g, onClick = { vm.setDailyGoal(g) }, label = { Text("$g dk") })
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("⏰ Akıllı hatırlatıcı", fontWeight = FontWeight.SemiBold)
                        Text("O gün çalışmadıysan seçtiğin saatte hatırlatır.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = p.reminderOn, onCheckedChange = { vm.setReminder(it) })
                }
                if (p.reminderOn) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(8, 12, 16, 18, 19, 20, 21, 22)) { h ->
                            FilterChip(selected = p.reminderHour == h, onClick = { vm.setReminder(true, h) },
                                label = { Text("%02d:00".format(h)) })
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🔁 Otomatik Pomodoro", fontWeight = FontWeight.SemiBold)
                        Text("Seans bitince mola kendiliğinden başlar; 4 seansta bir 15 dk uzun mola.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = p.autoCycle, onCheckedChange = { vm.setAutoCycle(it) })
                }
                HorizontalDivider()
                Text("🎓 Sınav geri sayımı", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = p.examName, onValueChange = { vm.setExamName(it) },
                    label = { Text("Sınav adı (ör. YKS, LGS, KPSS)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val cal = java.util.Calendar.getInstance()
                        if (p.examDate > 0) cal.timeInMillis = p.examDate else cal.add(java.util.Calendar.MONTH, 6)
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val c = java.util.Calendar.getInstance()
                                c.set(y, m, d, 9, 0, 0)
                                vm.setExamDate(c.timeInMillis)
                            },
                            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                    }) {
                        Text(
                            if (p.examDate > 0) "📅 " + java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.forLanguageTag("tr"))
                                .format(java.util.Date(p.examDate))
                            else "📅 Sınav tarihi seç"
                        )
                    }
                    if (p.examDate > 0) {
                        TextButton(onClick = { vm.setExamDate(0L) }) { Text("Kaldır") }
                    }
                }
            }
        }

        // Görünüm (yalnızca Android)
        SectionLabel("Görünüm")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("💧 Liquid Glass alt çubuk" + if (p.premium) "" else " 🔒", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !p.premium -> "Premium üyelere özel."
                            p.liquidGlass -> "Yüzen cam kapsül çubuk açık."
                            else -> "Klasik, alta yapışık çubuk kullanılıyor."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = p.premium && p.liquidGlass,
                    onCheckedChange = {
                        if (p.premium) vm.setLiquidGlass(it)
                        else Toast.makeText(context, "Liquid Glass alt çubuk Premium'a özel 👑", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Ayarlar
        SectionLabel("Mola süresi (dakika)")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BREAK_OPTIONS) { m ->
                FilterChip(selected = p.breakMin == m, onClick = { vm.setBreakMin(m) }, label = { Text("$m") })
            }
        }
        BottomSpacer()
    }

    // Ödüllü reklam simülasyonu (gerçek sürümde AdMob Rewarded Ad)
    if (adSeconds >= 0) {
        LaunchedEffect(Unit) {
            while (adSeconds > 0) {
                delay(1000)
                adSeconds--
            }
            vm.rewardAd()
            Toast.makeText(context, "+${FocusViewModel.AD_REWARD} altın kazandın! 🪙", Toast.LENGTH_SHORT).show()
            adSeconds = -1
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("🎬 Reklam (demo)") },
            text = {
                Text("Burada gerçek uygulamada bir ödüllü video reklam oynar.\n\nÖdülün $adSeconds saniye sonra hesabına eklenecek…")
            },
            confirmButton = { }
        )
    }

    // Premium satın alma simülasyonu (gerçek sürümde Google Play Billing)
    if (showPremium) {
        AlertDialog(
            onDismissRequest = { showPremium = false },
            title = { Text("👑 Premium (demo)") },
            text = {
                Text("Gerçek sürümde burada Google Play ödeme ekranı açılır. " +
                    "Şimdilik Premium özelliklerini ücretsiz deneyebilirsin.")
            },
            confirmButton = {
                Button(onClick = { vm.setPremium(true); showPremium = false }) { Text("Premium'u dene") }
            },
            dismissButton = {
                TextButton(onClick = { showPremium = false }) { Text("Vazgeç") }
            }
        )
    }
}
