package com.humangodkiller.luvia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiIntegrationActivity extends AppCompatActivity {

    private static final String TAG = "GeminiIntegration";

    // TODO: Replace with your actual Gemini API key
    private static final String GEMINI_API_KEY = "AIzaSyAT3gItPUmenkqKYAU0jkPbh9kTr2EyH4o--hghcfuyt 6d t t";
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + GEMINI_API_KEY;

    private TextToSpeech textToSpeech;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This activity works in the background, no UI needed
        // It will be triggered by the alarm receiver

        executorService = Executors.newSingleThreadExecutor();

        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported");
                }
            } else {
                Log.e(TAG, "TTS initialization failed");
            }
        });

        // Get reminder details from intent
        Intent intent = getIntent();
        if (intent != null) {
            String pillName = intent.getStringExtra("pill_name");
            String dosage = intent.getStringExtra("dosage");
            boolean isEarlyReminder = intent.getBooleanExtra("is_early_reminder", false);

            if (pillName != null) {
                generateAndSpeakReminder(pillName, dosage, isEarlyReminder);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Generate Reminder Message using Gemini API
    // ═══════════════════════════════════════════════════════════════════════

    private void generateAndSpeakReminder(String pillName, String dosage, boolean isEarlyReminder) {
        executorService.execute(() -> {
            try {
                String prompt = buildPrompt(pillName, dosage, isEarlyReminder);
                String reminderMessage = callGeminiAPI(prompt);

                if (reminderMessage != null && !reminderMessage.isEmpty()) {
                    runOnUiThread(() -> speakReminder(reminderMessage));
                } else {
                    // Fallback message if API fails
                    runOnUiThread(() -> speakReminder(getFallbackMessage(pillName, dosage, isEarlyReminder)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error generating reminder", e);
                runOnUiThread(() -> speakReminder(getFallbackMessage(pillName, dosage, isEarlyReminder)));
            }
        });
    }

    private String buildPrompt(String pillName, String dosage, boolean isEarlyReminder) {
        if (isEarlyReminder) {
            return String.format(
                    "Generate a friendly, caring reminder message (max 2 sentences) for a patient. " +
                            "Their medicine '%s' (dosage: %s) is due in 10 minutes. " +
                            "The message should be warm and encouraging, reminding them to prepare. " +
                            "Keep it natural and conversational, like a caring friend would speak.",
                    pillName, dosage
            );
        } else {
            return String.format(
                    "Generate a friendly, caring reminder message (max 2 sentences) for a patient. " +
                            "It's time to take their medicine '%s' (dosage: %s). " +
                            "The message should be warm, encouraging, and emphasize the importance of taking " +
                            "medication on time. Keep it natural and conversational, like a caring friend would speak.",
                    pillName, dosage
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Call Gemini API
    // ═══════════════════════════════════════════════════════════════════════

    private String callGeminiAPI(String prompt) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(GEMINI_API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Build request body
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            // Send request
            OutputStream os = connection.getOutputStream();
            os.write(requestBody.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return parseGeminiResponse(response.toString());
            } else {
                Log.e(TAG, "API call failed with code: " + responseCode);
                return null;
            }

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error calling Gemini API", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String parseGeminiResponse(String responseJson) {
        try {
            JSONObject jsonResponse = new JSONObject(responseJson);
            JSONArray candidates = jsonResponse.getJSONArray("candidates");

            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject content = candidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");

                if (parts.length() > 0) {
                    JSONObject part = parts.getJSONObject(0);
                    return part.getString("text").trim();
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Gemini response", e);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Fallback Messages
    // ═══════════════════════════════════════════════════════════════════════

    private String getFallbackMessage(String pillName, String dosage, boolean isEarlyReminder) {
        if (isEarlyReminder) {
            return String.format(
                    "Hello! Just a gentle reminder that you need to take %s (%s) in 10 minutes. " +
                            "Please prepare your medication now.",
                    pillName, dosage
            );
        } else {
            return String.format(
                    "It's time to take your medicine! Please take %s, dosage %s. " +
                            "Taking your medication on time is important for your health.",
                    pillName, dosage
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Text-to-Speech
    // ═══════════════════════════════════════════════════════════════════════

    private void speakReminder(String message) {
        if (textToSpeech != null) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reminder");

            // Show toast notification
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            // Keep activity alive until speech is done
            textToSpeech.setOnUtteranceProgressListener(
                    new android.speech.tts.UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            Log.d(TAG, "Speaking reminder");
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            Log.d(TAG, "Finished speaking");
                            finish();
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.e(TAG, "TTS error");
                            finish();
                        }
                    });
        } else {
            finish();
        }
    }
}