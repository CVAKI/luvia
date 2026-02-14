package com.humangodkiller.luvia;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
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

    // Standard early reminder window (10 minutes before alarm)
    private static final int REMINDER_MINUTES_BEFORE = 10;

    // If the gap between now and alarm is less than this, use a short reminder instead
    // The early reminder will fire after REMINDER_SHORT_DELAY_MINUTES from now
    private static final int REMINDER_SHORT_DELAY_MINUTES = 2;

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

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Medicine Plans");
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance(
                "https://luvia-cva-default-rtdb.asia-southeast1.firebasedatabase.app");
        remindersRef = database.getReference("pillReminders").child(currentUser.getUid());

        recyclerView   = findViewById(R.id.recycler_reminders);
        fabAddReminder = findViewById(R.id.fab_add_reminder);
        reminderList   = new ArrayList<>();
        adapter        = new PillReminderAdapter(reminderList);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fabAddReminder.setOnClickListener(v -> showAddReminderDialog());
        loadReminders();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (remindersRef != null && remindersListener != null)
            remindersRef.removeEventListener(remindersListener);
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    // ═══════════════════════════════════════════════════════════════════════
    // Load Reminders
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
    // Add Reminder Dialog — with Start Date & End Date
    // ═══════════════════════════════════════════════════════════════════════

    private void showAddReminderDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null);

        EditText etPillName     = dialogView.findViewById(R.id.et_pill_name);
        EditText etDosage       = dialogView.findViewById(R.id.et_dosage);
        Button btnSelectTime    = dialogView.findViewById(R.id.btn_select_time);
        TextView tvSelectedTime = dialogView.findViewById(R.id.tv_selected_time);
        Button btnStartDate     = dialogView.findViewById(R.id.btn_start_date);
        Button btnEndDate       = dialogView.findViewById(R.id.btn_end_date);
        TextView tvStartDate    = dialogView.findViewById(R.id.tv_start_date);
        TextView tvEndDate      = dialogView.findViewById(R.id.tv_end_date);

        final Calendar selectedTime  = Calendar.getInstance();
        final Calendar startDateCal  = Calendar.getInstance();
        final Calendar endDateCal    = Calendar.getInstance();
        final boolean[] timeSelected  = {false};
        final boolean[] startSelected = {false};
        final boolean[] endSelected   = {false};

        final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        btnSelectTime.setOnClickListener(v -> new TimePickerDialog(this,
                (view, h, m) -> {
                    selectedTime.set(Calendar.HOUR_OF_DAY, h);
                    selectedTime.set(Calendar.MINUTE, m);
                    selectedTime.set(Calendar.SECOND, 0);
                    tvSelectedTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
                    tvSelectedTime.setVisibility(View.VISIBLE);
                    timeSelected[0] = true;
                },
                selectedTime.get(Calendar.HOUR_OF_DAY),
                selectedTime.get(Calendar.MINUTE), false).show());

        btnStartDate.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(this,
                    (view, y, mo, d) -> {
                        startDateCal.set(y, mo, d);
                        tvStartDate.setText(dateFormat.format(startDateCal.getTime()));
                        tvStartDate.setVisibility(View.VISIBLE);
                        startSelected[0] = true;
                    },
                    startDateCal.get(Calendar.YEAR),
                    startDateCal.get(Calendar.MONTH),
                    startDateCal.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dp.show();
        });

        btnEndDate.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(this,
                    (view, y, mo, d) -> {
                        endDateCal.set(y, mo, d);
                        tvEndDate.setText(dateFormat.format(endDateCal.getTime()));
                        tvEndDate.setVisibility(View.VISIBLE);
                        endSelected[0] = true;
                    },
                    endDateCal.get(Calendar.YEAR),
                    endDateCal.get(Calendar.MONTH),
                    endDateCal.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dp.show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Add Medicine Reminder")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String pillName = etPillName.getText().toString().trim();
                    String dosage   = etDosage.getText().toString().trim();

                    if (pillName.isEmpty()) {
                        Toast.makeText(this, "Please enter medicine name", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (!timeSelected[0]) {
                        Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (!startSelected[0]) {
                        Toast.makeText(this, "Please select a start date", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (!endSelected[0]) {
                        Toast.makeText(this, "Please select an end date", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (endDateCal.before(startDateCal)) {
                        Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show(); return;
                    }

                    Calendar alarmCal = (Calendar) startDateCal.clone();
                    alarmCal.set(Calendar.HOUR_OF_DAY, selectedTime.get(Calendar.HOUR_OF_DAY));
                    alarmCal.set(Calendar.MINUTE,      selectedTime.get(Calendar.MINUTE));
                    alarmCal.set(Calendar.SECOND,      0);
                    alarmCal.set(Calendar.MILLISECOND, 0);
                    if (alarmCal.before(Calendar.getInstance()))
                        alarmCal.add(Calendar.DAY_OF_MONTH, 1);

                    endDateCal.set(Calendar.HOUR_OF_DAY, 23);
                    endDateCal.set(Calendar.MINUTE,      59);
                    endDateCal.set(Calendar.SECOND,      59);

                    saveReminder(new PillReminder(pillName, dosage,
                            alarmCal.getTimeInMillis(),
                            startDateCal.getTimeInMillis(),
                            endDateCal.getTimeInMillis(), true));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Save to Firebase
    // ═══════════════════════════════════════════════════════════════════════

    private void saveReminder(PillReminder reminder) {
        String key = remindersRef.push().getKey();
        if (key == null) return;
        reminder.setId(key);
        remindersRef.child(key).setValue(reminder)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Reminder added!", Toast.LENGTH_SHORT).show();
                    scheduleMainAlarm(reminder);
                    scheduleSmartEarlyAlarm(reminder);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save reminder", e);
                    Toast.makeText(this, "Failed to save reminder", Toast.LENGTH_SHORT).show();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Schedule Main Alarm (at the exact pill time)
    // ═══════════════════════════════════════════════════════════════════════

    private void scheduleMainAlarm(PillReminder reminder) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (reminder.getEndDate() > 0 && System.currentTimeMillis() > reminder.getEndDate()) {
            Log.d(TAG, "Skipping main alarm — end date passed for " + reminder.getPillName());
            return;
        }

        Intent intent = new Intent(this, PillAlarmReceiver.class);
        intent.putExtra("pill_name",         reminder.getPillName());
        intent.putExtra("dosage",            reminder.getDosage());
        intent.putExtra("reminder_id",       reminder.getId());
        intent.putExtra("is_early_reminder", false);
        intent.putExtra("minutes_remaining", 0); // main alarm — take now
        intent.putExtra("end_date",          reminder.getEndDate());

        int requestCode = reminder.getId().hashCode();
        PendingIntent pi = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long alarmTime = reminder.getScheduledTime();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi);
        else
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pi);

        Log.d(TAG, "Main alarm scheduled for " + reminder.getPillName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Smart Early Alarm — uses actual gap, not always 10 minutes
    //
    //  GAP >= 10 min  → early reminder fires 10 min before (standard)
    //  GAP < 10 min   → early reminder fires 2 min from now
    //                   and tells the patient the EXACT remaining minutes
    // ═══════════════════════════════════════════════════════════════════════

    private void scheduleSmartEarlyAlarm(PillReminder reminder) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (reminder.getEndDate() > 0 && System.currentTimeMillis() > reminder.getEndDate()) {
            Log.d(TAG, "Skipping early alarm — end date passed for " + reminder.getPillName());
            return;
        }

        long now           = System.currentTimeMillis();
        long alarmTime     = reminder.getScheduledTime();
        long gapMillis     = alarmTime - now;
        long gapMinutes    = gapMillis / (60 * 1000L);

        long earlyAlarmTime;
        int  minutesRemaining;

        if (gapMinutes >= REMINDER_MINUTES_BEFORE) {
            // Normal case: fire early reminder 10 min before
            earlyAlarmTime    = alarmTime - (REMINDER_MINUTES_BEFORE * 60 * 1000L);
            minutesRemaining  = REMINDER_MINUTES_BEFORE;
            Log.d(TAG, "Early alarm: standard 10-min-before for " + reminder.getPillName());
        } else if (gapMinutes > REMINDER_SHORT_DELAY_MINUTES) {
            // Short gap: fire 2 minutes from now, tell patient exact gap
            earlyAlarmTime    = now + (REMINDER_SHORT_DELAY_MINUTES * 60 * 1000L);
            minutesRemaining  = (int) gapMinutes; // e.g. 5 if alarm is 5 min away
            Log.d(TAG, "Early alarm: short gap (" + gapMinutes + " min) for " + reminder.getPillName()
                    + " — reminding in 2 min, saying '" + minutesRemaining + " min remaining'");
        } else {
            // Gap is too short (2 min or less) — skip early reminder entirely
            Log.d(TAG, "Skipping early alarm — gap too short (" + gapMinutes + " min) for " + reminder.getPillName());
            return;
        }

        Intent intent = new Intent(this, PillAlarmReceiver.class);
        intent.putExtra("pill_name",         reminder.getPillName());
        intent.putExtra("dosage",            reminder.getDosage());
        intent.putExtra("reminder_id",       reminder.getId());
        intent.putExtra("is_early_reminder", true);
        intent.putExtra("minutes_remaining", minutesRemaining); // ← actual minutes, not always 10
        intent.putExtra("end_date",          reminder.getEndDate());

        int requestCode = reminder.getId().hashCode() + 1000;
        PendingIntent pi = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, earlyAlarmTime, pi);
        else
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, earlyAlarmTime, pi);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Delete Reminder
    // ═══════════════════════════════════════════════════════════════════════

    private void deleteReminder(PillReminder reminder) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Reminder")
                .setMessage("Delete reminder for " + reminder.getPillName() + "?")
                .setPositiveButton("Delete", (dialog, which) ->
                        remindersRef.child(reminder.getId()).removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
                                    cancelAlarm(reminder, false);
                                    cancelAlarm(reminder, true);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null).show();
    }

    private void cancelAlarm(PillReminder reminder, boolean isEarlyReminder) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        int requestCode = reminder.getId().hashCode() + (isEarlyReminder ? 1000 : 0);
        PendingIntent pi = PendingIntent.getBroadcast(this, requestCode,
                new Intent(this, PillAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Adapter — shows date range
    // ═══════════════════════════════════════════════════════════════════════

    private class PillReminderAdapter extends RecyclerView.Adapter<PillReminderAdapter.ViewHolder> {
        private final List<PillReminder> reminders;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        public PillReminderAdapter(List<PillReminder> reminders) { this.reminders = reminders; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pill_reminder, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PillReminder r = reminders.get(position);
            holder.tvPillName.setText(r.getPillName());
            holder.tvDosage.setText("Dosage: " + r.getDosage());
            holder.tvTime.setText("Time: " + timeFormat.format(r.getScheduledTime()));

            if (r.getStartDate() > 0 && r.getEndDate() > 0) {
                holder.tvDateRange.setText(
                        dateFormat.format(r.getStartDate()) + "  →  " + dateFormat.format(r.getEndDate()));
                holder.tvDateRange.setVisibility(View.VISIBLE);
            } else {
                holder.tvDateRange.setVisibility(View.GONE);
            }
            holder.btnDelete.setOnClickListener(v -> deleteReminder(r));
        }

        @Override public int getItemCount() { return reminders.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPillName, tvDosage, tvTime, tvDateRange;
            ImageButton btnDelete;
            ViewHolder(View v) {
                super(v);
                tvPillName  = v.findViewById(R.id.tv_pill_name);
                tvDosage    = v.findViewById(R.id.tv_dosage);
                tvTime      = v.findViewById(R.id.tv_time);
                tvDateRange = v.findViewById(R.id.tv_date_range);
                btnDelete   = v.findViewById(R.id.btn_delete);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Model — added startDate & endDate
    // ═══════════════════════════════════════════════════════════════════════

    public static class PillReminder {
        private String id;
        private String pillName;
        private String dosage;
        private long scheduledTime;
        private long startDate;
        private long endDate;
        private boolean enabled;

        public PillReminder() {}

        public PillReminder(String pillName, String dosage, long scheduledTime,
                            long startDate, long endDate, boolean enabled) {
            this.pillName      = pillName;
            this.dosage        = dosage;
            this.scheduledTime = scheduledTime;
            this.startDate     = startDate;
            this.endDate       = endDate;
            this.enabled       = enabled;
        }

        public String getId()                    { return id; }
        public void setId(String id)             { this.id = id; }
        public String getPillName()              { return pillName; }
        public void setPillName(String p)        { this.pillName = p; }
        public String getDosage()                { return dosage; }
        public void setDosage(String d)          { this.dosage = d; }
        public long getScheduledTime()           { return scheduledTime; }
        public void setScheduledTime(long t)     { this.scheduledTime = t; }
        public long getStartDate()               { return startDate; }
        public void setStartDate(long startDate) { this.startDate = startDate; }
        public long getEndDate()                 { return endDate; }
        public void setEndDate(long endDate)     { this.endDate = endDate; }
        public boolean isEnabled()               { return enabled; }
        public void setEnabled(boolean enabled)  { this.enabled = enabled; }
    }
}