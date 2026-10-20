package com.hwstudio.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val TABS = listOf("Device", "Pins", "Code", "AI", "Serial")

@Composable
fun HwApp(vm: HwViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.background(AppBackground),
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xCC0B1020)) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(listOf("🔌", "🎛", "⌨", "✨", "📟")[i]) },
                        label = { Text(t, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).statusBarsPadding().padding(horizontal = 16.dp)) {
            when (tab) {
                0 -> DeviceTab(vm)
                1 -> PinsTab(vm)
                2 -> CodeTab(vm) { tab = 3 }
                3 -> AiTab(vm) { tab = 2 }
                else -> SerialTab(vm)
            }
        }
    }
}

@Composable
fun Title(t: String, sub: String) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(t, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFF9AA4C0))
    }
}

@Composable
fun DeviceTab(vm: HwViewModel) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.uploadHexFile(u)
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Hardware Studio", "OTG cable se board jodo, code do, control karo")
        Glass {
            Text(vm.status, fontWeight = FontWeight.SemiBold, color = if (vm.connected) Aqua else Rose)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.scan() }) { Text("Scan USB") }
                if (vm.connected) OutlinedButton(onClick = { vm.disconnect() }) { Text("Disconnect") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Baud rate", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(9600, 57600, 115200).forEach { b ->
                    FilterChip(selected = vm.baud == b, onClick = {
                        vm.baud = b
                        if (vm.connected) vm.link.setBaud(b)
                    }, label = { Text("$b") })
                }
            }
        }
        vm.drivers.forEach { d ->
            Glass {
                val dev = d.device
                Text(dev.productName ?: "USB device", fontWeight = FontWeight.Bold)
                Text("VID ${"%04X".format(dev.vendorId)}  PID ${"%04X".format(dev.productId)}", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.connect(d) }) { Text("Connect") }
            }
        }
        Glass {
            Text("Flash (Arduino Uno / Nano)", fontWeight = FontWeight.Bold)
            Text("Bridge firmware ek baar daalo, phir Pins tab se kuch bhi control karo.", fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = vm.connected && !vm.busy, onClick = { vm.uploadBridge() }) { Text("Bridge daalo") }
                OutlinedButton(enabled = vm.connected && !vm.busy, onClick = { pick.launch("*/*") }) { Text(".hex upload") }
            }
            if (vm.busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { vm.progress }, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PinsTab(vm: HwViewModel) {
    var pin by remember { mutableStateOf("13") }
    var pwm by remember { mutableFloatStateOf(128f) }
    var servo by remember { mutableFloatStateOf(90f) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Pins", "Bridge firmware wale board ko seedha control")
        Glass {
            OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Pin (13, 9, A0)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.send("DW $pin 1") }) { Text("HIGH") }
                OutlinedButton(onClick = { vm.send("DW $pin 0") }) { Text("LOW") }
                OutlinedButton(onClick = { vm.send("DR $pin") }) { Text("Read") }
                OutlinedButton(onClick = { vm.send("AR $pin") }) { Text("Analog") }
            }
        }
        Glass {
            Text("PWM: ${pwm.toInt()}", fontWeight = FontWeight.SemiBold)
            Slider(value = pwm, onValueChange = { pwm = it }, valueRange = 0f..255f,
                onValueChangeFinished = { vm.send("PWM $pin ${pwm.toInt()}") })
        }
        Glass {
            Text("Servo: ${servo.toInt()}°", fontWeight = FontWeight.SemiBold)
            Slider(value = servo, onValueChange = { servo = it }, valueRange = 0f..180f,
                onValueChangeFinished = { vm.send("SERVO $pin ${servo.toInt()}") })
        }
        Glass {
            Text("Live analog stream", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.send("STREAM $pin 200") }) { Text("Start") }
                OutlinedButton(onClick = { vm.send("STREAM OFF") }) { Text("Stop") }
            }
            Text("Values Serial tab me dikhengi", fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun CodeTab(vm: HwViewModel, openAi: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Code", "Yahan paste karo, ya AI se mangao")
        Glass(Modifier.weight(1f)) {
            BasicTextField(
                value = vm.code,
                onValueChange = { vm.code = it },
                textStyle = TextStyle(color = androidx.compose.ui.graphics.Color(0xFFE8ECF8), fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                cursorBrush = SolidColor(Aqua),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = openAi) { Text("AI se pucho") }
            OutlinedButton(onClick = { vm.code = vm.bridgeSource() }) { Text("Bridge code") }
        }
        Text(
            "Note: .ino ko .hex banane ke liye PC (EXE app) ya cloud compile chahiye. Hex file ban jaye to Device tab se upload karo.",
            fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF9AA4C0)
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun AiTab(vm: HwViewModel, toCode: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var showSet by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    LaunchedEffect(vm.chat.size) { if (vm.chat.isNotEmpty()) state.animateScrollToItem(vm.chat.size - 1) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title("AI Assistant", "Kisi bhi hardware ka code mango")
            TextButton(onClick = { showSet = !showSet }) { Text("Settings") }
        }
        if (showSet) {
            Glass {
                OutlinedTextField(value = vm.apiKey, onValueChange = { vm.apiKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vm.model, onValueChange = { vm.model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vm.board, onValueChange = { vm.board = it }, label = { Text("Board") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveSettings(); showSet = false }) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.weight(1f), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.chat.isEmpty()) item {
                Glass {
                    Text("Try karo:", fontWeight = FontWeight.Bold)
                    Text("• Arduino Uno me 3 LED chaser banao\n• ESP32 se WiFi par relay on/off\n• DHT11 temperature OLED par dikhao", fontSize = 13.sp)
                }
            }
            items(vm.chat) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (mine) Violet.copy(alpha = 0.35f) else GlassFill).padding(12.dp)
                    ) {
                        Text(m.text, fontSize = 14.sp)
                        if (!mine) {
                            val c = AiClient.extractCode(m.text)
                            if (c != null) {
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = { vm.code = c; toCode() }) { Text("Code tab me daalo") }
                            }
                        }
                    }
                }
            }
            if (vm.aiBusy) item { Text("Soch raha hu...", color = Aqua) }
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Kya banana hai?") })
            Spacer(Modifier.width(8.dp))
            Button(enabled = !vm.aiBusy, onClick = { vm.askAi(input); input = "" }) { Text("Send") }
        }
    }
}

@Composable
fun SerialTab(vm: HwViewModel) {
    var input by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    LaunchedEffect(vm.serialLog.size) { if (vm.serialLog.isNotEmpty()) state.scrollToItem(vm.serialLog.size - 1) }
    Column(Modifier.fillMaxSize()) {
        Title("Serial Monitor", vm.status)
        Glass(Modifier.weight(1f)) {
            LazyColumn(state = state) {
                items(vm.serialLog) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Command bhejo") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.send(input); input = "" }) { Text("Send") }
            TextButton(onClick = { vm.serialLog.clear() }) { Text("Clear") }
        }
    }
}
