package com.identifiant.ordoread

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.identifiant.ordoread.domain.ai.Medication
import com.identifiant.ordoread.presentation.MainViewModel
import com.identifiant.ordoread.presentation.UiState
import com.identifiant.ordoread.presentation.ocr.CameraPreviewScreen
import com.identifiant.ordoread.ui.theme.OrdoReadTheme
import com.identifiant.ordoread.utils.getSavedPrescriptions
import com.identifiant.ordoread.utils.saveImageToInternalStorage
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.TimeZone

enum class Screen { SCANNER, HISTORY }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            initUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            initUi()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun initUi() {
        setContent {
            OrdoReadTheme {
                var currentScreen by remember { mutableStateOf(Screen.SCANNER) }
                val context = LocalContext.current

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        currentScreen = Screen.SCANNER
                        try {
                            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val source = ImageDecoder.createSource(context.contentResolver, it)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                            }
                            viewModel.previewImage(bitmap.copy(Bitmap.Config.ARGB_8888, true))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erreur de lecture de l'image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                label = { Text("Scanner") },
                                selected = currentScreen == Screen.SCANNER,
                                onClick = { currentScreen = Screen.SCANNER }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                label = { Text("Importer") },
                                selected = false,
                                onClick = { launcher.launch("image/*") }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                label = { Text("Historique") },
                                selected = currentScreen == Screen.HISTORY,
                                onClick = { currentScreen = Screen.HISTORY }
                            )
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding()),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            Screen.SCANNER -> MainScreen(viewModel)
                            Screen.HISTORY -> HistoryScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreviewScreen(onTextExtracted = { bitmap ->
                        viewModel.previewImage(bitmap)
                    })
                }
            }
            is UiState.Preview -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Vérifiez que tous les médicaments sont visibles",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = "Prévisualisation de l'ordonnance",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.confirmAndProcess(state.bitmap) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Analyser cette photo")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reprendre la photo")
                    }
                }
            }
            is UiState.Processing -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is UiState.Success -> {
                LaunchedEffect(state.bitmap) {
                    saveImageToInternalStorage(context, state.bitmap)
                }

                var editableMedications by remember { mutableStateOf(state.result) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Vérifiez et modifiez les informations", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    editableMedications.forEachIndexed { index, med ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = med.description,
                                    onValueChange = { newDesc ->
                                        val newList = editableMedications.toMutableList()
                                        newList[index] = med.copy(description = newDesc)
                                        editableMedications = newList
                                    },
                                    label = { Text("Nom du médicament / posologie") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = med.durationDays.toString(),
                                    onValueChange = { newDuration ->
                                        val newList = editableMedications.toMutableList()
                                        newList[index] = med.copy(durationDays = newDuration.toIntOrNull() ?: 1)
                                        editableMedications = newList
                                    },
                                    label = { Text("Durée (jours)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Toggle prise libre
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Prise si besoin", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = med.isPriseLibre,
                                        onCheckedChange = { isLibre ->
                                            val newList = editableMedications.toMutableList()
                                            newList[index] = med.copy(
                                                isPriseLibre = isLibre,
                                                addToCalendar = if (isLibre) false else true
                                            )
                                            editableMedications = newList
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }

                                if (med.isPriseLibre) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Toggle ajouter au calendrier (vert/rouge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Ajouter au calendrier", style = MaterialTheme.typography.bodyMedium)
                                            if (med.addToCalendar) {
                                                Text(
                                                    "Rappels activés",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Text(
                                                    if (med.intervalHours > 0)
                                                        "Toutes les ${med.intervalHours}h détecté — activer pour planifier"
                                                    else
                                                        "Pas de rappels",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = med.addToCalendar,
                                            onCheckedChange = { addToCal ->
                                                val updated = if (addToCal) {
                                                    if (med.intervalHours > 0) {
                                                        val generated = generateHoursFromInterval(8, med.intervalHours)
                                                        med.copy(addToCalendar = true, hours = generated)
                                                    } else {
                                                        med.copy(addToCalendar = true)
                                                    }
                                                } else {
                                                    med.copy(addToCalendar = false, hours = emptyList())
                                                }
                                                val newList = editableMedications.toMutableList()
                                                newList[index] = updated
                                                editableMedications = newList
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.inverseOnSurface,
                                                checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.inverseOnSurface,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }

                                    if (med.addToCalendar) {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Intervalle entre prises
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = if (med.intervalHours > 0) med.intervalHours.toString() else "",
                                                onValueChange = { newVal ->
                                                    val interval = newVal.toIntOrNull()?.coerceIn(1, 24) ?: 0
                                                    val generated = if (interval > 0) generateHoursFromInterval(8, interval) else emptyList()
                                                    val newList = editableMedications.toMutableList()
                                                    newList[index] = med.copy(intervalHours = interval, hours = generated)
                                                    editableMedications = newList
                                                },
                                                label = { Text("Intervalle (heures)") },
                                                placeholder = { Text("Ex: 6") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                isError = med.intervalHours == 0
                                            )
                                            if (med.intervalHours > 0) {
                                                Text(
                                                    "Toutes les ${med.intervalHours}h",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            } else {
                                                Text(
                                                    "Renseignez l'intervalle prescrit",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        if (med.hours.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "${med.hours.size} prise(s)/jour : ${med.hours.joinToString(", ") { "${it}h" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (!med.isPriseLibre) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Heures de prise :", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    val timeSlots = listOf(
                                        "Matin" to 8,
                                        "Midi" to 12,
                                        "Après-midi" to 16,
                                        "Soir" to 20,
                                        "Coucher" to 22
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        timeSlots.forEach { (label, hour) ->
                                            val selected = hour in med.hours
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    val newHours = if (selected) {
                                                        med.hours - hour
                                                    } else {
                                                        (med.hours + hour).distinct().sorted()
                                                    }
                                                    val newList = editableMedications.toMutableList()
                                                    newList[index] = med.copy(hours = newHours)
                                                    editableMedications = newList
                                                },
                                                label = { Text("$label (${hour}h)") }
                                            )
                                        }
                                    }

                                    // Horaires personnalisés (ceux qui ne correspondent pas aux créneaux prédéfinis)
                                    val presetHours = timeSlots.map { it.second }.toSet()
                                    val customHours = med.hours.filter { it !in presetHours }

                                    if (customHours.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Personnalisés :", style = MaterialTheme.typography.labelSmall)
                                            customHours.forEach { hour ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = {
                                                        val newHours = med.hours - hour
                                                        val newList = editableMedications.toMutableList()
                                                        newList[index] = med.copy(hours = newHours)
                                                        editableMedications = newList
                                                    },
                                                    label = { Text("${hour}h") },
                                                    trailingIcon = { Icon(Icons.Default.Delete, contentDescription = "Supprimer", modifier = Modifier.size(16.dp)) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Bouton ajouter un horaire personnalisé
                                    var showCustomHourField by remember { mutableStateOf(false) }
                                    var customHourText by remember { mutableStateOf("") }

                                    if (showCustomHourField) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = customHourText,
                                                onValueChange = { customHourText = it.filter { c -> c.isDigit() } },
                                                label = { Text("Heure (0-23)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(120.dp),
                                                singleLine = true
                                            )
                                            FilledTonalButton(
                                                onClick = {
                                                    val h = customHourText.toIntOrNull()
                                                    if (h != null && h in 0..23 && h !in med.hours) {
                                                        val newHours = (med.hours + h).sorted()
                                                        val newList = editableMedications.toMutableList()
                                                        newList[index] = med.copy(hours = newHours)
                                                        editableMedications = newList
                                                    }
                                                    customHourText = ""
                                                    showCustomHourField = false
                                                }
                                            ) { Text("OK") }
                                            TextButton(onClick = {
                                                customHourText = ""
                                                showCustomHourField = false
                                            }) { Text("Annuler") }
                                        }
                                    } else {
                                        TextButton(onClick = { showCustomHourField = true }) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Ajouter un horaire personnalisé")
                                        }
                                    }

                                    if (med.hours.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${med.hours.size} prise(s)/jour : ${med.hours.joinToString(", ") { "${it}h" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        val newList = editableMedications.toMutableList()
                                        newList.removeAt(index)
                                        editableMedications = newList
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Supprimer ce médicament", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var showStartDialog by remember { mutableStateOf(false) }

                    if (showStartDialog) {
                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        AlertDialog(
                            onDismissRequest = { showStartDialog = false },
                            title = { Text("Début du traitement") },
                            text = {
                                Column {
                                    Text("Quand souhaitez-vous commencer les rappels ?")
                                    if (currentHour >= 20) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Il est ${currentHour}h — certaines prises d'aujourd'hui sont déjà passées.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showStartDialog = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                        insertMedicationsIntoCalendar(context, editableMedications, startToday = true)
                                        Toast.makeText(context, "Rappels ajoutés à partir d'aujourd'hui", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Permissions calendrier manquantes", Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Aujourd'hui") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showStartDialog = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                        insertMedicationsIntoCalendar(context, editableMedications, startToday = false)
                                        Toast.makeText(context, "Rappels ajoutés à partir de demain", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Permissions calendrier manquantes", Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Demain") }
                            }
                        )
                    }

                    Button(
                        onClick = { showStartDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ajouter les rappels au calendrier")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ignorer et refaire une analyse")
                    }
                }
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Erreur: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("Réessayer")
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    var files by remember { mutableStateOf(getSavedPrescriptions(context)) }

    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune ordonnance sauvegardée.")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        ) {
            items(files, key = { it.absolutePath }) { file ->
                val bitmap = remember(file.absolutePath) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                }

                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(onClick = {
                                if (file.delete()) {
                                    files = getSavedPrescriptions(context)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Ordonnance sauvegardée",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun generateHoursFromInterval(startHour: Int = 8, intervalHours: Int): List<Int> {
    val hours = mutableListOf<Int>()
    var h = startHour
    while (h < 24) {
        hours.add(h)
        h += intervalHours
    }
    return hours
}

private fun insertMedicationsIntoCalendar(context: Context, medications: List<Medication>, startToday: Boolean = false) {
    val projection = arrayOf(CalendarContract.Calendars._ID)
    val cursor = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        "${CalendarContract.Calendars.VISIBLE} = 1",
        null,
        null
    )

    var calendarId: Long? = null
    if (cursor != null && cursor.moveToFirst()) {
        calendarId = cursor.getLong(0)
        cursor.close()
    }

    if (calendarId == null) return

    val now = Calendar.getInstance()
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)

    medications.filter { !it.isPriseLibre || it.addToCalendar }.forEach { med ->
        // Filtrer les heures déjà passées si on commence aujourd'hui
        val todayHours = if (startToday) {
            med.hours.filter { hour -> hour > currentHour || (hour == currentHour && currentMinute < 30) }
        } else {
            emptyList()
        }

        // Jours récurrents complets : aujourd'hui est partiel donc ne compte pas
        val recurringDays = med.durationDays

        // 1. Créer les événements pour aujourd'hui (si startToday et il reste des heures)
        if (startToday && todayHours.isNotEmpty()) {
            todayHours.forEach { hour ->
                val eventStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, eventStart.timeInMillis)
                    put(CalendarContract.Events.DTEND, eventStart.timeInMillis + 15 * 60 * 1000)
                    put(CalendarContract.Events.ALL_DAY, 0)
                    put(CalendarContract.Events.TITLE, "Prise: ${med.description.split(" ").firstOrNull() ?: "Médicament"}")
                    put(CalendarContract.Events.HAS_ALARM, 1)
                    put(CalendarContract.Events.DESCRIPTION, med.description)
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    // Pas de RRULE : événement unique pour aujourd'hui
                }

                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()?.let { eventId ->
                    val reminderValues = ContentValues().apply {
                        put(CalendarContract.Reminders.MINUTES, 0)
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                }
            }
        }

        // 2. Créer les événements récurrents pour les jours suivants
        if (recurringDays > 0) {
            med.hours.forEach { hour ->
                val eventStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, if (startToday) 1 else 1)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val values = ContentValues().apply {
                    if (med.isPriseLibre) {
                        put(CalendarContract.Events.DTSTART, eventStart.timeInMillis)
                        put(CalendarContract.Events.DTEND, eventStart.timeInMillis + 24 * 60 * 60 * 1000)
                        put(CalendarContract.Events.ALL_DAY, 1)
                        put(CalendarContract.Events.TITLE, "Prise libre: ${med.description.split(" ").firstOrNull() ?: "Médicament"}")
                        put(CalendarContract.Events.HAS_ALARM, 0)
                    } else {
                        put(CalendarContract.Events.DTSTART, eventStart.timeInMillis)
                        put(CalendarContract.Events.DTEND, eventStart.timeInMillis + 15 * 60 * 1000)
                        put(CalendarContract.Events.ALL_DAY, 0)
                        put(CalendarContract.Events.TITLE, "Prise: ${med.description.split(" ").firstOrNull() ?: "Médicament"}")
                        put(CalendarContract.Events.HAS_ALARM, 1)
                    }
                    put(CalendarContract.Events.DESCRIPTION, med.description)
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=$recurringDays")
                }

                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

                if (!med.isPriseLibre) {
                    uri?.lastPathSegment?.toLongOrNull()?.let { eventId ->
                        val reminderValues = ContentValues().apply {
                            put(CalendarContract.Reminders.MINUTES, 0)
                            put(CalendarContract.Reminders.EVENT_ID, eventId)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                    }
                }
            }
        }
    }
}