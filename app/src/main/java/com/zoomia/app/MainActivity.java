package com.zoomia.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText endpointInput;
    private EditText modelInput;
    private EditText keyInput;
    private EditText promptInput;
    private TextView conversation;
    private Button sendButton;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("zoom_ia_settings", Context.MODE_PRIVATE);
        setContentView(createUi());
    }

    private View createUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 48);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Zoom IA");
        title.setTextSize(30f);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sua IA Android editavel");
        subtitle.setTextSize(16f);
        root.addView(subtitle);

        addSpace(root, 20);
        addSection(root, "Configuracao da IA");

        endpointInput = addInput(root, "Endpoint completo, ex.: https://seu-servidor/v1/chat/completions");
        endpointInput.setText(prefs.getString("endpoint", ""));

        modelInput = addInput(root, "Modelo");
        modelInput.setText(prefs.getString("model", ""));

        keyInput = addInput(root, "Chave da API (opcional)");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(prefs.getString("api_key", ""));

        Button save = new Button(this);
        save.setText("Salvar configuracao");
        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString("endpoint", endpointInput.getText().toString().trim())
                    .putString("model", modelInput.getText().toString().trim())
                    .putString("api_key", keyInput.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "Configuracao salva", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        addSpace(root, 20);
        addSection(root, "Ferramentas");
        addTool(root, "Chat IA", "Conecta a uma API compativel com /v1/chat/completions.");
        addTool(root, "Gerar imagem", "Pronto para conectar a um gerador de imagens.");
        addTool(root, "Remover objeto", "Pronto para conectar a inpainting e mascara.");
        addTool(root, "Trocar/adicionar roupa", "Pronto para conectar a virtual try-on.");
        addTool(root, "Editar video", "Pronto para conectar a um modelo de video.");

        addSpace(root, 20);
        addSection(root, "Conversa");
        conversation = new TextView(this);
        conversation.setText("Configure sua API acima e envie uma mensagem.\n");
        conversation.setTextSize(16f);
        root.addView(conversation);

        promptInput = addInput(root, "Digite um pedido para sua IA");
        promptInput.setMinLines(2);

        sendButton = new Button(this);
        sendButton.setText("Enviar");
        sendButton.setOnClickListener(v -> sendMessage());
        root.addView(sendButton);

        return scroll;
    }

    private void sendMessage() {
        String endpoint = endpointInput.getText().toString().trim();
        String model = modelInput.getText().toString().trim();
        String key = keyInput.getText().toString().trim();
        String prompt = promptInput.getText().toString().trim();

        if (prompt.isEmpty()) return;
        conversation.append("\nVoce: " + prompt + "\n");
        promptInput.setText("");

        if (endpoint.isEmpty() || model.isEmpty()) {
            conversation.append("Zoom IA: preencha o endpoint e o modelo primeiro.\n");
            return;
        }

        sendButton.setEnabled(false);
        sendButton.setText("Enviando...");

        executor.execute(() -> {
            String answer = callChatApi(endpoint, model, key, prompt);
            runOnUiThread(() -> {
                conversation.append("Zoom IA: " + answer + "\n");
                sendButton.setEnabled(true);
                sendButton.setText("Enviar");
            });
        });
    }

    private String callChatApi(String endpoint, String model, String key, String prompt) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            if (!key.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + key);
            }

            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);

            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("messages", messages);

            byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(data);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);

            if (code < 200 || code >= 300) {
                return "Erro HTTP " + code + ": " + trim(body, 500);
            }

            JSONObject root = new JSONObject(body);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return "A API respondeu, mas nao retornou choices[0].message.content.";
            }
            JSONObject messageObject = choices.optJSONObject(0).optJSONObject("message");
            if (messageObject == null) {
                return "Resposta recebida, mas sem o campo message.";
            }
            String content = messageObject.optString("content", "").trim();
            return content.isEmpty() ? "Resposta vazia da API." : content;
        } catch (Exception e) {
            return "Falha ao conectar: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private EditText addInput(LinearLayout root, String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(false);
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void addSection(LinearLayout root, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(21f);
        root.addView(view);
    }

    private void addTool(LinearLayout root, String name, String description) {
        TextView view = new TextView(this);
        view.setText("\n" + name + "\n" + description);
        view.setTextSize(16f);
        view.setPadding(12, 10, 12, 10);
        root.addView(view);
    }

    private void addSpace(LinearLayout root, int height) {
        View space = new View(this);
        root.addView(space, new LinearLayout.LayoutParams(1, height));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
