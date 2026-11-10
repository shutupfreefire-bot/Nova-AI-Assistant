package com.nova.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Ink = Color(0xFF0B0D12)
private val Panel = Color(0xFF151923)
private val Accent = Color(0xFF9B8CFF)
private data class ChatLine(val text: String, val user: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
 private var tts: TextToSpeech? = null
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tts = TextToSpeech(this, this); setContent { NovaApp(::listen, ::speak) } }
 override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
 private fun speak(text: String) { runOnUiThread { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova-reply") } }
 private fun listen(onText: (String) -> Unit, onError: (String) -> Unit) {
  if (!SpeechRecognizer.isRecognitionAvailable(this)) { onError("Speech recognition isn't available on this phone."); return }
  val local = android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
  val recognizer = if (local) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else SpeechRecognizer.createSpeechRecognizer(this)
  val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
   putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
  }
  recognizer.setRecognitionListener(object : RecognitionListener {
   override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText); recognizer.destroy() }
   override fun onError(error: Int) { onError("Speech recognition error ($error). Check your speech service."); recognizer.destroy() }
   override fun onReadyForSpeech(params: Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(rmsdB: Float) {}; override fun onBufferReceived(buffer: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onPartialResults(partialResults: Bundle?) {}; override fun onEvent(eventType: Int, params: Bundle?) {}
  })
  recognizer.startListening(intent)
 }
 override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaApp(listen: ((String)->Unit, (String)->Unit)->Unit, speak: (String)->Unit) {
 val context = androidx.compose.ui.platform.LocalContext.current
 val prefs = remember { context.getSharedPreferences("nova_settings", android.content.Context.MODE_PRIVATE) }
 var apiKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
 var baseUrl by remember { mutableStateOf(prefs.getString("url", "https://integrate.api.nvidia.com/v1") ?: "") }
 var model by remember { mutableStateOf(prefs.getString("model", "nvidia/nemotron-3.5-lightning-30b-a3b") ?: "") }
 var showSettings by remember { mutableStateOf(false) }
 var input by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("Ready") }
 val lines = remember { mutableStateListOf(ChatLine("Hi. I'm Nova. Ask me anything.", false)) }
 val scope = rememberCoroutineScope()
 val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) listen({ input = it; status = "Voice captured" }, { status = it }) else status = "Microphone permission denied" }
 MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Panel)) {
  Scaffold(containerColor = Ink, topBar = {
   TopAppBar(title = { Column { Text("NOVA", fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("PERSONAL AI ASSISTANT", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.5.sp) } },
    actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) } },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Color.White))
  }) { pad ->
   Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
     Box(Modifier.size(7.dp).background(Color(0xFF64DDAA), CircleShape)); Spacer(Modifier.width(8.dp)); Text(status, color = Color.Gray, fontSize = 12.sp)
     Spacer(Modifier.weight(1f)); Text("AI • " + model.substringAfterLast('/').take(22), color = Accent, fontSize = 11.sp)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     items(lines) { line ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.user) Arrangement.End else Arrangement.Start) {
       Surface(color = if (line.user) Color(0xFF29243F) else Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 310.dp)) { Text(line.text, Modifier.padding(14.dp), color = Color(0xFFF0F0F5), fontSize = 15.sp) }
      }
     }
     if (busy) item { Text("Nova is thinking…", color = Accent, fontSize = 13.sp) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
     IconButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen({ input = it; status = "Voice captured" }, { status = it }) else permission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.size(48.dp).background(Panel, CircleShape)) { Icon(Icons.Default.Mic, "Speak", tint = Accent) }
     Spacer(Modifier.width(8.dp))
     OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Nova…") }, maxLines = 4, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Accent, unfocusedBorderColor = Color.DarkGray))
     Spacer(Modifier.width(8.dp))
     IconButton(enabled = input.isNotBlank() && !busy, onClick = {
      val prompt = input.trim(); input = ""; lines.add(ChatLine(prompt, true)); busy = true; status = "Thinking…"
      scope.launch {
       try { val history = lines.takeLast(12).toList(); val answer = withContext(Dispatchers.IO) { callModel(baseUrl, apiKey, model, history) }; lines.add(ChatLine(answer, false)); speak(answer); status = "Ready" }
       catch (e: Exception) { lines.add(ChatLine("Couldn't reach AI provider: " + (e.message ?: "check settings and connection"), false)); status = "Connection issue" }
       busy = false
      }
     }, modifier = Modifier.size(48.dp).background(Accent, CircleShape)) { Icon(Icons.Default.Send, "Send", tint = Ink) }
    }
    Text("Android speech services • On-device recognition when supported", Modifier.padding(bottom = 10.dp), color = Color.Gray, fontSize = 10.sp)
   }
  }
  if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, containerColor = Panel,
   title = { Text("AI provider", color = Color.White) },
   text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("OpenAI-compatible API", color = Color.Gray, fontSize = 12.sp)
    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key") }, singleLine = true)
    Text("Key is stored in app-private preferences. Use a provider you trust.", color = Color.Gray, fontSize = 11.sp)
   } },
   confirmButton = { TextButton(onClick = { prefs.edit().putString("url", baseUrl.trim()).putString("model", model.trim()).putString("key", apiKey.trim()).apply(); showSettings = false }) { Text("Save") } },
   dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } })
 }
}

private fun callModel(base: String, key: String, model: String, history: List<ChatLine>): String {
 require(key.isNotBlank()) { "Add your API key in Settings." }
 val messages = JSONArray().put(JSONObject().put("role", "system").put("content", "You are Nova, a concise and helpful Android AI assistant."))
 history.forEach { messages.put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
 val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.7)
 val conn = (URL(base.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
  requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
  setRequestProperty("Content-Type", "application/json"); setRequestProperty("Authorization", "Bearer " + key.trim())
 }
 conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
 val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
 val raw = stream.bufferedReader().use { it.readText() }; conn.disconnect()
 if (code !in 200..299) error("HTTP $code: " + raw.take(180))
 return JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
}
