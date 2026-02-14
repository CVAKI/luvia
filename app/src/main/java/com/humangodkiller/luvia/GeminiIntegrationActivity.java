package com.humangodkiller.luvia;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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

    // ⚠️  Replace with your NEW API key after revoking the old one at aistudio.google.com
    private static final String GEMINI_API_KEY = "YOUR_NEW_API_KEY_HERE";
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-04-17:generateContent?key="
                    + GEMINI_API_KEY;

    private TextToSpeech textToSpeech;
    private ExecutorService executorService;
    private MediaPlayer mediaPlayer;

    private boolean ttsReady       = false;
    private String  pendingMessage  = null;
    private String  alarmLanguage   = "en";

    // Tracks how many times the MP3 alarm has played
    private int alarmPlayCount = 0;
    private static final int ALARM_REPEAT_COUNT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        executorService = Executors.newSingleThreadExecutor();

        Intent intent = getIntent();
        if (intent == null) { finish(); return; }

        String  pillName         = intent.getStringExtra("pill_name");
        String  dosage           = intent.getStringExtra("dosage");
        boolean isEarlyReminder  = intent.getBooleanExtra("is_early_reminder", false);
        // ← NEW: actual minutes remaining passed from PatientPlansActivity
        int     minutesRemaining = intent.getIntExtra("minutes_remaining", 10);

        if (pillName == null) { finish(); return; }

        final String  fp  = pillName;
        final String  fd  = dosage;
        final boolean fe  = isEarlyReminder;
        final int     fmr = minutesRemaining;

        // Step 1: Read alarmLanguage from Firestore, then start the alarm MP3
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        String lang = doc.getString("alarmLanguage");
                        if (lang != null && !lang.isEmpty()) alarmLanguage = lang;
                        Log.d(TAG, "Alarm language: " + alarmLanguage);
                        startAlarmThenSpeak(fp, fd, fe, fmr);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Could not read language, defaulting to English");
                        startAlarmThenSpeak(fp, fd, fe, fmr);
                    });
        } else {
            startAlarmThenSpeak(fp, fd, fe, fmr);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Step 1: Play alarm MP3 twice, THEN speak AI message
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Plays res/raw/alarm.mp3 exactly ALARM_REPEAT_COUNT times.
     * After the final play, initialises TTS and speaks the Gemini message.
     * Place your MP3 at: app/src/main/res/raw/alarm.mp3
     */
    private void startAlarmThenSpeak(String pillName, String dosage,
                                     boolean isEarlyReminder, int minutesRemaining) {
        alarmPlayCount = 0;

        mediaPlayer = MediaPlayer.create(this, R.raw.alarm);
        if (mediaPlayer == null) {
            Log.e(TAG, "MediaPlayer could not load R.raw.alarm — skipping to TTS");
            initTTSAndFetch(pillName, dosage, isEarlyReminder, minutesRemaining);
            return;
        }

        // Pre-fetch Gemini while alarm MP3 is playing — no delay after alarm finishes
        prefetchGeminiMessage(pillName, dosage, isEarlyReminder, minutesRemaining);

        mediaPlayer.setOnCompletionListener(mp -> {
            alarmPlayCount++;
            Log.d(TAG, "Alarm play #" + alarmPlayCount + " done");

            if (alarmPlayCount < ALARM_REPEAT_COUNT) {
                mp.seekTo(0);
                mp.start();
            } else {
                mp.release();
                mediaPlayer = null;
                Log.d(TAG, "Alarm finished. Starting TTS.");
                initTTSAndFetch(pillName, dosage, isEarlyReminder, minutesRemaining);
            }
        });

        mediaPlayer.start();
        Log.d(TAG, "Alarm MP3 started (play 1 of " + ALARM_REPEAT_COUNT + ")");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Pre-fetch Gemini in background while alarm MP3 plays
    // ═══════════════════════════════════════════════════════════════════════

    private void prefetchGeminiMessage(String pillName, String dosage,
                                       boolean isEarly, int minutesRemaining) {
        executorService.execute(() -> {
            try {
                String prompt   = buildPrompt(pillName, dosage, isEarly, minutesRemaining, alarmLanguage);
                String message  = callGeminiAPI(prompt);
                String finalMsg = (message != null && !message.isEmpty())
                        ? message : getFallback(pillName, dosage, isEarly, minutesRemaining, alarmLanguage);
                Log.d(TAG, "Gemini message ready: " + finalMsg);
                pendingMessage = finalMsg;
            } catch (Exception e) {
                Log.e(TAG, "Error pre-fetching Gemini message", e);
                pendingMessage = getFallback(pillName, dosage, isEarly, minutesRemaining, alarmLanguage);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Step 2: Init TTS + speak when ready
    // ═══════════════════════════════════════════════════════════════════════

    private void initTTSAndFetch(String pillName, String dosage,
                                 boolean isEarlyReminder, int minutesRemaining) {
        Locale ttsLocale = getTTSLocale(alarmLanguage);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(ttsLocale);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS locale not supported: " + ttsLocale + ", falling back to English");
                    textToSpeech.setLanguage(Locale.US);
                }
                ttsReady = true;
                Log.d(TAG, "TTS ready: " + ttsLocale);

                if (pendingMessage != null) {
                    runOnUiThread(() -> speakNow(pendingMessage));
                } else {
                    Log.d(TAG, "Waiting for Gemini response...");
                }
            } else {
                Log.e(TAG, "TTS init failed");
            }
        });

        // If Gemini hasn't been pre-fetched yet, fetch now
        if (pendingMessage == null) {
            generateAndSpeakReminder(pillName, dosage, isEarlyReminder, minutesRemaining);
        }
    }

    private Locale getTTSLocale(String code) {
        switch (code) {
            case "ml": return new Locale("ml", "IN");
            case "hi": return new Locale("hi", "IN");
            default:   return Locale.US;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Fallback Gemini fetch (if prefetch wasn't used)
    // ═══════════════════════════════════════════════════════════════════════

    private void generateAndSpeakReminder(String pillName, String dosage,
                                          boolean isEarly, int minutesRemaining) {
        executorService.execute(() -> {
            try {
                String prompt  = buildPrompt(pillName, dosage, isEarly, minutesRemaining, alarmLanguage);
                String message = callGeminiAPI(prompt);
                String final_  = (message != null && !message.isEmpty())
                        ? message : getFallback(pillName, dosage, isEarly, minutesRemaining, alarmLanguage);
                Log.d(TAG, "Message: " + final_);
                runOnUiThread(() -> deliverMessage(final_));
            } catch (Exception e) {
                Log.e(TAG, "Error generating reminder", e);
                runOnUiThread(() -> deliverMessage(
                        getFallback(pillName, dosage, isEarly, minutesRemaining, alarmLanguage)));
            }
        });
    }

    private void deliverMessage(String message) {
        if (ttsReady) speakNow(message);
        else          { Log.d(TAG, "TTS not ready, queuing"); pendingMessage = message; }
    }

    private void speakNow(String message) {
        Log.d(TAG, "Speaking: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) { Log.d(TAG, "TTS started"); }
            @Override public void onDone(String id)  { Log.d(TAG, "TTS done"); finish(); }
            @Override public void onError(String id) { Log.e(TAG, "TTS error"); finish(); }
        });

        int r = textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reminder");
        if (r == TextToSpeech.ERROR) { Log.e(TAG, "speak() ERROR"); finish(); }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Language-aware Gemini prompt — uses ACTUAL minutes, not always "10"
    // ═══════════════════════════════════════════════════════════════════════

    private String buildPrompt(String pillName, String dosage,
                               boolean isEarly, int minutesRemaining, String lang) {
        String langInstr;
        switch (lang) {
            case "ml": langInstr = "Respond ONLY in Malayalam (മലയാളം). Do not use English."; break;
            case "hi": langInstr = "Respond ONLY in Hindi (हिन्दी). Do not use English."; break;
            default:   langInstr = "Respond in English."; break;
        }

        if (isEarly) {
            // Uses the real minutesRemaining (e.g. 5, not always 10)
            return langInstr + " Generate a friendly caring reminder (max 2 sentences) for a patient. " +
                    "Their medicine '" + pillName + "' (dosage: " + dosage + ") is due in exactly " +
                    minutesRemaining + " minute" + (minutesRemaining == 1 ? "" : "s") + ". " +
                    "Be warm and encouraging. Mention the exact time left. Keep it conversational like a caring friend.";
        } else {
            return langInstr + " Generate a friendly caring reminder (max 2 sentences) for a patient. " +
                    "It's time to take '" + pillName + "' (dosage: " + dosage + "). " +
                    "Be warm and encouraging. Emphasise taking medicine on time. Keep it conversational.";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Fallback messages — also uses actual minutesRemaining
    // ═══════════════════════════════════════════════════════════════════════

    private String getFallback(String pillName, String dosage,
                               boolean isEarly, int minutesRemaining, String lang) {
        switch (lang) {
            case "ml":
                return isEarly
                        ? "ശ്രദ്ധിക്കൂ! " + minutesRemaining + " മിനിറ്റിനുള്ളിൽ " + pillName + " (" + dosage + ") കഴിക്കണം. ദയവായി ഇപ്പോൾ തയ്യാറാകൂ."
                        : "ഇപ്പോൾ " + pillName + " (" + dosage + ") കഴിക്കേണ്ട സമയമായി. നിങ്ങളുടെ ആരോഗ്യം പ്രധാനമാണ്.";
            case "hi":
                return isEarly
                        ? "ध्यान दें! " + minutesRemaining + " मिनट में " + pillName + " (" + dosage + ") लेने का समय है। कृपया अभी तैयारी करें।"
                        : "अब " + pillName + " (" + dosage + ") लेने का समय हो गया है। अपनी दवाई समय पर लें।";
            default:
                return isEarly
                        ? "Hello! " + pillName + " (" + dosage + ") is due in " + minutesRemaining
                        + " minute" + (minutesRemaining == 1 ? "" : "s") + ". Please prepare now."
                        : "Time to take " + pillName + " (" + dosage + "). Your health matters!";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Gemini API
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

            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            body.put("contents", contents);

            OutputStream os = connection.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush(); os.close();

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                String parsed = parseGeminiResponse(sb.toString());
                Log.d(TAG, "Gemini OK: " + parsed);
                return parsed;
            } else {
                Log.e(TAG, "API failed: " + code);
                return null;
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Gemini API error", e); return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String parseGeminiResponse(String json) {
        try {
            JSONObject r = new JSONObject(json);
            JSONArray candidates = r.getJSONArray("candidates");
            if (candidates.length() > 0) {
                JSONObject c = candidates.getJSONObject(0).getJSONObject("content");
                JSONArray parts = c.getJSONArray("parts");
                if (parts.length() > 0)
                    return parts.getJSONObject(0).getString("text").trim();
            }
        } catch (JSONException e) { Log.e(TAG, "Parse error", e); }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (executorService != null) executorService.shutdown();
    }
}