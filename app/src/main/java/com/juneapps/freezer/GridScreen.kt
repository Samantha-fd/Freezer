package com.juneapps.freezer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutLinearInEasing

private sealed class GridItem {
    data class App(val app: InstalledApp) : GridItem()
    data object AddButton : GridItem()
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(viewModel: FreezerViewModel = viewModel(), lockEnabled: Boolean = false, onToggleLock: () -> Unit = {}) {
    val selectedApps by viewModel.selectedApps.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    val shatteringIds by viewModel.shatteringAppIds.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var appPendingRemoval by remember { mutableStateOf<InstalledApp?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lastResult) {
        lastResult?.let { msg ->
            val text = when (msg) {
                is FreezerViewModel.UiMessage.Info -> msg.text
                is FreezerViewModel.UiMessage.Error -> msg.text
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearLastResult()
        }
    }

    val gridItems: List<GridItem> = remember(selectedApps) {
        selectedApps.map { GridItem.App(it) } + GridItem.AddButton
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Freezer", fontWeight = FontWeight.Bold)
                },
                actions = {
                    TextButton(onClick = onToggleLock) { Text(if (lockEnabled) "Unlock setting" else "App lock", color = Color(0xFFB3E5FC)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A1929),
                    titleContentColor = Color(0xFFB3E5FC)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A1929)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ShizukuStatusBanner(status = shizukuStatus)

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = gridItems,
                    key = { item ->
                        when (item) {
                            is GridItem.App -> item.app.packageName
                            is GridItem.AddButton -> "add_button"
                        }
                    }
                ) { item ->
                    when (item) {
                        is GridItem.App -> FrozenAppSlot(
                            app = item.app,
                            isShattering = shatteringIds.contains(item.app.packageName),
                            onShatterEnd = { viewModel.completeShatter(item.app.packageName) },
                            onClick = { appPendingRemoval = item.app }
                        )
                        is GridItem.AddButton -> AddButtonSlot(
                            onClick = {
                                if (shizukuStatus == FreezerViewModel.ShizukuStatus.READY) {
                                    showPicker = true
                                } else if (shizukuStatus == FreezerViewModel.ShizukuStatus.NEEDS_PERMISSION) {
                                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            viewModel = viewModel,
            onDismiss = { showPicker = false },
            onAppsSelected = { apps -> viewModel.selectApps(apps) }
        )
    }

    val fallbackApp by viewModel.fallbackApp.collectAsState()
    fallbackApp?.let { app ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveFallback(false) },
            title = { Text("Could not keep ${app.appName} visible") },
            text = { Text("Suspension was unavailable. Disable this app instead? Its launcher icon may disappear or move.") },
            confirmButton = { TextButton(onClick = { viewModel.resolveFallback(true) }) { Text("Disable app") } },
            dismissButton = { TextButton(onClick = { viewModel.resolveFallback(false) }) { Text("Cancel") } }
        )
    }

    appPendingRemoval?.let { app ->
        UnfreezeDialog(
            app = app,
            onDismiss = { appPendingRemoval = null },
            onConfirm = {
                viewModel.removeApp(app)
                appPendingRemoval = null
            }
        )
    }

    if (showOnboarding) {
        OnboardingScreen(onDismiss = { viewModel.dismissOnboarding() })
    }
}

@Composable
fun OnboardingScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1929))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "❄",
                color = Color(0xFFB3E5FC),
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to Freezer",
                color = Color(0xFFB3E5FC),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Pause apps without uninstalling them. Notifications are hidden, but background activity may continue. Battery savings have not been measured.",
                color = Color(0xFF88B0C4),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            OnboardingStep(
                number = "1",
                title = "Install Shizuku",
                description = "Freezer uses Shizuku — a free, open-source helper app — to safely manage other apps."
            )
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingStep(
                number = "2",
                title = "Start Shizuku once per reboot",
                description = "Open Shizuku and tap \"Start via Wireless debugging.\" Takes 30 seconds."
            )
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingStep(
                number = "3",
                title = "Freeze apps",
                description = "Tap +, pick an app, and watch it freeze. Tap a frozen app to bring it back."
            )

            Spacer(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E4D6B))
                    .clickable { onDismiss() }
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Get started",
                    color = Color(0xFFB3E5FC),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OnboardingStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E4D6B)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color(0xFFB3E5FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.fillMaxWidth(0.04f))
        Column {
            Text(
                text = title,
                color = Color(0xFFB3E5FC),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color(0xFF88B0C4),
                fontSize = 12.sp
            )
        }
    }
}
@Composable
fun ShizukuStatusBanner(status: FreezerViewModel.ShizukuStatus) {
    val context = LocalContext.current

    val (text, bgColor, textColor, action) = when (status) {
        FreezerViewModel.ShizukuStatus.READY -> return
        FreezerViewModel.ShizukuStatus.UNKNOWN -> BannerConfig(
            "Checking Shizuku…",
            Color(0xFF13293D),
            Color(0xFF88B0C4),
            null
        )
        FreezerViewModel.ShizukuStatus.NOT_RUNNING -> BannerConfig(
            "Shizuku not running. Tap to open Shizuku and start it.",
            Color(0xFF4D1E1E),
            Color(0xFFFFB3B3),
            BannerAction.OpenShizuku
        )
        FreezerViewModel.ShizukuStatus.NEEDS_PERMISSION -> BannerConfig(
            "Tap + and grant permission to continue.",
            Color(0xFF4D3D1E),
            Color(0xFFFFD79A),
            null
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .let { mod ->
                if (action == BannerAction.OpenShizuku) {
                    mod.clickable { openShizukuApp(context) }
                } else mod
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class BannerConfig(
    val text: String,
    val bgColor: Color,
    val textColor: Color,
    val action: BannerAction?
)

private enum class BannerAction {
    OpenShizuku
}

private fun openShizukuApp(context: android.content.Context) {
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (launchIntent != null) {
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}

@Composable
fun FrozenAppSlot(
    app: InstalledApp,
    isShattering: Boolean,
    onShatterEnd: () -> Unit,
    onClick: () -> Unit
) {
    var hasAppeared by remember(app.packageName) { mutableStateOf(false) }

    LaunchedEffect(app.packageName) {
        kotlinx.coroutines.delay(16)
        hasAppeared = true
    }

    val scaleIn by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else 0.4f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "freeze_scale"
    )
    val alphaIn by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 100, easing = LinearOutSlowInEasing),
        label = "freeze_alpha"
    )

    val shatterProgress by animateFloatAsState(
        targetValue = if (isShattering) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing),
        label = "shatter_progress",
        finishedListener = { value ->
            if (value >= 1f) onShatterEnd()
        }
    )

    val effectiveScale = scaleIn * (1f - shatterProgress * 0.3f)
    val effectiveAlpha = alphaIn * (1f - shatterProgress)

    Box(modifier = Modifier.aspectRatio(1f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = effectiveScale
                    scaleY = effectiveScale
                    alpha = effectiveAlpha
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E4D6B))
                .clickable(enabled = !isShattering, onClickLabel = "Unfreeze ${app.appName}") { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                val bitmap = remember(app.packageName) {
                    app.icon.toBitmap().asImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.appName,
                    color = Color(0xFFB3E5FC),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = effectiveAlpha; scaleX = effectiveScale; scaleY = effectiveScale }) {
            val r = CornerRadius(16.dp.toPx())
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .25f), Color(0xFF81D7F2).copy(alpha = .05f), Color(0xFFC5F6FF).copy(alpha = .24f))),
                cornerRadius = r
            )
            drawRoundRect(color = Color(0xFFBDEFFF).copy(alpha = .8f), cornerRadius = r,
                style = Stroke(width = 2.dp.toPx()))
            drawRoundRect(color = Color.White.copy(alpha = .30f),
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
            val frost = Path().apply {
                moveTo(size.width * .08f, size.height * .23f)
                lineTo(size.width * .13f, size.height * .10f)
                lineTo(size.width * .39f, size.height * .08f)
            }
            drawPath(frost, Color.White.copy(alpha = .8f), style = Stroke(width = 2.dp.toPx()))
            drawLine(Color.White.copy(alpha = .45f), Offset(size.width * .68f, size.height * .90f),
                Offset(size.width * .89f, size.height * .83f), strokeWidth = 1.5.dp.toPx())
        }
        if (isShattering) {
            ShatterOverlay(progress = shatterProgress)
        }
    }
}

@Composable
fun ShatterOverlay(progress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val fade = (1f - progress).coerceIn(0f, 1f)
        val distance = size.minDimension * progress
        // Soft expanding clouds give the shards a frosty burst.
        for (i in 0 until 7) {
            val angle = i * (Math.PI * 2 / 7)
            val cloudCenter = center + Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()) * (distance * .58f)
            val radius = size.minDimension * (.12f + progress * .32f)
            drawCircle(brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE5FBFF).copy(alpha = fade * .35f), Color(0xFFB3ECFF).copy(alpha = 0f)),
                center = cloudCenter, radius = radius
            ), radius = radius, center = cloudCenter)
        }
        for (i in 0 until 12) {
            val angle = i * (Math.PI * 2 / 12) + if (i % 2 == 0) .12 else -.08
            val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
            val point = center + direction * (distance * if (i % 2 == 0) 1.1f else .75f)
            val shard = size.minDimension * (if (i % 2 == 0) .14f else .09f) * (1f - progress * .35f)
            val path = Path().apply {
                moveTo(point.x + direction.x * shard, point.y + direction.y * shard)
                lineTo(point.x - direction.y * shard * .55f, point.y + direction.x * shard * .55f)
                lineTo(point.x - direction.x * shard * .6f + direction.y * shard * .3f, point.y - direction.y * shard * .6f - direction.x * shard * .3f)
                close()
            }
            drawPath(path, Color(0xFFBEF4FF).copy(alpha = fade * .9f))
            drawPath(path, Color.White.copy(alpha = fade), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@Composable
fun AddButtonSlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13293D))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        DashedBorder()
        Text(
            text = "+",
            color = Color(0xFF4A90A4),
            fontSize = 40.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
fun DashedBorder() {
    val strokeColor = Color(0xFF2A4A5E)
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawDashedRoundRect(strokeColor)
    }
}

private fun DrawScope.drawDashedRoundRect(color: Color) {
    drawRoundRect(
        color = color,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )
    )
}

@Composable
fun UnfreezeDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF13293D))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Unfreeze ${app.appName}?",
                    color = Color(0xFFB3E5FC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This will make the app available again.",
                    color = Color(0xFF88B0C4),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF88B0C4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.fillMaxWidth(0.05f))
                    Text(
                        text = "Unfreeze",
                        color = Color(0xFFB3E5FC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onConfirm() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppPickerDialog(
    viewModel: FreezerViewModel,
    onDismiss: () -> Unit,
    onAppsSelected: (List<InstalledApp>) -> Unit
) {
    val installedApps by viewModel.installedApps.collectAsState()
    val frozenApps by viewModel.selectedApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFreezing by viewModel.isFreezing.collectAsState()
    val failedIds by viewModel.failedAppIds.collectAsState()
    val fallbackApp by viewModel.fallbackApp.collectAsState()
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var search by remember { mutableStateOf("") }
    val remaining = (16 - frozenApps.size).coerceAtLeast(0)
    val frozenIds = frozenApps.map { it.packageName }.toSet()
    LaunchedEffect(frozenIds) { chosen = chosen - frozenIds }

    LaunchedEffect(Unit) {
        viewModel.clearFailedApps()
        viewModel.loadInstalledApps()
    }

    Dialog(onDismissRequest = { if (!isFreezing) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp)).background(Color(0xFF13293D)).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose apps to freeze", color = Color(0xFFB3E5FC), fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, enabled = !isFreezing,
                    modifier = Modifier.semantics { contentDescription = "Close app picker" }) {
                    Text("×", color = Color(0xFFB3E5FC), fontSize = 30.sp)
                }
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search apps") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFFB3E5FC), unfocusedLabelColor = Color(0xFF88B0C4),
                    focusedBorderColor = Color(0xFFB3E5FC), unfocusedBorderColor = Color(0xFF4A90A4),
                    cursorColor = Color(0xFFB3E5FC)
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            Text("${chosen.size} selected · $remaining slots available", color = Color(0xFFB3E5FC), fontSize = 13.sp)
            if (failedIds.isNotEmpty() && !isFreezing && fallbackApp == null) {
                Text("${failedIds.size} could not be frozen. They remain selected so you can retry.",
                    color = Color(0xFFFFC9A6), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFB3E5FC))
                }
            } else {
                val matches = installedApps.filter { it.packageName !in frozenIds && (it.appName.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)) }
                if (matches.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = Color(0xFF88B0C4))
                    }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(1), modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(matches, key = { it.packageName }) { app ->
                        val checked = app.packageName in chosen
                        val enabled = !isFreezing && (checked || chosen.size < remaining)
                        Row(
                            modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox) {
                                chosen = if (it) chosen + app.packageName else chosen - app.packageName
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
                            AppListRow(app = app)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = chosen.isNotEmpty() && !isFreezing && fallbackApp == null,
                onClick = { onAppsSelected(installedApps.filter { it.packageName in chosen && it.packageName !in frozenIds }) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF84D8F5), contentColor = Color(0xFF082238), disabledContainerColor = Color(0xFF294758), disabledContentColor = Color(0xFF90AAB8)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (isFreezing) "Freezing…" else if (chosen.isEmpty()) "Select apps to freeze" else if (failedIds.isNotEmpty() && chosen == failedIds) "Retry ${chosen.size} selected" else "Freeze ${chosen.size} selected", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun AppListRow(app: InstalledApp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A1929))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(app.packageName) {
                app.icon.toBitmap().asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .height(40.dp)
                    .aspectRatio(1f)
            )
            Spacer(modifier = Modifier.fillMaxWidth(0.05f))
            Column {
                Text(
                    text = app.appName,
                    color = Color(0xFFB3E5FC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = Color(0xFF9CC8DA),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}