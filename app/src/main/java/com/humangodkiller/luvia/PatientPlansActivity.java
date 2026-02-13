package com.humangodkiller.luvia;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PatientPlansActivity extends AppCompatActivity {

    private static final String TAG = "PatientPlansActivity";
    private static final int REMINDER_MINUTES_BEFORE = 10;

    private RecyclerView recyclerView;
    private FloatingActionButton fabAddReminder;
    private PillReminderAdapter adapter;
    private List<PillReminder> reminderList;

    private FirebaseAuth mAuth;
    private DatabaseReference remindersRef;
    private ValueEventListener remindersListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_plans);

        // ── Toolbar ────────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Medicine Plans");
        }

        // ── Firebase ───────────────────────────────────────────────────────
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance(
                "https://luvia-cva-default-rtdb.asia-southeast1.firebasedatabase.app"
        );
        remindersRef = database.getReference("pillReminders").child(currentUser.getUid());

        // ── Initialize views ───────────────────────────────────────────────
        recyclerView = findViewById(R.id.recycler_reminders);
        fabAddReminder = findViewById(R.id.fab_add_reminder);

        reminderList = new ArrayList<>();
        adapter = new PillReminderAdapter(reminderList);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // ── Add reminder button ────────────────────────────────────────────
        fabAddReminder.setOnClickListener(v -> showAddReminderDialog());

        // ── Load reminders ─────────────────────────────────────────────────
        loadReminders();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (remindersRef != null && remindersListener != null) {
            remindersRef.removeEventListener(remindersListener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Load Reminders from Firebase
    // ═══════════════════════════════════════════════════════════════════════

    private void loadReminders() {
        remindersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reminderList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    PillReminder reminder = child.getValue(PillReminder.class);
                    if (reminder != null) {
                        reminder.setId(child.getKey());
                        reminderList.add(reminder);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load reminders", error.toException());
                Toast.makeText(PatientPlansActivity.this,
                        "Failed to load reminders", Toast.LENGTH_SHORT).show();
            }
        };
        remindersRef.addValueEventListener(remindersListener);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Add Reminder Dialog
    // ═══════════════════════════════════════════════════════════════════════

    private void showAddReminderDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null);

        EditText etPillName = dialogView.findViewById(R.id.et_pill_name);
        EditText etDosage = dialogView.findViewById(R.id.et_dosage);
        Button btnSelectTime = dialogView.findViewById(R.id.btn_select_time);
        TextView tvSelectedTime = dialogView.findViewById(R.id.tv_selected_time);

        final Calendar selectedTime = Calendar.getInstance();

        btnSelectTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(
                    this,
                    (view, hourOfDay, minute) -> {
                        selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedTime.set(Calendar.MINUTE, minute);

                        String timeStr = String.format(Locale.getDefault(),
                                "%02d:%02d", hourOfDay, minute);
                        tvSelectedTime.setText(timeStr);
                        tvSelectedTime.setVisibility(View.VISIBLE);
                    },
                    selectedTime.get(Calendar.HOUR_OF_DAY),
                    selectedTime.get(Calendar.MINUTE),
                    false
            );
            timePicker.show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Add Medicine Reminder")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String pillName = etPillName.getText().toString().trim();
                    String dosage = etDosage.getText().toString().trim();

                    if (pillName.isEmpty()) {
                        Toast.makeText(this, "Please enter medicine name",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (tvSelectedTime.getVisibility() != View.VISIBLE) {
                        Toast.makeText(this, "Please select a time",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    PillReminder reminder = new PillReminder(
                            pillName,
                            dosage.isEmpty() ? "As prescribed" : dosage,
                            selectedTime.getTimeInMillis(),
                            true
                    );

                    saveReminder(reminder);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Save Reminder to Firebase
    // ═══════════════════════════════════════════════════════════════════════

    private void saveReminder(PillReminder reminder) {
        String key = remindersRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Failed to create reminder", Toast.LENGTH_SHORT).show();
            return;
        }

        reminder.setId(key);
        remindersRef.child(key).setValue(reminder)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Reminder added successfully",
                            Toast.LENGTH_SHORT).show();

                    // Schedule alarms
                    scheduleAlarm(reminder, false);  // Main alarm
                    scheduleAlarm(reminder, true);   // 10-min early alarm
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save reminder", e);
                    Toast.makeText(this, "Failed to save reminder",
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Schedule Alarms
    // ═══════════════════════════════════════════════════════════════════════

    private void scheduleAlarm(PillReminder reminder, boolean isEarlyReminder) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, PillAlarmReceiver.class);
        intent.putExtra("pill_name", reminder.getPillName());
        intent.putExtra("dosage", reminder.getDosage());
        intent.putExtra("reminder_id", reminder.getId());
        intent.putExtra("is_early_reminder", isEarlyReminder);

        int requestCode = reminder.getId().hashCode() + (isEarlyReminder ? 1000 : 0);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long alarmTime = reminder.getScheduledTime();
        if (isEarlyReminder) {
            alarmTime -= (REMINDER_MINUTES_BEFORE * 60 * 1000); // 10 minutes earlier
        }

        // Schedule exact alarm
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
            );
        }

        Log.d(TAG, "Alarm scheduled for " + reminder.getPillName() +
                (isEarlyReminder ? " (10 min early)" : " (main)"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Delete Reminder
    // ═══════════════════════════════════════════════════════════════════════

    private void deleteReminder(PillReminder reminder) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Reminder")
                .setMessage("Are you sure you want to delete this reminder for " +
                        reminder.getPillName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    remindersRef.child(reminder.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
                                cancelAlarm(reminder, false);
                                cancelAlarm(reminder, true);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete reminder", e);
                                Toast.makeText(this, "Failed to delete reminder",
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cancelAlarm(PillReminder reminder, boolean isEarlyReminder) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, PillAlarmReceiver.class);
        int requestCode = reminder.getId().hashCode() + (isEarlyReminder ? 1000 : 0);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RecyclerView Adapter
    // ═══════════════════════════════════════════════════════════════════════

    private class PillReminderAdapter extends RecyclerView.Adapter<PillReminderAdapter.ViewHolder> {

        private final List<PillReminder> reminders;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        public PillReminderAdapter(List<PillReminder> reminders) {
            this.reminders = reminders;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pill_reminder, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PillReminder reminder = reminders.get(position);

            holder.tvPillName.setText(reminder.getPillName());
            holder.tvDosage.setText(reminder.getDosage());
            holder.tvTime.setText(timeFormat.format(reminder.getScheduledTime()));

            holder.btnDelete.setOnClickListener(v -> deleteReminder(reminder));
        }

        @Override
        public int getItemCount() {
            return reminders.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPillName, tvDosage, tvTime;
            ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvPillName = itemView.findViewById(R.id.tv_pill_name);
                tvDosage = itemView.findViewById(R.id.tv_dosage);
                tvTime = itemView.findViewById(R.id.tv_time);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Model
    // ═══════════════════════════════════════════════════════════════════════

    public static class PillReminder {
        private String id;
        private String pillName;
        private String dosage;
        private long scheduledTime;
        private boolean enabled;

        public PillReminder() {
            // Required for Firebase
        }

        public PillReminder(String pillName, String dosage, long scheduledTime, boolean enabled) {
            this.pillName = pillName;
            this.dosage = dosage;
            this.scheduledTime = scheduledTime;
            this.enabled = enabled;
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPillName() { return pillName; }
        public void setPillName(String pillName) { this.pillName = pillName; }

        public String getDosage() { return dosage; }
        public void setDosage(String dosage) { this.dosage = dosage; }

        public long getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(long scheduledTime) { this.scheduledTime = scheduledTime; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}