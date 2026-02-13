package com.humangodkiller.luvia;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class PatientRegistrationActivity extends AppCompatActivity {

    private static final String TAG = "PatientRegistration";

    // Blood group options
    private static final String[] BLOOD_GROUPS = {
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    };

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // UI elements
    private TextInputEditText etName, etHeight, etWeight, etAge, etPhone;
    private TextInputLayout tilName, tilHeight, tilWeight, tilAge, tilPhone, tilBloodGroup;
    private AutoCompleteTextView actvBloodGroup;
    private Button btnRegister;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_registration);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Guard: if somehow user is null, send back to login
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false); // registration is mandatory
            getSupportActionBar().setTitle("Complete Your Profile");
        }

        // Views
        tilName       = findViewById(R.id.til_name);
        tilHeight     = findViewById(R.id.til_height);
        tilWeight     = findViewById(R.id.til_weight);
        tilAge        = findViewById(R.id.til_age);
        tilBloodGroup = findViewById(R.id.til_blood_group);
        tilPhone      = findViewById(R.id.til_phone);

        etName        = findViewById(R.id.et_name);
        etHeight      = findViewById(R.id.et_height);
        etWeight      = findViewById(R.id.et_weight);
        etAge         = findViewById(R.id.et_age);
        etPhone       = findViewById(R.id.et_phone);
        actvBloodGroup = findViewById(R.id.actv_blood_group);

        btnRegister   = findViewById(R.id.btn_register);
        progressBar   = findViewById(R.id.progress_bar);

        // Pre-fill name from Google account
        if (currentUser.getDisplayName() != null) {
            etName.setText(currentUser.getDisplayName());
        }

        // Blood group dropdown adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, BLOOD_GROUPS);
        actvBloodGroup.setAdapter(adapter);

        // Register button
        btnRegister.setOnClickListener(v -> validateAndSubmit(currentUser));
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    private void validateAndSubmit(FirebaseUser user) {
        // Clear previous errors
        tilName.setError(null);
        tilHeight.setError(null);
        tilWeight.setError(null);
        tilAge.setError(null);
        tilBloodGroup.setError(null);
        tilPhone.setError(null);

        String name       = etName.getText() != null ? etName.getText().toString().trim() : "";
        String heightStr  = etHeight.getText() != null ? etHeight.getText().toString().trim() : "";
        String weightStr  = etWeight.getText() != null ? etWeight.getText().toString().trim() : "";
        String ageStr     = etAge.getText() != null ? etAge.getText().toString().trim() : "";
        String bloodGroup = actvBloodGroup.getText() != null ? actvBloodGroup.getText().toString().trim() : "";
        String phone      = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        boolean valid = true;

        if (TextUtils.isEmpty(name)) {
            tilName.setError("Full name is required");
            valid = false;
        }

        if (TextUtils.isEmpty(heightStr)) {
            tilHeight.setError("Height is required");
            valid = false;
        } else {
            try {
                float h = Float.parseFloat(heightStr);
                if (h <= 0 || h > 300) {
                    tilHeight.setError("Enter a valid height in cm (e.g. 170)");
                    valid = false;
                }
            } catch (NumberFormatException e) {
                tilHeight.setError("Enter a valid number");
                valid = false;
            }
        }

        if (TextUtils.isEmpty(weightStr)) {
            tilWeight.setError("Weight is required");
            valid = false;
        } else {
            try {
                float w = Float.parseFloat(weightStr);
                if (w <= 0 || w > 500) {
                    tilWeight.setError("Enter a valid weight in kg (e.g. 65)");
                    valid = false;
                }
            } catch (NumberFormatException e) {
                tilWeight.setError("Enter a valid number");
                valid = false;
            }
        }

        if (TextUtils.isEmpty(ageStr)) {
            tilAge.setError("Age is required");
            valid = false;
        } else {
            try {
                int age = Integer.parseInt(ageStr);
                if (age <= 0 || age > 150) {
                    tilAge.setError("Enter a valid age");
                    valid = false;
                }
            } catch (NumberFormatException e) {
                tilAge.setError("Enter a valid number");
                valid = false;
            }
        }

        if (TextUtils.isEmpty(bloodGroup) || !isValidBloodGroup(bloodGroup)) {
            tilBloodGroup.setError("Please select a blood group");
            valid = false;
        }

        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError("Phone number is required");
            valid = false;
        } else if (phone.length() < 7 || phone.length() > 15) {
            tilPhone.setError("Enter a valid phone number");
            valid = false;
        }

        if (valid) {
            saveRegistration(user, name, heightStr, weightStr, ageStr, bloodGroup, phone);
        }
    }

    private boolean isValidBloodGroup(String bg) {
        for (String group : BLOOD_GROUPS) {
            if (group.equalsIgnoreCase(bg)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Firestore
    // ---------------------------------------------------------------

    private void saveRegistration(FirebaseUser user,
                                  String name, String height, String weight,
                                  String age, String bloodGroup, String phone) {
        setFormEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("name",        name);
        data.put("heightCm",    Float.parseFloat(height));
        data.put("weightKg",    Float.parseFloat(weight));
        data.put("age",         Integer.parseInt(age));
        data.put("bloodGroup",  bloodGroup);
        data.put("phone",       phone);
        data.put("registered",  true);
        data.put("registeredAt", System.currentTimeMillis());

        // Merge so we don't overwrite uid / email / role set earlier
        db.collection("users").document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Patient registration saved.");
                    Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show();
                    goToDashboard();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to save registration", e);
                    Toast.makeText(this, "Failed to save profile. Please try again.", Toast.LENGTH_SHORT).show();
                    setFormEnabled(true);
                });
    }

    // ---------------------------------------------------------------
    // Navigation & UI helpers
    // ---------------------------------------------------------------

    private void goToDashboard() {
        Intent intent = new Intent(this, PatientDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setFormEnabled(boolean enabled) {
        progressBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        btnRegister.setEnabled(enabled);
        etName.setEnabled(enabled);
        etHeight.setEnabled(enabled);
        etWeight.setEnabled(enabled);
        etAge.setEnabled(enabled);
        actvBloodGroup.setEnabled(enabled);
        etPhone.setEnabled(enabled);
    }

    // Back button disabled — registration is mandatory for patients
    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Please complete your profile first.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}