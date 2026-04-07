package com.example.maliai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.* // استيراد الأيقونات الحديثة
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// الألوان المعتمدة للتصميم الفخم
val DeepNavy = Color(0xFF060914)
val CardNavy = Color(0xFF11172B)
val MaaliBlue = Color(0xFF2D65FF)
val AiPurple = Color(0xFF7B61FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // التحكم في التنقل التفاعلي
                var currentScreen by remember { mutableStateOf("login") }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    when (currentScreen) {
                        "login" -> LoginScreen(onLoginSuccess = { currentScreen = "main" })
                        "main" -> MainAppContent()
                    }
                }
            }
        }
    }
}

// --- 1. شاشة تسجيل الدخول التفاعلية ---
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepNavy).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(100.dp).background(Brush.radialGradient(listOf(AiPurple, MaaliBlue)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("💰", fontSize = 40.sp)
        }
        Text("MaaliAI", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("مساعدك المالي الذكي", color = Color.Gray, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("البريد الإلكتروني") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("كلمة المرور") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaaliBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("دخول", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// --- 2. الواجهة الرئيسية الكاملة ---
@Composable
fun MainAppContent() {
    Scaffold(
        containerColor = DeepNavy,
        bottomBar = { MaaliBottomNavigation() }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            // الهيدر
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("مرحباً بك 👋", color = Color.Gray, fontSize = 14.sp)
                    Text("عبدالرحمن خالد", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = {}, modifier = Modifier.background(CardNavy, CircleShape)) {
                    Icon(Icons.Rounded.Notifications, null, tint = Color.Yellow)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // بطاقة الرصيد
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E294B)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الرصيد المتاح هذا الشهر", color = Color.Gray, fontSize = 14.sp)
                    Text("8,450 SAR", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { 0.56f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaaliBlue, trackColor = Color.White.copy(0.1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ملخص (الدخل، المصروف، الادخار)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("الدخل", "15,000", Color(0xFF4CAF50), Modifier.weight(1f))
                StatBox("المصروف", "6,550", Color(0xFFFF5252), Modifier.weight(1f))
                StatBox("الادخار", "8,450", MaaliBlue, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // نصيحة AI
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                border = BorderStroke(1.dp, AiPurple.copy(0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = AiPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("تحليل Maali: وفرت 12% من ميزانية الطعام!", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("آخر المعاملات", color = Color.White, fontWeight = FontWeight.Bold)

            // قائمة المعاملات
            TransactionItem("أسواق Tamimi", "-350 SAR", Icons.Rounded.ShoppingCart, Color(0xFF4DB6AC))
            TransactionItem("محطة Aramco", "-180 SAR", Icons.Rounded.LocalGasStation, Color.Yellow)
            TransactionItem("الراتب الشهري", "+15,000 SAR", Icons.Rounded.AccountBalanceWallet, Color.Green)
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardNavy)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.Gray, fontSize = 10.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun TransactionItem(title: String, amount: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(CardNavy, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).background(color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(amount, color = Color.White, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun MaaliBottomNavigation() {
    NavigationBar(containerColor = Color(0xFF11172B)) {
        NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("الرئيسية") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Rounded.BarChart, null) }, label = { Text("التحليل") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Rounded.AutoAwesome, null) }, label = { Text("AI") })
    }
}