package com.example.varmap

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.varmap.data.PointEntity
import com.example.varmap.ui.MapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(vm: MapViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.setLocationGranted(granted)
        mapView?.let { enableMyLocation(it, granted) }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }


    LaunchedEffect(mapView) {
        if (mapView == null) return@LaunchedEffect
        var lastLat = ui.centerLat
        var lastLon = ui.centerLon
        while (true) {
            try {
                delay(160)
                val map = mapView ?: continue
                val center = map.mapCenter as GeoPoint
                val lat = center.latitude
                val lon = center.longitude
                if (lat != lastLat || lon != lastLon) {
                    vm.setCenter(lat, lon)

                    if (ui.selectedPoint != null && !ui.isProgrammaticMove) {
                        vm.selectPoint(null)
                    }

                    lastLat = lat
                    lastLon = lon
                }
            } catch (_: Exception) { break }
        }
    }

    // --- Вычисление видимых точек ---
    val visiblePoints by remember(ui.points, mapView) {
        derivedStateOf {
            val map = mapView ?: return@derivedStateOf emptyList<PointEntity>()
            val boundingBox = map.boundingBox ?: return@derivedStateOf emptyList<PointEntity>()
            ui.points.filter { point ->
                boundingBox.contains(GeoPoint(point.latitude, point.longitude))
            }
        }
    }

    val drawerWidth = 320.dp
    val offset = animateDpAsState(
        targetValue = if (ui.showChatDrawer) 0.dp else drawerWidth,
        label = "drawerOffset"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        val initialPoint = GeoPoint(ui.centerLat, ui.centerLon)
                        controller.setCenter(initialPoint)

                        setOnLongClickListener {
                            vm.showAddPointDialog(ui.centerLat, ui.centerLon)
                            true
                        }

                        if (ui.locationGranted) {
                            enableMyLocation(this, true)
                        }
                    }.also { mapView = it }
                },
                update = { map ->
                    map.overlays.removeAll { it is Marker && it.title != "Москва" }
                    ui.points.forEach { addMarker(map, it) }
                }
            )
            if (ui.isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(Color(0xFF2196F3), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Text(
                        text = "Синхронизация...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            // --- Прицел ---
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Place, contentDescription = "Прицел", tint = Color.Red, modifier = Modifier.size(32.dp))
            }

            // Внутри Box, после блока с координатами центра
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 52.dp, start = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Видимые точки ---
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Text("Видимые точки:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val visiblePoints = getVisiblePoints(mapView ?: return@LazyColumn, ui.points)
                        items(visiblePoints.size) { index ->
                            val point = visiblePoints[index]
                            Text(
                                text = "${point.name} (${point.latitude.format(6)}, ${point.longitude.format(6)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
                                modifier = Modifier
                                    .clickable {
                                        val geoPoint = GeoPoint(point.latitude, point.longitude)
                                        vm.selectPoint(point)
                                        vm.startProgrammaticMove()
                                        mapView?.controller?.animateTo(geoPoint)
                                        coroutineScope.launch {
                                            delay(1500)
                                            vm.endProgrammaticMove()
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        if (visiblePoints.isEmpty()) {
                            item {
                                Text(
                                    text = "Нет точек в области",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }

                // --- Выбранная точка (справа) ---
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    if (ui.selectedPoint != null) {
                        // Показываем выбранную точку
                        val point = ui.selectedPoint!!
                        Text("Выбрана точка:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(point.name, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        Text("Широта: ${"%.6f".format(point.latitude)}", style = MaterialTheme.typography.bodySmall)
                        Text("Долгота: ${"%.6f".format(point.longitude)}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        // Показываем центр карты
                        Text("Центр карты:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("Широта: ${"%.6f".format(ui.centerLat)}", style = MaterialTheme.typography.bodySmall)
                        Text("Долгота: ${"%.6f".format(ui.centerLon)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Кнопка Zoom In
            IconButton(
                onClick = { mapView?.controller?.zoomIn() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 210.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .size(60.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Приблизить", tint = Color.Black)
            }

// Кнопка Zoom Out
            IconButton(
                onClick = { mapView?.controller?.zoomOut() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 130.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .size(60.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Отдалить", tint = Color.Black)
            }

// Кнопка "Моё местоположение"
            IconButton(
                onClick = {
                    val overlay = mapView?.overlays?.firstOrNull { it is MyLocationNewOverlay } as? MyLocationNewOverlay
                    val location = overlay?.myLocation ?: return@IconButton
                    mapView?.controller?.animateTo(location)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .size(60.dp)
            ) {
                Icon(Icons.Default.Place, contentDescription = "Моё местоположение", tint = Color.Black)
            }

            // --- Панель снизу ---
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .shadow(6.dp, RoundedCornerShape(50))
                    .background(Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 25.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Чат", tint = Color.Black,
                    modifier = Modifier.size(35.dp).clickable { vm.openChat(true) })

                Icon(Icons.Default.Refresh, contentDescription = "Синхронизировать", tint = Color.Black,
                    modifier = Modifier.size(35.dp).clickable {
                        coroutineScope.launch {
                            kotlin.runCatching {
                                com.example.varmap.network.NetworkModule.api.getPoints()
                                vm.viewModelScope.launch { vm.syncNow()}
                            }
                        }
                    })

                Icon(Icons.Default.AddLocationAlt, contentDescription = "Добавить точку", tint = Color.Black,
                    modifier = Modifier.size(35.dp).clickable {
                        vm.showAddPointDialog(ui.centerLat, ui.centerLon)
                    })
            }
        }

        // --- Чат ---
        if (ui.showChatDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.6f)
                    .background(Color.Black)
                    .clickable(onClick = { vm.openChat(false) })
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .align(Alignment.CenterEnd)
                    .offset(x = offset.value)
                    .background(Color.White)
                    .border(1.dp, Color.LightGray)
                    .graphicsLayer { clip = true }
            ) {
                ChatDrawerContent(
                    messages = ui.messages,
                    inputMessage = ui.inputMessage,
                    onInputChanged = vm::setInputMessage,
                    onSendMessage = vm::sendMessage,
                    onClose = { vm.openChat(false) }
                )
            }
        }

        // --- Диалог добавления точки ---
        ui.showAddPointDialog?.let { (lat, lon) ->
            AddPointDialog(
                lat = lat,
                lon = lon,
                onDismiss = { vm.hideAddPointDialog() },
                onSave = { name -> vm.addPoint(name) }
            )
        }
    }
}
fun getVisiblePoints(mapView: MapView, allPoints: List<PointEntity>): List<PointEntity> {
    val boundingBox = mapView.boundingBox ?: return emptyList()
    return allPoints.filter { point ->
        boundingBox.contains(GeoPoint(point.latitude, point.longitude))
    }
}
private fun Double.format(digits: Int) = "%.${digits}f".format(this)