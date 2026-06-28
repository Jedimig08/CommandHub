package com.example.uitest.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.uitest.data.ModuleConfig
import com.example.uitest.util.moveModule
import com.example.uitest.viewmodel.DashboardViewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.Image
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPage(
    modules: SnapshotStateList<ModuleConfig>,
    viewModel: DashboardViewModel = viewModel(),
) {
    var selectedModule: ModuleConfig? by remember { mutableStateOf(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importLayout(it)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLayout()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(viewModel.columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.run { spacedBy(8.dp) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        item(span = { GridItemSpan(viewModel.columns) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        modules.add(ModuleConfig(
                            id = modules.size,
                            type = "LOG",
                            spanX = 1,
                            aspRatio = 1f,
                            color = Color.Gray
                        ))
                    }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Module")
                }
                
                Button(onClick = {
                    if (modules.isNotEmpty()) modules.removeAt(modules.lastIndex)
                }, modifier = Modifier.weight(1f)) {
                    Text("Remove Last")
                }
            }
        }

        items(
            items = modules,
            key = { it.id },
            span = { module ->
                GridItemSpan(module.spanX)
            }
        ) { module ->

            ModuleView(
                module = module,
                viewModel = viewModel,
            ) { 
                selectedModule = it 
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
                    val cameras = remember { viewModel.cameraManager.getCameraInfos() }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Camera:", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        cameras.forEach { camera ->
                            Button(
                                onClick = { editedType = "CAMERA:${camera.id}" },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text("${camera.facing} ${camera.type}", fontSize = 10.sp)
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

                                if (targetIndex != null && targetIndex in 0..modules.lastIndex) {
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
                val cameraId = module.type.split(":").getOrNull(1) ?: "0"
                val frame by viewModel.cameraManager.getFlow(cameraId).collectAsState(null)
                
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
            
            module.type.startsWith("SENSOR") -> {
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
