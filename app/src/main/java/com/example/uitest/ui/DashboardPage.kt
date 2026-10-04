package com.example.uitest.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uitest.data.ModuleConfig
import com.example.uitest.util.moveModule
import com.example.uitest.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPage(
    modules: SnapshotStateList<ModuleConfig>,
    pageIndex: Int = 0,
    totalPages: Int = 1,
    viewModel: DashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    var selectedModule: ModuleConfig? by remember { mutableStateOf(null) }
    var showSettingsSheet by remember { mutableStateOf(value = false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            viewModel.importLayout(it)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(viewModel.columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.run { spacedBy(8.dp) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        item(span = { GridItemSpan(viewModel.columns) }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Page ${pageIndex + 1} / $totalPages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleModulesActive() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (viewModel.isModulesActive)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Text(
                                text = if (viewModel.isModulesActive) "⚡ Active" else "⏸ Paused",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Text("⚙", fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        items(
            items = modules,
            key = { it.id },
            span = { module ->
                GridItemSpan(module.spanX)
            },
        ) { module ->

            ModuleView(
                module = module,
                viewModel = viewModel,
            ) { 
                selectedModule = it 
            }

        }
    }

    if (showSettingsSheet) {
        val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            modifier = Modifier.imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Keep Screen Awake toggle & Domain Publication
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep Screen On",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Prevent phone from sleeping (like YouTube)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = viewModel.keepScreenOn,
                                onCheckedChange = { viewModel.setKeepScreenOnEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "High-Speed Sensors",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (viewModel.sensorDelayFastest) "FASTEST mode (Higher CPU/Battery)" else "UI mode (Power Saver)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = viewModel.sensorDelayFastest,
                                onCheckedChange = { viewModel.updateSensorDelayMode(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Domain Publication (mDNS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Re-broadcast 'command-hub' service on local network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.publishDomain()
                                Toast.makeText(context, "Domain publication sent", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Publish Domain")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Layout Management
                Text(
                    text = "Layout Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Current Page: ${pageIndex + 1} of $totalPages",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.addLayoutPreset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Layout")
                    }

                    Button(
                        onClick = { viewModel.removeLayoutPreset(pageIndex) },
                        enabled = totalPages > 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Remove Layout")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Grid Columns adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Grid Columns: ${viewModel.columns}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.updateColumns(viewModel.columns - 1) },
                            enabled = viewModel.columns > 1
                        ) {
                            Text("-")
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateColumns(viewModel.columns + 1) },
                            enabled = viewModel.columns < 8
                        ) {
                            Text("+")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveLayout() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Layout")
                    }

                    Button(
                        onClick = { launcher.launch("application/json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Layout")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Module Management
                Text(
                    text = "Modules on Current Page",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.addModuleToPreset(pageIndex) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Module")
                    }

                    Button(
                        onClick = { viewModel.removeLastModuleFromPreset(pageIndex) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Remove Last")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (selectedModule != null) {

        var editedType by remember(selectedModule) {
            mutableStateOf(selectedModule!!.type)
        }

        var editedSpanX by remember(selectedModule) {
            mutableStateOf(selectedModule!!.spanX.toString())
        }

        var editedAspRatio by remember(selectedModule) {
            mutableStateOf(selectedModule!!.aspRatio.toString())
        }

        var moveToIndex by remember { mutableStateOf("") }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { selectedModule = null },
            sheetState = sheetState,
            modifier = Modifier.imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text("Edit Module", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = editedSpanX,
                        onValueChange = { editedSpanX = it },
                        label = { Text("Span") },
                        modifier = Modifier.weight(1f)
                    )

                    TextField(
                        value = editedAspRatio,
                        onValueChange = { editedAspRatio = it },
                        label = { Text("Ratio") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = moveToIndex,
                    onValueChange = { moveToIndex = it },
                    label = { Text("Move to index") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = editedType,
                    onValueChange = { editedType = it },
                    label = { Text("Type (LOG, CAMERA:id, SENSOR:id)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (editedType.startsWith("SENSOR")) {
                    val sensors = remember { viewModel.sensorManager.getAvailableSensors() }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Sensor:", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        sensors.forEach { sensor ->
                            Button(
                                onClick = { editedType = "SENSOR:${sensor.type}" },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(sensor.name, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (editedType.startsWith("CAMERA")) {
                    val parts = editedType.split(":")
                    val cameraId = parts.getOrNull(1) ?: "0"
                    val cameras = remember { viewModel.cameraManager.getCameraInfos() }
                    val selectedCamera = cameras.find { it.id == cameraId }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Camera:", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        cameras.forEach { camera ->
                            Button(
                                onClick = { 
                                    val resPart = parts.getOrNull(2) ?: "640x480"
                                    editedType = "CAMERA:${camera.id}:$resPart" 
                                },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text("${camera.facing} ${camera.type}", fontSize = 10.sp)
                            }
                        }
                    }

                    if ((selectedCamera != null) && selectedCamera.supportedResolutions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Resolution:", style = MaterialTheme.typography.labelSmall)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            selectedCamera.supportedResolutions.forEach { res ->
                                Button(
                                    onClick = { editedType = "CAMERA:$cameraId:${res.width}x${res.height}" },
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Text("${res.width}x${res.height}", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val currentIndex =
                                modules.indexOfFirst { it.id == selectedModule?.id }

                            if (currentIndex != -1) {

                                val newSpan =
                                    editedSpanX.toIntOrNull() ?: modules[currentIndex].spanX
                                val newRatio =
                                    editedAspRatio.toFloatOrNull() ?: modules[currentIndex].aspRatio

                                // Update module FIRST
                                modules[currentIndex] =
                                    modules[currentIndex].copy(
                                        type = editedType,
                                        spanX = newSpan,
                                        aspRatio = newRatio
                                    )

                                val targetIndex = moveToIndex.toIntOrNull()

                                if ((targetIndex != null) && (targetIndex in modules.indices)) {
                                    moveModule(modules, currentIndex, targetIndex)
                                }
                            }

                            moveToIndex = ""
                            selectedModule = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }

                    Button(
                        onClick = {
                            viewModel.saveLayout()
                        },
                        modifier = Modifier.weight(1f)
                    ){
                        Text("Save")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        launcher.launch("application/json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ){
                    Text("Load Layout")
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}


@Composable
fun ModuleView(
    module: ModuleConfig,
    viewModel: DashboardViewModel,
    onEditRequest: (ModuleConfig) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(module.aspRatio)
            .background(module.color, MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { },
                onLongClick = { onEditRequest(module) }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            module.type.startsWith("CAMERA") -> {
                if (!viewModel.isModulesActive) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Camera Paused",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.toggleModulesActive() }
                        ) {
                            Text("Activate", fontSize = 10.sp, color = Color.White)
                        }
                    }
                } else {
                    val parts = module.type.split(":")
                    val cameraId = parts.getOrNull(1) ?: "0"
                    val res = parts.getOrNull(2)?.split("x")
                    val width = res?.getOrNull(0)?.toIntOrNull() ?: 640
                    val height = res?.getOrNull(1)?.toIntOrNull() ?: 480
                    
                    val frame by viewModel.cameraManager.getFlow(cameraId, width, height).collectAsState(null)
                    
                    frame?.let { bytes ->
                        val bitmap = remember(bytes) { 
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) 
                        }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Camera $cameraId",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } ?: Text("Camera $cameraId Loading...", color = Color.Gray, fontSize = 12.sp)
                }
            }
            
            module.type.startsWith("SENSOR") -> {
                if (!viewModel.isModulesActive) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Sensor Paused",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.toggleModulesActive() }
                        ) {
                            Text("Activate", fontSize = 10.sp, color = Color.White)
                        }
                    }
                } else {
                    val sensorType = module.type.split(":").getOrNull(1)?.toIntOrNull() ?: 1
                    val data by viewModel.sensorManager.getSensorFlow(sensorType).collectAsState(null)
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sensor $sensorType",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = data?.values?.joinToString("\n") { "%.2f".format(it) } ?: "Waiting...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            else -> {
                // Default fallback or "LOG" type
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = module.type,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = module.data?.data ?: "No Data",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
