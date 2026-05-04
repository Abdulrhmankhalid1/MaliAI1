package com.example.maliai

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsMessage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

// ===================== COLORS =====================

val BgDark    = Color(0xFF0D1117)
val BgCard    = Color(0xFF161B22)
val AccentBlue  = Color(0xFF2F81F7)
val AccentGold  = Color(0xFFE3B341)
val GreenColor  = Color(0xFF3FB950)
val RedColor    = Color(0xFFF85149)
val TextPrimary   = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)

// ===================== DATA =====================

enum class TxType   { INCOME, EXPENSE }
enum class TxSource { MANUAL, SMS }

data class Transaction(
    val id          : String   = UUID.randomUUID().toString(),
    val amount      : Double,
    val type        : TxType,
    val merchant    : String,
    val description : String   = "",
    val category    : String   = "عام",
    val date        : Date     = Date(),
    val source      : TxSource = TxSource.MANUAL
)

// ===================== VIEWMODEL (AndroidViewModel) =====================
// نستخدم AndroidViewModel حتى يعيش بنفس دورة حياة الـ Application
// وبالتالي الـ SmsReceiver يقدر يصل له مباشرة

class FinanceViewModel(app: Application) : AndroidViewModel(app) {

    private val _transactions = MutableStateFlow(
        listOf(
            Transaction(
                amount = 8450.0, type = TxType.INCOME,
                merchant = "الراتب الشهري", description = "راتب أبريل 2026",
                category = "راتب",
                date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -20) }.time
            ),
            Transaction(
                amount = 350.0, type = TxType.EXPENSE,
                merchant = "هايبر بنده", description = "مشتريات أسبوعية",
                category = "تسوق",
                date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -3) }.time
            ),
            Transaction(
                amount = 120.0, type = TxType.EXPENSE,
                merchant = "مطعم الأصيل", description = "غداء",
                category = "مطاعم",
                date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
            )
        )
    )

    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    val balance      get() = _transactions.value.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }
    val totalIncome  get() = _transactions.value.filter { it.type == TxType.INCOME  }.sumOf { it.amount }
    val totalExpenses get()= _transactions.value.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }

    fun addTransaction(tx: Transaction) {
        _transactions.value = listOf(tx) + _transactions.value
    }

    // ---- تحليل رسالة SMS وإضافتها فوراً ----
    fun handleSms(sender: String, body: String) {
        val isExpense = body.contains("خصم") || body.contains("سحب") ||
                body.contains("مدين") || body.contains("تم خصم") ||
                body.contains("purchase", ignoreCase = true) ||
                body.contains("deducted", ignoreCase = true)

        val isIncome = body.contains("إيداع") || body.contains("ايداع") ||
                body.contains("دائن") || body.contains("تم إضافة") ||
                body.contains("credit", ignoreCase = true)

        if (!isExpense && !isIncome) return

        val amountRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(?:ريال|SAR|SR)""", RegexOption.IGNORE_CASE)
        val amount = amountRegex.find(body)
            ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return

        val merchantRegex = Regex("""(?:من|لدى|at|@|بـ|متجر)\s*([^\n,،.]{2,30})""", RegexOption.IGNORE_CASE)
        val merchant = merchantRegex.find(body)?.groupValues?.get(1)?.trim()
            ?.ifBlank { sender } ?: sender

        addTransaction(
            Transaction(
                amount      = amount,
                type        = if (isExpense) TxType.EXPENSE else TxType.INCOME,
                merchant    = merchant,
                description = body.take(100),
                category    = "SMS",
                source      = TxSource.SMS
            )
        )
    }
}

// ===================== APPLICATION CLASS =====================
// نحتفظ بنسخة واحدة من الـ ViewModel على مستوى الـ App بالكامل

class MaliApp : Application() {
    lateinit var financeViewModel: FinanceViewModel
        private set

    override fun onCreate() {
        super.onCreate()
        financeViewModel = ViewModelProvider.AndroidViewModelFactory
            .getInstance(this)
            .create(FinanceViewModel::class.java)
    }
}

// ===================== SMS RECEIVER =====================
// يصل مباشرة للـ ViewModel عبر الـ Application

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return

        val app = context.applicationContext as? MaliApp ?: return

        for (pdu in pdus) {
            val msg    = SmsMessage.createFromPdu(pdu as ByteArray)
            val sender = msg.originatingAddress ?: ""
            val body   = msg.messageBody        ?: ""
            // هنا يُحدَّث الـ StateFlow مباشرة → الـ UI يتحدث فوراً
            app.financeViewModel.handleSms(sender, body)
        }
    }
}

// ===================== MAIN ACTIVITY =====================

class MainActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermission.launch(missing.toTypedArray())

        // نستخدم نفس الـ ViewModel من الـ Application
        val vm = (application as MaliApp).financeViewModel

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary       = AccentBlue,
                    background    = BgDark,
                    surface       = BgCard,
                    onBackground  = TextPrimary,
                    onSurface     = TextPrimary
                )
            ) {
                AppNavigation(vm)
            }
        }
    }
}

// ===================== NAVIGATION =====================

enum class Screen { DASHBOARD, TRANSACTIONS, DETAIL }

@Composable
fun AppNavigation(vm: FinanceViewModel) {
    var screen   by remember { mutableStateOf(Screen.DASHBOARD) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var showAdd  by remember { mutableStateOf(false) }

    val transactions by vm.transactions.collectAsState()

    Box(Modifier.fillMaxSize().background(BgDark)) {
        when (screen) {
            Screen.DASHBOARD -> DashboardScreen(
                vm       = vm,
                transactions = transactions,
                onSeeAll = { screen = Screen.TRANSACTIONS },
                onTxClick = { selected = it; screen = Screen.DETAIL },
                onAdd    = { showAdd = true }
            )
            Screen.TRANSACTIONS -> TransactionsScreen(
                transactions = transactions,
                onBack   = { screen = Screen.DASHBOARD },
                onTxClick = { selected = it; screen = Screen.DETAIL },
                onAdd    = { showAdd = true }
            )
            Screen.DETAIL -> selected?.let {
                DetailScreen(tx = it, onBack = { screen = Screen.TRANSACTIONS })
            }
        }

        if (showAdd) {
            AddDialog(
                onDismiss = { showAdd = false },
                onConfirm = { vm.addTransaction(it); showAdd = false }
            )
        }
    }
}

// ===================== DASHBOARD =====================

@Composable
fun DashboardScreen(
    vm: FinanceViewModel,
    transactions: List<Transaction>,
    onSeeAll: () -> Unit,
    onTxClick: (Transaction) -> Unit,
    onAdd: () -> Unit
) {
    // نقرأ الـ state مباشرة بدون cache حتى يتحدث الـ UI فوراً
    val txList by vm.transactions.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("مرحباً 👋", color = TextSecondary, fontSize = 13.sp)
                    Text("محفظتي", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, null, tint = AccentBlue)
                }
            }
        }

        item {
            // نمرر القيم الحية من vm مباشرة
            BalanceCard(vm.balance, vm.totalIncome, vm.totalExpenses)
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("آخر العمليات", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("عرض الكل", color = AccentBlue, fontSize = 13.sp,
                    modifier = Modifier.clickable { onSeeAll() })
            }
        }

        items(txList.take(6), key = { it.id }) { tx ->
            TxRow(tx = tx, onClick = { onTxClick(tx) })
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun BalanceCard(balance: Double, income: Double, expenses: Double) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(
                listOf(Color(0xFF1F3A6E), Color(0xFF163259), Color(0xFF0D2140))
            ))
            .padding(24.dp)
    ) {
        Column {
            Text("إجمالي الرصيد", color = Color.White.copy(0.65f), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "SAR ${"%,.2f".format(balance)}",
                color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(20.dp))

            val ratio = if (income > 0) (expenses / income).coerceIn(0.0, 1.0).toFloat() else 0f
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = AccentGold, trackColor = Color.White.copy(0.15f)
            )
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatChip("الدخل",       income,   GreenColor, Icons.Default.KeyboardArrowUp)
                StatChip("المصروفات",   expenses, RedColor,   Icons.Default.KeyboardArrowDown)
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: Double, color: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, color = Color.White.copy(0.65f), fontSize = 11.sp)
            Text("SAR ${"%,.0f".format(value)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ===================== TX ROW =====================

@Composable
fun TxRow(tx: Transaction, onClick: () -> Unit) {
    val isExp = tx.type == TxType.EXPENSE
    val color = if (isExp) RedColor else GreenColor
    val df    = SimpleDateFormat("dd MMM", Locale("ar"))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onClick() },
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isExp) Icons.Default.ShoppingCart else Icons.Default.AccountBalance,
                    null, tint = color, modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.category, color = TextSecondary, fontSize = 11.sp)
                    if (tx.source == TxSource.SMS) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(AccentGold.copy(0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) { Text("SMS", color = AccentGold, fontSize = 9.sp) }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isExp) "−" else "+"} ${"%,.2f".format(tx.amount)}",
                    color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Text(df.format(tx.date), color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ===================== TRANSACTIONS SCREEN =====================

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    onBack: () -> Unit,
    onTxClick: (Transaction) -> Unit,
    onAdd: () -> Unit
) {
    var filter by remember { mutableStateOf("الكل") }
    val filters = listOf("الكل", "مصروفات", "دخل", "SMS")

    val filtered = transactions.filter {
        when (filter) {
            "مصروفات" -> it.type == TxType.EXPENSE
            "دخل"     -> it.type == TxType.INCOME
            "SMS"     -> it.source == TxSource.SMS
            else      -> true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(52.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Text(
                "جميع العمليات (${transactions.size})",
                color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null, tint = AccentBlue)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick  = { filter = f },
                    label    = { Text(f, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        selectedLabelColor     = Color.White,
                        containerColor         = BgCard,
                        labelColor             = TextSecondary
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.id }) { tx ->
                TxRow(tx = tx, onClick = { onTxClick(tx) })
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد عمليات", color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ===================== DETAIL SCREEN =====================

@Composable
fun DetailScreen(tx: Transaction, onBack: () -> Unit) {
    val isExp = tx.type == TxType.EXPENSE
    val color = if (isExp) RedColor else GreenColor
    val df    = SimpleDateFormat("EEEE، d MMMM yyyy\nhh:mm a", Locale("ar"))

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(52.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
            Text("تفاصيل العملية", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(68.dp).clip(CircleShape).background(color.copy(0.15f)),
                    contentAlignment = Alignment.Center) {
                    Icon(
                        if (isExp) Icons.Default.ShoppingCart else Icons.Default.AccountBalance,
                        null, tint = color, modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "${if (isExp) "−" else "+"} SAR ${"%,.2f".format(tx.amount)}",
                    color = color, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(tx.merchant, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isExp) "سحب / مصروف" else "إيداع / دخل",
                    color = color.copy(0.75f), fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)) {
            Column(Modifier.padding(20.dp)) {
                DetailItem(Icons.Default.Store,         "المتجر / الجهة",  tx.merchant)
                DetailItem(Icons.Default.CalendarToday, "التاريخ والوقت",  df.format(tx.date))
                DetailItem(Icons.Default.Category,      "الفئة",           tx.category)
                DetailItem(Icons.Default.AttachMoney,   "المبلغ",          "SAR ${"%,.2f".format(tx.amount)}")
                DetailItem(Icons.Default.SwapVert,      "نوع العملية",     if (isExp) "مصروف / سحب" else "دخل / إيداع")
                DetailItem(Icons.Default.Sms,           "المصدر",
                    if (tx.source == TxSource.SMS) "رسالة SMS تلقائية ✅" else "إدخال يدوي")
                if (tx.description.isNotBlank())
                    DetailItem(Icons.Default.Description, "نص الرسالة", tx.description)
            }
        }
    }
}

@Composable
fun DetailItem(icon: ImageVector, label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDivider(color = Color.White.copy(0.05f))
    }
}

// ===================== ADD DIALOG =====================

@Composable
fun AddDialog(onDismiss: () -> Unit, onConfirm: (Transaction) -> Unit) {
    var amount      by remember { mutableStateOf("") }
    var merchant    by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf("عام") }
    var type        by remember { mutableStateOf(TxType.EXPENSE) }
    val categories = listOf("عام", "مطاعم", "تسوق", "مواصلات", "صحة", "ترفيه", "راتب", "دخل إضافي")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        shape            = RoundedCornerShape(24.dp),
        title = { Text("إضافة عملية", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgDark)) {
                    listOf(TxType.EXPENSE to "مصروف", TxType.INCOME to "دخل").forEach { (t, lbl) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (type == t) AccentBlue else Color.Transparent)
                                .clickable { type = t }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(lbl, color = if (type == t) Color.White else TextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ (SAR)", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = TextSecondary.copy(0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("المتجر / الجهة", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = TextSecondary.copy(0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("الوصف (اختياري)", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = TextSecondary.copy(0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )

                Text("الفئة", color = TextSecondary, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick  = { category = cat },
                            label    = { Text(cat, fontSize = 11.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue, selectedLabelColor = Color.White,
                                containerColor = BgDark, labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@Button
                    if (merchant.isBlank()) return@Button
                    onConfirm(Transaction(amount = amt, type = type, merchant = merchant, description = description, category = category))
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape  = RoundedCornerShape(12.dp)
            ) { Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = TextSecondary) }
        }
    )
}