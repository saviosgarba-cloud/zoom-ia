package com.zoomia.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZoomIAApp()
                }
            }
        }
    }
}

data class ChatLine(val author: String, val text: String)

data class ToolItem(val title: String, val description: String)

@Composable
fun ZoomIAApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("zoom_ia_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var endpoint by remember {
        mutableStateOf(prefs.getString("endpoint", "") ?: "")
    }
    var model by remember {
        mutableStateOf(prefs.getString("model", "") ?: "")
    }
    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }
    var prompt by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Configure sua API para começar.") }

    val messages = remember { mutableStateListOf<ChatLine>() }

    val tools = listOf(
        ToolItem("Chat IA", "Funciona com uma API de chat compatível com OpenAI."),
        ToolItem("Gerar imagem", "Módulo preparado para conectar ao seu gerador de imagens."),
        ToolItem("Remover objeto", "Módulo preparado para inpainting/máscara."),
        ToolItem("Trocar roupa", "Módulo preparado para virtual try-on."),
        ToolItem("Editar vídeo", "Módulo preparado para geração e edição de vídeo.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Zoom IA", style = MaterialTheme.typography.headlineLarge)
        Text("Sua IA Android editável")
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Configuração da IA", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Endpoint /v1/chat/completions") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Modelo") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Chave da API") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            prefs.edit()
                                .putString("endpoint", endpoint.trim())
                                .putString("model", model.trim())
                                .putString("api_key", apiKey.trim())
                                .apply()
                            status = "Configuração salva neste aparelho."
                        }) {
                            Text("Salvar configuração")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Text("Ferramentas", style = MaterialTheme.typography.titleMedium)
            }

            items(tools) { tool ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(tool.description)
                    }
                }
            }

            if (messages.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Conversa", style = MaterialTheme.typography.titleMedium)
                }
                items(messages) { message ->
                    Text("${message.author}: ${message.text}")
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Digite um pedido") },
            enabled = !isSending
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending,
                onClick = {
                    val userPrompt = prompt.trim()
                    val currentEndpoint = endpoint.trim()
                    val currentModel = model.trim()
                    val currentKey = apiKey.trim()

                    if (userPrompt.isBlank()) return@Button
                    messages.add(ChatLine("Você", userPrompt))
                    prompt = ""

                    if (currentEndpoint.isBlank() || currentModel.isBlank()) {
                        messages.add(
                            ChatLine(
                                "Zoom IA",
                                "Preencha o endpoint e o modelo na configuração acima."
                            )
                        )
                        return@Button
                    }

                    isSending = true
                    scope.launch {
                        val answer = withContext(Dispatchers.IO) {
                            callChatApi(
                                endpoint = currentEndpoint,
                                model = currentModel,
                                apiKey = currentKey,
                                prompt = userPrompt
                            )
                        }
                        messages.add(ChatLine("Zoom IA", answer))
                        isSending = false
                    }
                }
            ) {
                Text(if (isSending) "Enviando..." else "Enviar")
            }
        }
    }
}

private fun callChatApi(
    endpoint: String,
    model: String,
    apiKey: String,
    prompt: String
): String {
    return try {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        val payload = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            )
        }

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (code !in 200..299) {
            return "Erro HTTP $code: ${body.take(500)}"
        }

        val root = JSONObject(body)
        val content = root
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()

        if (content.isNullOrBlank()) {
            "A API respondeu, mas não encontrei choices[0].message.content."
        } else {
            content
        }
    } catch (e: Exception) {
        "Falha ao conectar: ${e.message ?: e.javaClass.simpleName}"
    }
}
