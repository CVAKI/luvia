package com.humangodkiller.luvia;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView tvWelcome;
    private CardView cardDoctor, cardPatient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvWelcome   = findViewById(R.id.tv_welcome);
        cardDoctor  = findViewById(R.id.card_doctor);
        cardPatient = findViewById(R.id.card_patient);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvWelcome.setText("Welcome, " + currentUser.getDisplayName() + "!\nPlease select your role.");

        cardDoctor.setOnClickListener(v ->
                confirmAndSaveRole("doctor", currentUser));
        cardPatient.setOnClickListener(v ->
                confirmAndSaveRole("patient", currentUser));
    }

    /**
     * Show a confirmation dialog before locking in the role.
     * This is a one-time permanent choice — user should be sure.
     */
    private void confirmAndSaveRole(String role, FirebaseUser user) {
        String roleDisplay = "doctor".equals(role) ? "Doctor" : "Patient";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Role")
                .setMessage("You are selecting: " + roleDisplay
                        + "\n\nThis cannot be changed later. Are you sure?")
                .setPositiveButton("Yes, I'm sure",
                        (dialog, which) -> saveRoleToFirestore(role, user))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveRoleToFirestore(String role, FirebaseUser user) {
        setCardsEnabled(false);

        // Build a full user map so the doc is created if it doesn't exist yet,
        // or just the role field is merged if it does — handles both cases safely.
        Map<String, Object> data = new HashMap<>();
        data.put("uid",        user.getUid());
        data.put("name",       user.getDisplayName());
        data.put("email",      user.getEmail());
        data.put("photoUrl",   user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        data.put("role",       role);
        data.put("registered", false); // Patients will set this true after registration form
        data.put("createdAt",  System.currentTimeMillis());

        // SetOptions.merge() = create doc if missing, otherwise only update provided fields
        db.collection("users").document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Role saved: " + role);
                    Toast.makeText(this, "Role saved!", Toast.LENGTH_SHORT).show();
                    navigateToDashboard(role);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to save role", e);
                    setCardsEnabled(true);
                    Toast.makeText(this,
                            "Failed to save role. Please try again.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Doctors go straight to their dashboard.
     * Patients are always new here (first role selection),
     * so they always go to the registration form first.
     */
    private void navigateToDashboard(String role) {
        Intent intent = "doctor".equals(role)
                ? new Intent(this, DoctorDashboardActivity.class)
                : new Intent(this, PatientRegistrationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setCardsEnabled(boolean enabled) {
        cardDoctor.setEnabled(enabled);
        cardPatient.setEnabled(enabled);
        cardDoctor.setAlpha(enabled ? 1.0f : 0.5f);
        cardPatient.setAlpha(enabled ? 1.0f : 0.5f);
    }
}