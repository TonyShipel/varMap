package com.example.varmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.varmap.data.PointEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun AddPointDialog(lat: Double, lon: Double, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить точку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Широта: ${"%.6f".format(lat)}")
                Text("Долгота: ${"%.6f".format(lon)}")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Ориентир 1") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ChatDrawerContent(
    messages: List<String>,
    inputMessage: String,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Чат", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                ChatBubble(message = message, isOwn = index % 2 == 1)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            TextField(
                value = inputMessage,
                onValueChange = onInputChanged,
                placeholder = { Text("Написать сообщение...") },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (inputMessage.isNotBlank()) onSendMessage() },
                modifier = Modifier.size(48.dp).background(Color.Blue, CircleShape)
            ) { Icon(Icons.Default.Send, contentDescription = "Отправить", tint = Color.White) }
        }
    }
}

@Composable
fun ChatBubble(message: String, isOwn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isOwn) Color.Blue else Color.LightGray,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = message,
                color = if (isOwn) Color.White else Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun enableMyLocation(map: MapView, granted: Boolean) {
    val existing = map.overlays.firstOrNull { it is MyLocationNewOverlay } as? MyLocationNewOverlay
    if (existing != null) {
        map.overlays.remove(existing)
    }

    if (granted) {
        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(map.context), map).apply {
            enableMyLocation()
        }
        map.overlays.add(overlay)
        map.invalidate()
    }
}

fun addMarker(mapView: MapView, point: PointEntity) {
    val marker = Marker(mapView).apply {
        position = GeoPoint(point.latitude, point.longitude)
        title = point.name
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = ContextCompat.getDrawable(mapView.context, org.osmdroid.library.R.drawable.person)
    }
    mapView.overlays.add(marker)
}
