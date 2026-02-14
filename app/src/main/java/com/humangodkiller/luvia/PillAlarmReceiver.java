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

        String  pillName         = intent.getStringExtra("pill_name");
        String  dosage           = intent.getStringExtra("dosage");
        String  reminderId       = intent.getStringExtra("reminder_id");
        boolean isEarlyReminder  = intent.getBooleanExtra("is_early_reminder", false);

        // ← NEW: pass through the actual minutes remaining (set by PatientPlansActivity)
        int minutesRemaining = intent.getIntExtra("minutes_remaining", 10);

        Log.d(TAG, "Reminder: " + pillName + " - " + dosage +
                " (Early: " + isEarlyReminder + ", Minutes: " + minutesRemaining + ")");

        // Start Gemini Integration Activity to play alarm MP3 then speak AI reminder
        Intent geminiIntent = new Intent(context, GeminiIntegrationActivity.class);
        geminiIntent.putExtra("pill_name",         pillName);
        geminiIntent.putExtra("dosage",            dosage);
        geminiIntent.putExtra("reminder_id",       reminderId);
        geminiIntent.putExtra("is_early_reminder", isEarlyReminder);
        geminiIntent.putExtra("minutes_remaining", minutesRemaining); // ← pass it along
        geminiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(geminiIntent);

        // Also show a toast notification with correct minutes
        String message;
        if (isEarlyReminder) {
            message = minutesRemaining == 1
                    ? "Reminder: " + pillName + " in 1 minute!"
                    : "Reminder: " + pillName + " in " + minutesRemaining + " minutes";
        } else {
            message = "Time to take: " + pillName;
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}