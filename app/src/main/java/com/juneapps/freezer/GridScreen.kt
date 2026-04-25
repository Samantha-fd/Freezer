package com.juneapps.freezer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

private sealed class GridItem {
    data class App(val app: InstalledApp) : GridItem()
    data object AddButton : GridItem()
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(viewModel: FreezerViewModel = viewModel()) {
    val selectedApps by viewModel.selectedApps.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()

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
            onAppSelected = { app ->
                viewModel.selectApp(app)
                showPicker = false
            }
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
}

@Composable
fun ShizukuStatusBanner(status: FreezerViewModel.ShizukuStatus) {
    val (text, bgColor, textColor) = when (status) {
        FreezerViewModel.ShizukuStatus.READY -> return
        FreezerViewModel.ShizukuStatus.UNKNOWN -> Triple(
            "Checking Shizuku…",
            Color(0xFF13293D),
            Color(0xFF88B0C4)
        )
        FreezerViewModel.ShizukuStatus.NOT_RUNNING -> Triple(
            "Shizuku not running. Open Shizuku and start it.",
            Color(0xFF4D1E1E),
            Color(0xFFFFB3B3)
        )
        FreezerViewModel.ShizukuStatus.NEEDS_PERMISSION -> Triple(
            "Tap + and grant permission to continue.",
            Color(0xFF4D3D1E),
            Color(0xFFFFD79A)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
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

@Composable
fun FrozenAppSlot(app: InstalledApp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E4D6B))
            .clickable { onClick() },
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
                contentDescription = app.appName,
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
                    text = "This will re-enable the app.",
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
    onAppSelected: (InstalledApp) -> Unit
) {
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF13293D))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose an app to freeze",
                    color = Color(0xFFB3E5FC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFB3E5FC))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(installedApps, key = { it.packageName }) { app ->
                            AppListRow(app = app, onClick = { onAppSelected(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppListRow(app: InstalledApp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A1929))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bitmap = remember(app.packageName) {
                app.icon.toBitmap().asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = app.appName,
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
                    color = Color(0xFF4A90A4),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}