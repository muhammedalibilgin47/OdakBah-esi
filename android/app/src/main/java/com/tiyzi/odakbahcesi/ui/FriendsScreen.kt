package com.tiyzi.odakbahcesi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiyzi.odakbahcesi.FocusViewModel
import com.tiyzi.odakbahcesi.data.PlantType
import com.tiyzi.odakbahcesi.data.computeStats

private data class LeaderRow(
    val id: String, val name: String, val level: Int, val weekMin: Int, val streak: Int, val isMe: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(vm: FocusViewModel) {
    val context = LocalContext.current
    val p by vm.profile.collectAsState()
    val friends by vm.friends.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val primary = MaterialTheme.colorScheme.primary

    var codeInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupGoal by remember { mutableIntStateOf(600) }
    var confirmLeave by remember { mutableStateOf(false) }

    val stats = remember(sessions) { computeStats(sessions) }
    val rows = remember(friends, stats, p.name, p.level) {
        (friends.map { LeaderRow(it.id, it.name, it.level, it.weekMin, it.streak, false) } +
            LeaderRow("me", "${p.name} (sen)", p.level, stats.weekTotal, stats.streak, true))
            .sortedByDescending { it.weekMin }
    }
    LaunchedEffect(Unit) { vm.refreshFriends() }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Arkadaşlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)

        AccountCard(vm)

        // ---- Profilim
        Box2 {
            Text("Senin profilin", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = p.name, onValueChange = { vm.setName(it) },
                label = { Text("İsmin") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Arkadaşlarının seni eklemesi için profil kodunu gönder. Yeni seanslardan sonra tekrar gönderirsen sıralamada güncellenirsin. Kodlar iPhone sürümüyle de uyumlu.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, vm.shareMessage())
                    }
                    context.startActivity(Intent.createChooser(send, "Profil kodunu paylaş"))
                }, modifier = Modifier.weight(1f)) { Text("📤 Kodu paylaş") }
                OutlinedButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Odak kodu", vm.myShareCode()))
                    Toast.makeText(context, "Profil kodun kopyalandı 📋", Toast.LENGTH_SHORT).show()
                }) { Text("📋 Kopyala") }
            }
        }

        // ---- Arkadaş ekle
        Box2 {
            Text("Arkadaş ekle", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = codeInput, onValueChange = { codeInput = it },
                placeholder = { Text("ODAK-... kodunu buraya yapıştır") },
                maxLines = 3, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    codeInput = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                }) { Text("Yapıştır") }
                Button(
                    onClick = {
                        val msg = vm.addFriend(codeInput)
                        message = msg
                        if ("eklendi" in msg || "güncellendi" in msg) codeInput = ""
                    },
                    enabled = codeInput.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("➕ Ekle") }
            }
            if (!p.premium) {
                Text("Ücretsiz sürümde en fazla ${FocusViewModel.FREE_FRIEND_LIMIT} arkadaş. Premium ile sınırsız 👑",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Sıralama
        Box2 {
            Text("🏆 Bu hafta sıralama", fontWeight = FontWeight.Bold)
            rows.forEachIndexed { i, r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (r.isMe) primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." },
                        fontSize = 20.sp, modifier = Modifier.width(36.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = if (r.isMe) FontWeight.Bold else FontWeight.SemiBold)
                        Text("Seviye ${r.level} · 🔥 ${r.streak} gün", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${r.weekMin} dk", fontWeight = FontWeight.Bold, color = if (r.isMe) primary else MaterialTheme.colorScheme.onSurface)
                    if (!r.isMe) {
                        TextButton(onClick = { vm.removeFriend(r.id) }) { Text("✕") }
                    }
                }
            }
            if (friends.isEmpty()) {
                Text("Henüz arkadaşın yok. Kodunu paylaş veya bir arkadaşının kodunu ekle!",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Grup + ortak bahçe
        Box2 {
            Text("👥 Çalışma grubu", fontWeight = FontWeight.Bold)
            if (!p.premium) {
                Text("🔒 Grup kurma, ortak hedef ve ortak bahçe Premium'a özel. Mağaza sekmesinden açabilirsin.")
            } else if (p.groupName.isEmpty()) {
                Text("Arkadaşlarınla bir grup kur, haftalık ortak hedef belirle ve bahçelerinizi birleştirin.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = newGroupName, onValueChange = { newGroupName = it },
                    label = { Text("Grup adı (ör. TYT Savaşçıları)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Haftalık ortak hedef", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(300, 600, 1000, 1500).forEach { g ->
                        FilterChip(selected = newGroupGoal == g, onClick = { newGroupGoal = g }, label = { Text("$g") })
                    }
                }
                Button(
                    onClick = { vm.createGroup(newGroupName, newGroupGoal) },
                    enabled = newGroupName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Grubu kur") }
            } else {
                val total = stats.weekTotal + friends.sumOf { it.weekMin }
                val goal = p.groupGoal.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.groupName, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text("${friends.size + 1} üye", style = MaterialTheme.typography.bodySmall)
                }
                Text("Haftalık hedef: $total / $goal dk")
                LinearProgressIndicator(
                    progress = { (total / goal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                if (total >= goal) {
                    Text("🎉 Hedefe ulaştınız! Harika takım çalışması.", color = primary, fontWeight = FontWeight.SemiBold)
                }
                Text("🌳 Ortak bahçe", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                val mine = sessions.filter { it.success }.take(12).map { PlantType.of(it.plant).final }
                val all = mine + friends.flatMap { it.garden }
                if (all.isEmpty()) {
                    Text("Henüz bitki yok. İlk seansı tamamlayan kazanır! 🌱", style = MaterialTheme.typography.bodySmall)
                } else {
                    all.chunked(7).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            line.forEach { e ->
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF8D6E63).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 22.sp) }
                            }
                        }
                    }
                }
                TextButton(onClick = { confirmLeave = true }) { Text("Gruptan çık", color = Color(0xFFC62828)) }
            }
        }

        if (friends.none { it.isDemo }) {
            TextButton(onClick = { vm.addDemoFriends() }, modifier = Modifier.fillMaxWidth()) {
                Text("🧪 Denemek için demo arkadaşlar ekle")
            }
        }
        BottomSpacer()
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Tamam") } }
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Gruptan çıkılsın mı?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveGroup() }) { Text("Çık") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun Box2(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Hesap kartı: Google / e-posta ile giriş, bulut yedekleme */
@Composable
fun AccountCard(vm: FocusViewModel) {
    val context = LocalContext.current
    val acc by vm.account.collectAsState()
    var showEmail by remember { mutableStateOf(false) }

    Box2 {
        if (!acc.signedIn) {
            Text("☁️ Hesap", fontWeight = FontWeight.Bold)
            Text(
                "Giriş yap: bahçen, altınların ve rozetlerin bulutta yedeklensin; arkadaş sıralaması canlı güncellensin. iPhone'da da aynı hesapla girebilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { context.findActivity()?.let { vm.signInWithGoogle(it) } },
                enabled = !acc.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("G   Google ile giriş yap", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { showEmail = true },
                enabled = !acc.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("✉️  E-posta ile giriş / kayıt") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(21.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(acc.email.take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Giriş yapıldı ✅", fontWeight = FontWeight.Bold)
                    Text(acc.email, style = MaterialTheme.typography.bodySmall)
                    if (acc.lastSync > 0) {
                        Text(
                            "Son yedek: " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(acc.lastSync)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.syncNow() }, enabled = !acc.busy, modifier = Modifier.weight(1f)) {
                    Text("☁️ Şimdi eşitle")
                }
                OutlinedButton(onClick = { vm.signOut(context.findActivity()) }, enabled = !acc.busy) {
                    Text("Çıkış")
                }
            }
        }
        if (acc.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Bekleyin…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showEmail) EmailDialog(vm, onClose = { showEmail = false })

    acc.message?.let {
        AlertDialog(
            onDismissRequest = { vm.clearAccountMessage() },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { vm.clearAccountMessage() }) { Text("Tamam") } }
        )
    }
}

@Composable
private fun EmailDialog(vm: FocusViewModel, onClose: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("E-posta ile giriş") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("E-posta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text("Şifre (en az 6 karakter)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { if (email.isNotBlank()) { vm.resetPassword(email); onClose() } }) {
                    Text("Şifremi unuttum")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.signInWithEmail(email, pw, create = false); onClose() },
                enabled = email.isNotBlank() && pw.length >= 6
            ) { Text("Giriş yap") }
        },
        dismissButton = {
            TextButton(
                onClick = { vm.signInWithEmail(email, pw, create = true); onClose() },
                enabled = email.isNotBlank() && pw.length >= 6
            ) { Text("Kayıt ol") }
        }
    )
}
