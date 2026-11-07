package com.tiyzi.odakbahcesi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.Mode
import com.tiyzi.odakbahcesi.Outcome
import com.tiyzi.odakbahcesi.data.DEAD_PLANT

enum class AppTab(val label: String, val icon: String) {
    ODAK("Odak", "⏱️"),
    BAHCE("Bahçe", "🌳"),
    ARKADAS("Arkadaşlar", "👥"),
    ISTATISTIK("İstatistik", "📊"),
    MAGAZA("Mağaza", "🛍️"),
}

/** Ekranların en altta bırakması gereken boşluk (alt çubuk + reklam yüksekliği). */
val LocalBottomSpace = compositionLocalOf { 0.dp }

/** Kaydırılabilir ekranların sonuna eklenir; içerik alt çubuğun arkasında kalmasın. */
@Composable
fun BottomSpacer() {
    Spacer(Modifier.navigationBarsPadding().height(LocalBottomSpace.current))
}

@Composable
fun App(vm: FocusViewModel) {
    val context = LocalContext.current
    val timer by vm.timer.collectAsState()
    val outcome by vm.outcome.collectAsState()
    val profile by vm.profile.collectAsState()
    val interstitial by vm.showInterstitial.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.ODAK) }
    val theme = AppTheme.of(profile.theme)

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val focusing = timer.mode == Mode.FOCUS
    val view = LocalView.current
    DisposableEffect(focusing) {
        view.keepScreenOn = focusing
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = focusing && profile.strictMode) {
        Toast.makeText(context, "Odak modundasın! Çıkarsan bitkin solar 🥀", Toast.LENGTH_SHORT).show()
    }

    val showBanner = !profile.premium && tab != AppTab.MAGAZA && !focusing
    val glass = profile.liquidGlass
    val bottomSpace: Dp = (if (glass) 92.dp else 84.dp) + (if (showBanner) 68.dp else 0.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CompositionLocalProvider(LocalBottomSpace provides bottomSpace) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (tab) {
                    AppTab.ODAK -> FocusScreen(vm)
                    AppTab.BAHCE -> GardenScreen(vm)
                    AppTab.ARKADAS -> FriendsScreen(vm)
                    AppTab.ISTATISTIK -> StatsScreen(vm)
                    AppTab.MAGAZA -> StoreScreen(vm)
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (showBanner) FakeBannerAd()
            if (glass) {
                GlassTabBar(selected = tab, dark = theme.dark, onSelect = { tab = it })
            } else {
                NavigationBar {
                    AppTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.icon, fontSize = 22.sp) },
                            label = { Text(t.label, maxLines = 1, fontSize = 11.sp) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = interstitial,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            InterstitialAd(onClose = { vm.closeInterstitial() })
        }
    }

    outcome?.let { OutcomeDialog(it, vm) }
}

/** Yüzen "Liquid Glass" alt çubuk: alttan ayrık, yarı saydam cam kapsül. */
@Composable
fun GlassTabBar(selected: AppTab, dark: Boolean, onSelect: (AppTab) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val glassFill = if (dark) {
        Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xCCFFFFFF), Color(0x99FFFFFF)))
    }
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.45f else 0.95f), Color.White.copy(alpha = 0.08f))
    )
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 4.dp)
            .fillMaxWidth()
            .shadow(20.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(glassFill)
            .border(0.8.dp, rim, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { t ->
            val isSel = t == selected
            val bg by animateColorAsState(if (isSel) primary.copy(alpha = 0.18f) else Color.Transparent, label = "tabBg")
            val sc by animateFloatAsState(if (isSel) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "tabScale")
            Column(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (!isSel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(t)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.icon, fontSize = 20.sp, modifier = Modifier.scale(sc))
                Text(
                    t.label,
                    fontSize = 10.sp,
                    maxLines = 1,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun OutcomeDialog(outcome: Outcome, vm: FocusViewModel) {
    when (outcome) {
        is Outcome.Grown -> AlertDialog(
            onDismissRequest = { vm.dismissOutcome() },
            title = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(outcome.plant.final, fontSize = 72.sp)
                    Text("${outcome.plant.label} büyüdü!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(outcome.message, textAlign = TextAlign.Center)
                    Text(
                        "⏱️ ${outcome.minutes} dk   🪙 +${outcome.coins}   ⭐ +${outcome.xp} XP",
                        fontWeight = FontWeight.SemiBold
                    )
                    outcome.newLevel?.let {
                        Text(
                            "🎉 Seviye $it'e ulaştın!",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.dismissOutcome(); vm.startBreak() }) { Text("Mola ver ☕") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissOutcome() }) { Text("Tamam") }
            }
        )

        is Outcome.Withered -> AlertDialog(
            onDismissRequest = { vm.dismissOutcome() },
            title = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(DEAD_PLANT, fontSize = 72.sp)
                    Text("Bitkin soldu", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(outcome.reason, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    Text(outcome.message, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Button(onClick = { vm.dismissOutcome(); vm.startFocus() }) { Text("Tekrar dene 🌱") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissOutcome() }) { Text("Kapat") }
            }
        )
    }
}

fun formatTime(totalSec: Int): String = "%02d:%02d".format(totalSec / 60, totalSec % 60)
