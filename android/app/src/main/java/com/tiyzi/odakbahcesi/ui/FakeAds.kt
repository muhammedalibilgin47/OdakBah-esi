package com.tiyzi.odakbahcesi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Demo reklamlar — markaların hepsi uydurmadır; gerçek sürümde yerine AdMob gelir. */
data class FakeAd(val emoji: String, val brand: String, val text: String, val cta: String, val color: Color)

val FAKE_ADS = listOf(
    FakeAd("📘", "Tohum Yayınları", "Yeni TYT deneme seti çıktı! 40 deneme, video çözümlü.", "İncele", Color(0xFF2163CC)),
    FakeAd("🥤", "Fıstık Fizz", "Ders arasına buz gibi serinlik. Şimdi limon aromalı!", "Keşfet", Color(0xFFF28C1A)),
    FakeAd("🎧", "SusPus Kulaklık", "Gürültüyü kapat, odağı aç. Öğrencilere %25 indirim.", "Satın al", Color(0xFF40404D)),
    FakeAd("🍕", "Pizzakule", "Gece çalışanlara özel: 23:00'ten sonra ikinci pizza bizden.", "Sipariş ver", Color(0xFFCC2626)),
    FakeAd("☕", "Bulutkahve", "Sınav haftası kampanyası: tüm filtre kahveler 29 TL.", "Haritada gör", Color(0xFF734D33)),
    FakeAd("🧮", "Soru Avcısı", "Günde 10 soruyla netlerini artır. İlk ay ücretsiz!", "İndir", Color(0xFF6633A6)),
)

/** Alt kısımdaki küçük banner reklam (8 saniyede bir değişir) */
@Composable
fun FakeBannerAd() {
    var index by remember { mutableIntStateOf(FAKE_ADS.indices.random()) }
    var info by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(8_000)
            index = (index + 1) % FAKE_ADS.size
        }
    }
    val ad = FAKE_ADS[index]
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ad.color)
            .clickable { info = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ad.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Reklam",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .background(Color(0xFFFFEB3B), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(ad.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                ad.text, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            ad.cta,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ad.color,
            modifier = Modifier
                .background(Color.White, CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text("Demo reklam") },
            text = { Text("Bu bir deneme reklamıdır. Premium ile reklamları kaldırabilirsin.") },
            confirmButton = { TextButton(onClick = { info = false }) { Text("Tamam") } }
        )
    }
}

/** Seans sonrası tam ekran reklam (5 sn sonra kapatılabilir) */
@Composable
fun InterstitialAd(onClose: () -> Unit) {
    val ad = remember { FAKE_ADS.random() }
    var remaining by remember { mutableIntStateOf(60) }   // reklam 1 dakika ekranda kalır
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    BackHandler { if (remaining == 0) onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ad.color, ad.color.copy(alpha = 0.6f), Color.Black)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(ad.emoji, fontSize = 110.sp)
            Text(ad.brand, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(ad.text, fontSize = 18.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Text(
                ad.cta,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ad.color,
                modifier = Modifier
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 40.dp, vertical = 14.dp)
            )
            Text("Demo reklam · Premium ile reklamsız kullan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(20.dp)
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = remaining == 0) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (remaining > 0) "$remaining" else "✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
