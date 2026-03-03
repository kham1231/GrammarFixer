package com.example.grammarfixer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    
    // Ваш API ключ и адрес Mistral
    private static final String API_KEY = "WgPi9EtJuv9oTUZQTdL2NppZb3jNu6u2";
    private static final String API_URL = "https://api.mistral.ai/v1/chat/completions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        boolean isReadOnly = getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);

        if (text != null && !isReadOnly) {
            // Показываем уведомление, что процесс пошел
            Toast.makeText(this, "Исправляем через Mistral...", Toast.LENGTH_SHORT).show();
            // Запускаем запрос в фоне
            fixTextWithMistral(text.toString());
        } else {
            finish();
        }
    }

    private void fixTextWithMistral(String originalText) {
        // Создаем отдельный поток для интернета, чтобы не повесить телефон
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            String resultText = originalText; // Если будет ошибка, вернем оригинал

            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
                conn.setDoOutput(true);

                // Жесткий системный промпт
                String systemPrompt = "Ты профессиональный редактор. Исправь все орфографические, грамматические и пунктуационные ошибки. В ответе пришли ТОЛЬКО исправленный текст. Никаких приветствий, кавычек или пояснений. Если ошибок нет, верни исходный текст.";
                
                // Очищаем текст от символов, которые могут сломать JSON
                String escapedText = originalText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");

                // Формируем JSON-запрос
                String jsonInputString = "{"
                        + "\"model\": \"mistral-small-latest\","
                        + "\"messages\": ["
                        + "{\"role\": \"system\", \"content\": \"" + systemPrompt + "\"},"
                        + "{\"role\": \"user\", \"content\": \"" + escapedText + "\"}"
                        + "]"
                        + "}";

                // Отправляем запрос
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Читаем ответ
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    // Парсим ответ от Mistral
                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray choices = jsonObject.getJSONArray("choices");
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    resultText = message.getString("content").trim();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Возвращаемся в главный поток интерфейса, чтобы отдать текст и закрыть программу
            final String finalResult = resultText;
            handler.post(() -> {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, finalResult);
                setResult(RESULT_OK, resultIntent);
                finish();
            });
     
        });
    }
}
