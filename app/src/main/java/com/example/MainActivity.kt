package com.example

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        val prefs = PreferencesHelper(this)
        if (prefs.isLockTimeActive()) {
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                putExtra("REASON", "LOCKDOWN")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(lockIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesHelper(context) }
    
    var isLockEnabled by remember { mutableStateOf(prefs.isLockEnabled) }
    var startHour by remember { mutableStateOf(prefs.startHour) }
    var startMinute by remember { mutableStateOf(prefs.startMinute) }
    var endHour by remember { mutableStateOf(prefs.endHour) }
    var endMinute by remember { mutableStateOf(prefs.endMinute) }
    
    var midDayLockEnabled by remember { mutableStateOf(prefs.midDayLockEnabled) }
    var midDayStartHour by remember { mutableStateOf(prefs.midDayStartHour) }
    var midDayStartMinute by remember { mutableStateOf(prefs.midDayStartMinute) }
    var midDayEndHour by remember { mutableStateOf(prefs.midDayEndHour) }
    var midDayEndMinute by remember { mutableStateOf(prefs.midDayEndMinute) }
    
    var inLockTime by remember { mutableStateOf(prefs.isLockTimeActive()) }
    var hasUsageAccess by remember { mutableStateOf(false) }
    var hasOverlayAccess by remember { mutableStateOf(false) }
    var hasBatteryOptimization by remember { mutableStateOf(false) }

    fun checkUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun checkOverlayAccess(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun checkBatteryOptimization(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        val intent = Intent(context, FocusTrackingService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            context.startService(intent)
        }
        while(true) {
            inLockTime = prefs.isLockTimeActive()
            hasUsageAccess = checkUsageAccess()
            hasOverlayAccess = checkOverlayAccess()
            hasBatteryOptimization = checkBatteryOptimization()
            kotlinx.coroutines.delay(2000)
        }
    }

    var showDocLibrary by remember { mutableStateOf(false) }
    var activeDoc by remember { mutableStateOf<FocusPdfDoc?>(null) }

    var selectedDurationMinutes by remember { mutableIntStateOf(120) }

    fun activateManualLock(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        val endTime = if (durationMinutes > 0) {
            now + (durationMinutes.toLong() * 60L * 1000L)
        } else if (durationMinutes == -1) {
            val cal = java.util.Calendar.getInstance()
            if (cal.get(java.util.Calendar.HOUR_OF_DAY) >= 7) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 7)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } else {
            0L
        }

        prefs.manualLockActive = true
        prefs.manualLockEndTimeMillis = endTime
        inLockTime = true

        val serviceIntent = Intent(context, FocusTrackingService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {}

        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
            putExtra("REASON", "LOCKDOWN")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val bundle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        try {
            if (bundle != null) {
                context.startActivity(lockIntent, bundle)
            } else {
                context.startActivity(lockIntent)
            }
        } catch (e: Exception) {}
        (context as? ComponentActivity)?.finish()
    }

    fun updateEnableStatus(enabled: Boolean) {
        prefs.isLockEnabled = enabled
        isLockEnabled = enabled
    }

    if (activeDoc != null) {
        PdfReaderScreen(
            doc = activeDoc!!,
            onBack = { activeDoc = null }
        )
        return
    }

    if (showDocLibrary) {
        PdfLibraryScreen(
            onOpenDoc = { doc -> activeDoc = doc },
            onBack = { showDocLibrary = false },
            onPickPdfRequested = {
                prefs.allowDocumentPickerUntilMillis = System.currentTimeMillis() + 60_000L
            },
            onPickPdfFinished = {
                prefs.allowDocumentPickerUntilMillis = 0L
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Lock") },
                actions = {
                    IconButton(onClick = { showDocLibrary = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Reading Library"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasUsageAccess || !hasOverlayAccess || !hasBatteryOptimization) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ MISSING PERMISSIONS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        val missing = mutableListOf<String>()
                        if (!hasUsageAccess) missing.add("Usage Access")
                        if (!hasOverlayAccess) missing.add("Display Over Other Apps")
                        if (!hasBatteryOptimization) missing.add("Ignore Battery Optimizations")
                        Text("App Blocking will NOT work because ${missing.joinToString(" and ")} is missing. You MUST grant these permissions.", color = MaterialTheme.colorScheme.onErrorContainer)
                        
                        Spacer(Modifier.height(16.dp))
                        if (!hasUsageAccess) {
                            Button(
                                onClick = { 
                                    prefs.allowSettingsUntilMillis = System.currentTimeMillis() + 5 * 60 * 1000L
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Grant Usage Access") }
                        }
                        if (!hasOverlayAccess) {
                            Button(
                                onClick = { 
                                    prefs.allowSettingsUntilMillis = System.currentTimeMillis() + 5 * 60 * 1000L
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Grant Display Over Other Apps") }
                        }
                        if (!hasBatteryOptimization) {
                            Button(
                                onClick = { 
                                    prefs.allowSettingsUntilMillis = System.currentTimeMillis() + 5 * 60 * 1000L
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Allow Background Execution") }
                        }
                    }
                }
                return@Scaffold // Hide the rest of the UI until granted
            }

            if (inLockTime) {
                var lockRemainingText by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    while (true) {
                        val now = System.currentTimeMillis()
                        val target = when {
                            prefs.manualLockActive && prefs.manualLockEndTimeMillis > 0 -> prefs.manualLockEndTimeMillis
                            prefs.quickLockEndTimeMillis > 0 -> prefs.quickLockEndTimeMillis
                            else -> 0L
                        }
                        if (target > now) {
                            val diffSec = (target - now) / 1000
                            val days = diffSec / (24 * 3600)
                            val hours = (diffSec % (24 * 3600)) / 3600
                            val minutes = (diffSec % 3600) / 60
                            val seconds = diffSec % 60
                            lockRemainingText = when {
                                days >= 30 -> {
                                    val months = days / 30
                                    val remDays = days % 30
                                    if (remDays > 0) String.format("%dmo %dd %dh remaining", months, remDays, hours)
                                    else String.format("%dmo %dh remaining", months, hours)
                                }
                                days > 0 -> String.format("%dd %02dh %02dm %02ds remaining", days, hours, minutes, seconds)
                                hours > 0 -> String.format("%dh %02dm %02ds remaining", hours, minutes, seconds)
                                else -> String.format("%02dm %02ds remaining", minutes, seconds)
                            }
                        } else if (prefs.manualLockActive && prefs.manualLockEndTimeMillis == 0L) {
                            lockRemainingText = "Locked Indefinitely (No timer)"
                        } else {
                            lockRemainingText = "Lockdown active"
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "LOCKDOWN ACTIVE",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Cannot be disabled. Strict lock in progress.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "TIME REMAINING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = lockRemainingText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "All restricted applications are blocked. Restarting your phone will not bypass this lockdown.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                                        putExtra("REASON", "LOCKDOWN")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    context.startActivity(lockIntent)
                                    (context as? ComponentActivity)?.finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("GO TO LOCK SCREEN", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showDocLibrary = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Read Books / PDF")
                            }
                        }
                    }
                }
            } else {
                // Immediate Manual Lockdown (Primary Feature Requested)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Switch On Lock Immediately",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Manually lock the entire phone now. No unexpected auto-locks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Switch(
                                checked = false,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        activateManualLock(selectedDurationMinutes)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Choose Lockdown Duration:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(8.dp))

                        // Category 1: Hours / Quick
                        Text(
                            "HOURS / SHORT SESSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))

                        val shortPresets = listOf(
                            "30 Mins" to 30,
                            "1 Hour" to 60,
                            "2 Hours" to 120,
                            "4 Hours" to 240,
                            "8 Hours" to 480,
                            "12 Hours" to 720,
                            "Until 7 AM" to -1
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shortPresets.forEach { (label, minutes) ->
                                val isSelected = selectedDurationMinutes == minutes
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDurationMinutes = minutes },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Category 2: Days & Weeks
                        Text(
                            "DAYS & WEEKS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))

                        val multiDayPresets = listOf(
                            "1 Day" to 1440,
                            "2 Days" to 2880,
                            "5 Days" to 7200,
                            "1 Week" to 10080,
                            "2 Weeks" to 20160
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            multiDayPresets.forEach { (label, minutes) ->
                                val isSelected = selectedDurationMinutes == minutes
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDurationMinutes = minutes },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Category 3: Months & 1 Year & Indefinite
                        Text(
                            "MONTHS, 1 YEAR & INDEFINITE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))

                        val longTermPresets = listOf(
                            "1 Month" to 43200,
                            "3 Months" to 129600,
                            "6 Months" to 259200,
                            "1 Year" to 525600,
                            "Indefinite" to 0
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            longTermPresets.forEach { (label, minutes) ->
                                val isSelected = selectedDurationMinutes == minutes
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDurationMinutes = minutes },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val durationDescription = when (selectedDurationMinutes) {
                            -1 -> "Lockdown active until tomorrow 7:00 AM."
                            0 -> "Lockdown active indefinitely with no auto-unlock timer."
                            30 -> "30 minutes quick focus lockdown."
                            60 -> "1 hour deep focus lockdown."
                            120 -> "2 hours study and work lockdown."
                            240 -> "4 hours deep immersion lockdown."
                            480 -> "8 hours workday lockdown."
                            720 -> "12 hours half-day lockdown."
                            1440 -> "1 full day (24 hours) complete lockdown."
                            2880 -> "2 days weekend detox lockdown."
                            7200 -> "5 days complete school/work week lockdown."
                            10080 -> "1 week (7 days) deep focus lockdown."
                            20160 -> "2 weeks habit-reset lockdown."
                            43200 -> "1 month (30 days) extreme digital detox."
                            129600 -> "3 months (90 days) monk mode lockdown."
                            259200 -> "6 months life transformation lockdown."
                            525600 -> "1 year (365 days) maximum discipline lockdown."
                            else -> "$selectedDurationMinutes minutes lockdown."
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    durationDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                activateManualLock(selectedDurationMinutes)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("LOCK PHONE NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Document & PDF Reader Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDocLibrary = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Document & PDF Reader",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Import and read books & notes offline during lock",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Open Library",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Scheduled Auto-Lock (Turned Off by default per user request)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scheduled Auto-Lock (Turned OFF)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Auto-lock is switched OFF. You control locking manually via the switch above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Overnight Auto-Lock", fontWeight = FontWeight.Medium)
                                Text(if (isLockEnabled) "Enabled" else "Turned Off (Manual mode)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isLockEnabled,
                                onCheckedChange = { 
                                    isLockEnabled = it
                                    prefs.isLockEnabled = it
                                    inLockTime = prefs.isLockTimeActive()
                                    context.sendBroadcast(Intent(context, RestartReceiver::class.java).apply { action = "com.example.RESTART_SERVICE" })
                                }
                            )
                        }
                        if (isLockEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Start Time:")
                                Button(onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        prefs.startHour = h
                                        prefs.startMinute = m
                                        startHour = h
                                        startMinute = m
                                    }, startHour, startMinute, true).show()
                                }) {
                                    Text(String.format("%02d:%02d", startHour, startMinute))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("End Time:")
                                Button(onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        prefs.endHour = h
                                        prefs.endMinute = m
                                        endHour = h
                                        endMinute = m
                                    }, endHour, endMinute, true).show()
                                }) {
                                    Text(String.format("%02d:%02d", endHour, endMinute))
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mid-Day Auto-Lock", fontWeight = FontWeight.Medium)
                                Text(if (midDayLockEnabled) "Enabled" else "Turned Off (Manual mode)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = midDayLockEnabled,
                                onCheckedChange = { 
                                    midDayLockEnabled = it
                                    prefs.midDayLockEnabled = it
                                    inLockTime = prefs.isLockTimeActive()
                                    context.sendBroadcast(Intent(context, RestartReceiver::class.java).apply { action = "com.example.RESTART_SERVICE" })
                                }
                            )
                        }
                        if (midDayLockEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Start Time:")
                                Button(onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        prefs.midDayStartHour = h
                                        prefs.midDayStartMinute = m
                                        midDayStartHour = h
                                        midDayStartMinute = m
                                    }, midDayStartHour, midDayStartMinute, true).show()
                                }) {
                                    Text(String.format("%02d:%02d", midDayStartHour, midDayStartMinute))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("End Time:")
                                Button(onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        prefs.midDayEndHour = h
                                        prefs.midDayEndMinute = m
                                        midDayEndHour = h
                                        midDayEndMinute = m
                                    }, midDayEndHour, midDayEndMinute, true).show()
                                }) {
                                    Text(String.format("%02d:%02d", midDayEndHour, midDayEndMinute))
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("YouTube Limit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("A permanent 3-hour daily limit is enforced for YouTube. This rule is always active and cannot be removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            
            Text("Permissions Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            
            Button(
                onClick = {
                    prefs.allowSettingsUntilMillis = System.currentTimeMillis() + 60_000 * 5 // 5 minutes
                    try {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:" + context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val backupIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(backupIntent)
                        } catch (e2: Exception) {
                            try {
                                val fallBack = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(fallBack)
                                android.widget.Toast.makeText(context, "Please find App Usage Access manually.", android.widget.Toast.LENGTH_LONG).show()
                            } catch (e3: Exception) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enable Usage Access (REQUIRED to block apps)")
            }

            Button(
                onClick = {
                    prefs.allowSettingsUntilMillis = System.currentTimeMillis() + 60_000 * 5 // 5 minutes
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:" + context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val backupIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(backupIntent)
                        } catch (e2: Exception) {
                            try {
                                val fallBack = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(fallBack)
                            } catch (e3: Exception) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enable Display Over Other Apps")
            }

            Button(
                onClick = {
                    prefs.allowSettingsUntilMillis = System.currentTimeMillis() + 60_000 * 5 // 5 minutes
                    val componentName = ComponentName(context, AdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Requires Device Admin to prevent easy uninstallation during lock hours.")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Make it Hard to Remove (Device Admin)")
            }

            Text(
                "Warning: Enabling Device Admin blocks standard uninstallation. You must disable it first in Settings to remove this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
