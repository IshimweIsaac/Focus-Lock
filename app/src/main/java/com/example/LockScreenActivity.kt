package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

enum class LockViewMode {
    STATUS,
    LIBRARY,
    READER
}

class LockScreenActivity : ComponentActivity() {
    private var isLaunchingAllowedApp = false

    private val forceFrontReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.example.FORCE_FRONT") {
                isLaunchingAllowedApp = false
                val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                try {
                    activityManager.moveTaskToFront(taskId, 0)
                } catch (e: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferencesHelper(this)
        if (!prefs.isLockTimeActive()) {
            finish()
            return
        }

        // Show over lockscreen without forcing screen on or preventing screen power-off
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        val serviceIntent = Intent(this, FocusTrackingService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {}

        val filter = android.content.IntentFilter("com.example.FORCE_FRONT")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(forceFrontReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(forceFrontReceiver, filter)
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesHelper(this)
        if (!prefs.isLockTimeActive()) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(forceFrontReceiver)
        } catch (e: Exception) {}

        val prefs = PreferencesHelper(this)
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenOn = powerManager?.isInteractive ?: true
        // Only restart if active, allowed app is not launching, screen is actually on, and no call/USSD is active!
        if (prefs.isLockTimeActive() && !isLaunchingAllowedApp && isScreenOn && !TelephonyHelper.isCallOrUssdActive(this)) {
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("REASON", "LOCKDOWN")
            }
            try {
                startActivity(lockIntent)
            } catch (e: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val prefs = PreferencesHelper(this)
        if (!prefs.isLockTimeActive()) {
            finish()
            return
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val reason = intent.getStringExtra("REASON")

        enableEdgeToEdge()

        // Hide system bars for true full screen kiosk mode
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

        setContent {
            MyApplicationTheme {
                LockScreenRoot(
                    reason = reason,
                    onPickPdfRequested = {
                        isLaunchingAllowedApp = true
                        val prefs = PreferencesHelper(this@LockScreenActivity)
                        prefs.allowDocumentPickerUntilMillis = System.currentTimeMillis() + 60_000L
                    },
                    onPickPdfFinished = {
                        isLaunchingAllowedApp = false
                        val prefs = PreferencesHelper(this@LockScreenActivity)
                        prefs.allowDocumentPickerUntilMillis = 0L
                    },
                    onOpenPhoneRequested = {
                        isLaunchingAllowedApp = true
                        TelephonyHelper.openDialer(this@LockScreenActivity)
                    }
                )
            }
        }
    }

    override fun onBackPressed() {
        // Prevent going back out of the lock screen only while active
        val prefs = PreferencesHelper(this)
        if (!prefs.isLockTimeActive()) {
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefs = PreferencesHelper(this)
        if (prefs.isLockTimeActive() && !isLaunchingAllowedApp && !TelephonyHelper.isCallOrUssdActive(this)) {
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("REASON", intent?.getStringExtra("REASON") ?: "LOCKDOWN")
            }
            try {
                startActivity(lockIntent)
            } catch (e: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val prefs = PreferencesHelper(this)
        if (!prefs.isLockTimeActive()) {
            finish()
            return
        }
        if (hasFocus) {
            isLaunchingAllowedApp = false
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else if (prefs.isLockTimeActive() && !isLaunchingAllowedApp && !TelephonyHelper.isCallOrUssdActive(this)) {
            // User tried opening notification shade or recents app menu - pull immediately back
            val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            try {
                activityManager?.moveTaskToFront(taskId, 0)
            } catch (e: Exception) {}

            val lockIntent = Intent(this@LockScreenActivity, LockScreenActivity::class.java).apply {
                putExtra("REASON", intent?.getStringExtra("REASON") ?: "LOCKDOWN")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                startActivity(lockIntent)
            } catch (e: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        val prefs = PreferencesHelper(this)
        if (!prefs.isLockTimeActive() || isLaunchingAllowedApp || TelephonyHelper.isCallOrUssdActive(this)) return

        val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        try {
            activityManager?.moveTaskToFront(taskId, 0)
        } catch (e: Exception) {}

        val lockIntent = Intent(this@LockScreenActivity, LockScreenActivity::class.java).apply {
            putExtra("REASON", intent?.getStringExtra("REASON") ?: "LOCKDOWN")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(lockIntent)
        } catch (e: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        val prefs = PreferencesHelper(this)
        if (prefs.isLockTimeActive() && !isLaunchingAllowedApp && !TelephonyHelper.isCallOrUssdActive(this)) {
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("REASON", intent?.getStringExtra("REASON") ?: "LOCKDOWN")
            }
            try {
                startActivity(lockIntent)
            } catch (e: Exception) {}
        }
    }
}

@Composable
fun LockScreenRoot(
    reason: String?,
    onPickPdfRequested: () -> Unit,
    onPickPdfFinished: () -> Unit,
    onOpenPhoneRequested: () -> Unit
) {
    var viewMode by remember { mutableStateOf(LockViewMode.STATUS) }
    var selectedDoc by remember { mutableStateOf<FocusPdfDoc?>(null) }

    when (viewMode) {
        LockViewMode.STATUS -> {
            LockStatusContent(
                reason = reason,
                onOpenDocReader = { viewMode = LockViewMode.LIBRARY },
                onOpenPhoneRequested = onOpenPhoneRequested
            )
        }
        LockViewMode.LIBRARY -> {
            PdfLibraryScreen(
                onOpenDoc = { doc ->
                    selectedDoc = doc
                    viewMode = LockViewMode.READER
                },
                onBack = { viewMode = LockViewMode.STATUS },
                onPickPdfRequested = onPickPdfRequested,
                onPickPdfFinished = onPickPdfFinished
            )
        }
        LockViewMode.READER -> {
            selectedDoc?.let { doc ->
                PdfReaderScreen(
                    doc = doc,
                    onBack = { viewMode = LockViewMode.LIBRARY }
                )
            } ?: run {
                viewMode = LockViewMode.LIBRARY
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockStatusContent(
    reason: String?,
    onOpenDocReader: () -> Unit,
    onOpenPhoneRequested: () -> Unit
) {
    val isYoutube = reason == "YOUTUBE"
    val isGeneralLimit = reason == "GENERAL_LIMIT"

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PreferencesHelper(context) }
    var remainingTimeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            if (!prefs.isLockTimeActive()) {
                (context as? android.app.Activity)?.finish()
                break
            }
            val now = System.currentTimeMillis()
            val targetEnd = when {
                prefs.manualLockActive && prefs.manualLockEndTimeMillis > 0 -> prefs.manualLockEndTimeMillis
                prefs.quickLockEndTimeMillis > now -> prefs.quickLockEndTimeMillis
                else -> 0L
            }
            if (targetEnd > now) {
                val diffSec = (targetEnd - now) / 1000
                val days = diffSec / 86400
                val hours = (diffSec % 86400) / 3600
                val minutes = (diffSec % 3600) / 60
                val seconds = diffSec % 60
                remainingTimeText = when {
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
                remainingTimeText = "Locked Indefinitely"
            } else {
                remainingTimeText = "All apps restricted"
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("FocusGate", fontWeight = FontWeight.Medium)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDocReader) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Document Reader",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(16.dp))

            // Main visual container
            Box(contentAlignment = Alignment.Center) {
                // Outer ring 1
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                )
                // Outer ring 2
                Box(
                    modifier = Modifier
                        .size(208.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                )
                // Center circle
                Surface(
                    modifier = Modifier.size(176.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isYoutube) "PERMANENT BAN" else if (isGeneralLimit) "TIMEOUT" else "LOCKED",
                            fontWeight = FontWeight.Light,
                            letterSpacing = 4.sp,
                            fontSize = 24.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isYoutube) "YOUTUBE IS BLOCKED" else if (isGeneralLimit) "5 HOUR COOLDOWN" else "TIME TO PRAY",
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = if (isYoutube) "YouTube is Permanently Blocked" else if (isGeneralLimit) "Usage Limit Reached" else "Strict Mode Active",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isYoutube) "You have permanently blocked YouTube on this device. It can no longer be accessed." else if (isGeneralLimit) "You have used this app for 30 minutes consecutively. It is now locked for 5 hours." else "All applications are currently restricted.\n\nTake a moment to disconnect, pray, and rest.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Document Reader Card (Requested Feature)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDocReader() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Document & PDF Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Read imported books & notes offline during lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Phone & USSD Services Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPhoneRequested() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Calls and USSD",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Phone & USSD Services",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Make calls & dial USSD codes (*182#, *131#, Mobile Money)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Dial",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isYoutube) "PERMANENT RESTRICTION" else if (isGeneralLimit) "COOLDOWN ACTIVE" else if (prefs.manualLockActive) "MANUAL LOCK ACTIVE" else "LOCKDOWN ACTIVE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isYoutube) "Never coming back" else if (isGeneralLimit) "Unlocks in 5 hours" else if (remainingTimeText.isNotEmpty()) remainingTimeText else "Rest & pray",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
