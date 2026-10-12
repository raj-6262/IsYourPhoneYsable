package com.example.phonecheck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

private val BG = Color(0xFF0B1020)
private val CardBg = Color(0xFF151B30)
private val Track = Color(0xFF26304D)
private val Accent = Color(0xFF6C8CFF)
private val Green = Color(0xFF2ECC71)
private val Amber = Color(0xFFF5B041)
private val Red = Color(0xFFE74C3C)
private val TextDim = Color(0xFF9AA4C4)

private fun scoreColor(pct: Float) = when {
    pct >= 0.75f -> Green
    pct >= 0.5f -> Amber
    else -> Red
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = BG, surface = BG)) {
        Surface(Modifier.fillMaxSize(), color = BG) {
            when (val s = state) {
                is UiState.Idle -> HomeScreen(onStart = vm::start)
                is UiState.Scanning -> ScanningScreen(s)
                is UiState.Done -> ResultScreen(s.result, onAgain = vm::reset)
            }
        }
    }
}

// ---------------- Home ----------------

@Composable
private fun HomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Is your phone usable?", fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "One tap tests your Android version, RAM, storage, speed and smoothness, then tells you whether it's time to upgrade.",
            color = TextDim, textAlign = TextAlign.Center, fontSize = 15.sp
        )
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier.size(170.dp).clip(CircleShape).background(Accent).clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Text("START\nSCAN", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        Text("Takes about 15 seconds. Everything runs on your phone and nothing is uploaded.",
            color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ---------------- Scanning ----------------

@Composable
private fun ScanningScreen(s: UiState.Scanning) {
    val rot by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = 14.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            rotate(rot) {
                drawArc(Accent, 0f, 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(s.stage, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Bar(s.progress, Accent, modifier = Modifier.width(240.dp))
        Spacer(Modifier.height(24.dp))
        Text("Please keep the app open. The phone may feel busy for a moment, that's the test.",
            color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ---------------- Result ----------------

private data class Verdict(val title: String, val body: String, val color: Color)

private fun verdictFor(score: Int): Verdict = when {
    score >= 75 -> Verdict("Keep your phone", "It's in good shape. You can comfortably use it for 2 or more years.", Green)
    score >= 55 -> Verdict("Fine for now", "It still works well. Start watching sales and plan to upgrade within 1 to 2 years.", Amber)
    score >= 35 -> Verdict("Upgrade soon", "It's showing its age. Expect more lag and fewer updates within 6 to 12 months.", Amber)
    else -> Verdict("Time to upgrade", "This phone is struggling. A new phone will make a clear difference.", Red)
}

@Composable
private fun ResultScreen(r: ScanResult, onAgain: () -> Unit) {
    val verdict = verdictFor(r.total)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(r.info.model, color = TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            ScoreGauge(r.total, verdict.color)
        }
        item {
            Card(verdict.color.copy(alpha = 0.15f)) {
                Text(verdict.title, color = verdict.color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(verdict.body, color = Color.White, fontSize = 15.sp)
            }
        }
        item { SectionTitle("Score breakdown") }
        items(r.checks) { CheckRow(it) }
        item { SectionTitle("Your phone") }
        item { InfoCard(r.info) }
        item { SectionTitle(if (r.total < 75) "Picking your next phone" else "If you still want to upgrade") }
        item { BuyGuide(r) }
        item {
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScoreGauge(score: Int, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(score) { anim.animateTo(score / 100f, tween(1200)) }
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(Track, 135f, 270f, false, topLeft, arcSize, style = style)
            drawArc(color, 135f, 270f * anim.value, false, topLeft, arcSize, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(anim.value * 100).roundToInt()}", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text("out of 100", color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CheckRow(c: Check) {
    val frac = c.score.toFloat() / c.max
    val color = scoreColor(frac)
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("${c.score}/${c.max}", color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text(c.value, color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Bar(frac, color)
        Spacer(Modifier.height(8.dp))
        Text(c.advice, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}

@Composable
private fun InfoCard(i: DeviceInfo) {
    Card {
        InfoLine("Model", i.model)
        InfoLine("Android", "${i.androidVersion} (API ${i.apiLevel})")
        InfoLine("Security patch", i.securityPatch)
        InfoLine("RAM", "${i.ramGb} GB")
        InfoLine("Storage", "%.1f GB free of %d GB".format(i.storageFreeGb, i.storageTotalGb))
        InfoLine("CPU cores", "${i.cores} (${i.abi})")
        InfoLine("Screen refresh", "${i.refreshHz} Hz")
        InfoLine("Battery", i.batteryHealth)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

// ---------------- Buying guide ----------------

private fun buyTips(r: ScanResult): List<String> {
    val weak = r.checks.filter { it.score.toFloat() / it.max < 0.5f }.map { it.id }.toSet()
    val tips = mutableListOf("Check the update promise (5 or more years of Android and security updates), not just the specs.")
    if ("ram" in weak || "cpu" in weak || "smooth" in weak) {
        tips += "Pick 8 GB RAM and a recent chipset (a current mid-range or better processor)."
    }
    if ("storage" in weak || "disk" in weak) {
        tips += "Pick 256 GB with fast storage (UFS 3.1 or newer) so you don't run out again."
    }
    if ("battery" in weak) tips += "Look for a 5000 mAh or larger battery with fast charging."
    if ("smooth" in weak) tips += "Choose a 120 Hz display for a smoother feel."
    if ("os" in weak || "patch" in weak) tips += "Buy a phone launched with the latest Android version."
    return tips
}

@Composable
private fun BuyGuide(r: ScanResult) {
    val uri = LocalUriHandler.current
    val budgets = listOf(15000, 20000, 30000, 40000, 60000)
    var budget by remember { mutableIntStateOf(20000) }
    Card {
        Text("What to look for", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        buyTips(r).forEach { Text("•  $it", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("Your budget", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            budgets.forEach { b ->
                val selected = b == budget
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Accent else Track)
                        .clickable { budget = b }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("${b / 1000}k", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { uri.openUri("https://www.flipkart.com/search?q=smartphone+under+$budget") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Flipkart: phones under ₹$budget") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { uri.openUri("https://www.amazon.in/s?k=smartphone+under+$budget") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Amazon: phones under ₹$budget") }
        Spacer(Modifier.height(10.dp))
        Text(
            "These open live search pages, so you see current sale prices (Big Billion Days, Great Indian Festival). Compare both before you buy.",
            color = TextDim, fontSize = 12.sp
        )
    }
}

// ---------------- Small building blocks ----------------

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Card(background: Color = CardBg, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).padding(16.dp),
        content = content
    )
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Dp = 8.dp, modifier: Modifier = Modifier.fillMaxWidth()) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}
