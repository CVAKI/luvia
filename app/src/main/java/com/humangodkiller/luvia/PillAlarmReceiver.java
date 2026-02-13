package com.humangodkiller.luvia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Broadcast Receiver for Medicine Reminder Alarms
 * Triggers when scheduled alarm time is reached
 */
public class PillAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "PillAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received");

        String pillName = intent.getStringExtra("pill_name");
        String dosage = intent.getStringExtra("dosage");
        String reminderId = intent.getStringExtra("reminder_id");
        boolean isEarlyReminder = intent.getBooleanExtra("is_early_reminder", false);

        Log.d(TAG, "Reminder: " + pillName + " - " + dosage +
                " (Early: " + isEarlyReminder + ")");

        // Start Gemini Integration Activity to generate and speak reminder
        Intent geminiIntent = new Intent(context, GeminiIntegrationActivity.class);
        geminiIntent.putExtra("pill_name", pillName);
        geminiIntent.putExtra("dosage", dosage);
        geminiIntent.putExtra("reminder_id", reminderId);
        geminiIntent.putExtra("is_early_reminder", isEarlyReminder);
        geminiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(geminiIntent);

        // Also show a toast notification
        String message = isEarlyReminder
                ? "Reminder: " + pillName + " in 10 minutes"
                : "Time to take: " + pillName;

        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}