package com.humangodkiller.luvia;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class PatientDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Profile views
    private TextView tvPatientName, tvPatientEmail;

    // Detail tile views
    private TextView tvAgeValue, tvBloodGroupValue;
    private TextView tvHeightValue, tvWeightValue, tvPhoneValue;

    // Bottom nav
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_dashboard);

        // ── Toolbar ──────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("Patient Dashboard");
        }

        // ── Firebase ─────────────────────────────────────────────
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        // ── Bind views ───────────────────────────────────────────
        tvPatientName   = findViewById(R.id.tv_patient_name);
        tvPatientEmail  = findViewById(R.id.tv_patient_email);
        tvAgeValue      = findViewById(R.id.tv_age_value);
        tvBloodGroupValue = findViewById(R.id.tv_blood_group_value);
        tvHeightValue   = findViewById(R.id.tv_height_value);
        tvWeightValue   = findViewById(R.id.tv_weight_value);
        tvPhoneValue    = findViewById(R.id.tv_phone_value);

        // ── Bottom Navigation ─────────────────────────────────────
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        setupBottomNavigation();

        // ── Load data ─────────────────────────────────────────────
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadPatientData(currentUser);
        } else {
            // Session expired — back to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Bottom Navigation
    // ─────────────────────────────────────────────────────────────

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                // Already here — nothing to do
                return true;

            } else if (id == R.id.nav_plans) {
                showComingSoon(
                        "Plans 📋",
                        "Health plans and subscriptions are on the way!\nStay tuned for updates."
                );
                return true;

            } else if (id == R.id.nav_sos) {
                showComingSoon(
                        "SOS 🚨",
                        "One-tap emergency alerts will be available soon.\nWe're working hard on it!"
                );
                return true;

            } else if (id == R.id.nav_message) {
                showComingSoon(
                        "Messages 💬",
                        "Direct chat with your doctor is coming soon.\nHang tight!"
                );
                return true;

            } else if (id == R.id.nav_map) {
                showComingSoon(
                        "Map 📍",
                        "Find nearby hospitals and clinics — coming soon!\nWe're mapping it out."
                );
                return true;
            }

            return false;
        });
    }

    private void showComingSoon(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got it!", null)
                .show();

        // Reset selection back to Home after dialog shown
        bottomNavigationView.post(() ->
                bottomNavigationView.setSelectedItemId(R.id.nav_home));
    }

    // ─────────────────────────────────────────────────────────────
    // Load patient data from Firestore
    // ─────────────────────────────────────────────────────────────

    private void loadPatientData(FirebaseUser user) {
        // Show Google account info immediately while Firestore loads
        String displayName = user.getDisplayName();
        String email       = user.getEmail();

        tvPatientName.setText(displayName != null ? displayName : "Patient");
        tvPatientEmail.setText(email != null ? email : "");

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        populateDetails(doc);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Could not load profile data.",
                                Toast.LENGTH_SHORT).show());
    }

    private void populateDetails(DocumentSnapshot doc) {
        // Name — prefer Firestore value (user may have edited it during registration)
        String name = doc.getString("name");
        if (name != null && !name.isEmpty()) {
            tvPatientName.setText(name);
        }

        // Age
        Long age = doc.getLong("age");
        tvAgeValue.setText(age != null ? age + " yrs" : "—");

        // Blood group
        String bloodGroup = doc.getString("bloodGroup");
        tvBloodGroupValue.setText(
                bloodGroup != null && !bloodGroup.isEmpty() ? bloodGroup : "—");

        // Height
        Double height = doc.getDouble("heightCm");
        tvHeightValue.setText(height != null ? (int) Math.round(height) + " cm" : "—");

        // Weight
        Double weight = doc.getDouble("weightKg");
        tvWeightValue.setText(weight != null ? (int) Math.round(weight) + " kg" : "—");

        // Phone
        String phone = doc.getString("phone");
        tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "—");
    }

    // ─────────────────────────────────────────────────────────────
    // Back press — dashboard is the root, so exit app cleanly
    // ─────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        // Move app to background instead of going back to registration
        moveTaskToBack(true);
    }
}