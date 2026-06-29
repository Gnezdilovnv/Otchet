package com.example.otchet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.otchet.data.AppDatabase
import com.example.otchet.data.Record
import com.example.otchet.data.RecordDao
import com.example.otchet.ui.theme.OtchetTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDatabase
    private lateinit var recordDao: RecordDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "records_db"
        ).build()
        recordDao = db.recordDao()

        setContent {
            OtchetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(recordDao)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }
}

@Composable
fun App(recordDao: RecordDao) {
    val context = LocalContext.current
    val records by recordDao.getAllRecords().collectAsState(initial = emptyList())

    var direction by remember { mutableStateOf("") }
    var point by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("FPV") }
    var freqVideo by remember { mutableStateOf("") }
    var freqControl by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var time by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var suppressed by remember { mutableStateOf(false) }

    // Сохранение настроек направления и точки
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        direction = prefs.getString("direction", "") ?: ""
        point = prefs.getString("point", "") ?: ""
    }

    fun saveSettings() {
        prefs.edit().putString("direction", direction).putString("point", point).apply()
    }

    fun saveRecord() {
        if (direction.isBlank() || point.isBlank()) {
            Toast.makeText(context, "Направление и точка должны быть заполнены", Toast.LENGTH_SHORT).show()
            return
        }
        if (freqVideo.isBlank() || freqControl.isBlank()) {
            Toast.makeText(context, "Заполните частоты", Toast.LENGTH_SHORT).show()
            return
        }
        val record = Record(
            date = date,
            time = time,
            direction = direction,
            point = point,
            type = type,
            freqVideo = freqVideo,
            freqControl = freqControl,
            suppressed = if (suppressed) "ДА" else "НЕТ",
            exported = false
        )
        recordDao.insert(record)
        Toast.makeText(context, "Запись сохранена", Toast.LENGTH_SHORT).show()
        freqVideo = ""
        freqControl = ""
        suppressed = false
        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    fun generateReport() {
        val unexported = records.filter { !it.exported }
        if (unexported.isEmpty()) {
            Toast.makeText(context, "Нет новых данных для отчёта", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Данные")
            val headerRow = sheet.createRow(0)
            val headers = listOf("Дата", "Время", "Направление", "Точка", "Тип",
                "Частота видео", "Частота управления", "Подавлен")
            headers.forEachIndexed { idx, h -> headerRow.createCell(idx).setCellValue(h) }
            unexported.forEachIndexed { idx, rec ->
                val row = sheet.createRow(idx + 1)
                row.createCell(0).setCellValue(rec.date)
                row.createCell(1).setCellValue(rec.time)
                row.createCell(2).setCellValue(rec.direction)
                row.createCell(3).setCellValue(rec.point)
                row.createCell(4).setCellValue(rec.type)
                row.createCell(5).setCellValue(rec.freqVideo)
                row.createCell(6).setCellValue(rec.freqControl)
                row.createCell(7).setCellValue(rec.suppressed)
            }
            for (i in 0 until headers.size) {
                sheet.autoSizeColumn(i)
            }
            val filename = "отчёт_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
            val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, filename)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()

            // Отмечаем записи как экспортированные
            recordDao.markExported(unexported.map { it.id })

            Toast.makeText(context, "Отчёт сохранён: $filename", Toast.LENGTH_LONG).show()

            // Открываем Intent для отправки
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Поделиться отчётом"))

        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Мониторинг") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { saveSettings() }) {
                Text("Сохранить настройки")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Настройки
            OutlinedTextField(
                value = direction,
                onValueChange = { direction = it },
                label = { Text("Направление") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = point,
                onValueChange = { point = it },
                label = { Text("Точка") },
                modifier = Modifier.fillMaxWidth()
            )
            // Тип – выпадающий список
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    readOnly = true,
                    label = { Text("Тип") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listOf("FPV", "DJI", "Яга", "Крыло", "Радар", "Радар ударный", "Перехватчик")
                        .forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    type = item
                                    expanded = false
                                }
                            )
                        }
                }
            }
            OutlinedTextField(
                value = freqVideo,
                onValueChange = { freqVideo = it },
                label = { Text("Частота видео (МГц)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = freqControl,
                onValueChange = { freqControl = it },
                label = { Text("Частота управления (МГц)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Дата") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Время") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Подавлен")
                Switch(checked = suppressed, onCheckedChange = { suppressed = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveRecord() }, modifier = Modifier.weight(1f)) {
                    Text("Сохранить запись")
                }
                Button(onClick = { generateReport() }, modifier = Modifier.weight(1f)) {
                    Text("Сформировать отчёт")
                }
            }
            Divider()
            Text("Последние записи (последние 20)", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(records.takeLast(20).reversed()) { rec ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = "${rec.date} ${rec.time} | ${rec.type} | Вид: ${rec.freqVideo} МГц, Упр: ${rec.freqControl} МГц | ${if (rec.suppressed == "ДА") "⚠️ Подавлен" else "✅ Активен"} ${if (rec.exported) "📤" else "🔄"}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
