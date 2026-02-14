package com.humangodkiller.luvia;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class PatientDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView tvPatientName, tvPatientEmail;
    private TextView tvAgeValue, tvBloodGroupValue;
    private TextView tvHeightValue, tvWeightValue, tvPhoneValue;
    private TextView tvLanguage;

    private CardView cardProfile, cardHealth, cardSettings, cardAppointments, cardRecords;
    private BottomNavigationView bottomNavigationView;

    private static final String[] LANGUAGE_LABELS = {"English", "Malayalam", "Hindi"};
    private static final String[] LANGUAGE_CODES  = {"en", "ml", "hi"};

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_dashboard);

        setupToolbar();
        bindViews();
        setupClickListeners();
        setupBottomNavigation();
        checkAuthAndLoad();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("My Dashboard");
        }
    }

    private void bindViews() {
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        tvPatientName     = findViewById(R.id.tv_patient_name);
        tvPatientEmail    = findViewById(R.id.tv_patient_email);
        tvAgeValue        = findViewById(R.id.tv_age_value);
        tvBloodGroupValue = findViewById(R.id.tv_blood_group_value);
        tvHeightValue     = findViewById(R.id.tv_height_value);
        tvWeightValue     = findViewById(R.id.tv_weight_value);
        tvPhoneValue      = findViewById(R.id.tv_phone_value);
        tvLanguage        = findViewById(R.id.tv_language_value);

        cardProfile      = findViewById(R.id.card_profile);
        cardHealth       = findViewById(R.id.card_health);
        cardSettings     = findViewById(R.id.card_settings);
        cardAppointments = findViewById(R.id.card_appointments);
        cardRecords      = findViewById(R.id.card_records);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Pre-hide cards for staggered entrance
        View[] cards = {cardProfile, cardHealth, cardSettings, cardAppointments, cardRecords};
        for (View card : cards) {
            card.setAlpha(0f);
            card.setTranslationY(60f);
        }
    }

    private void setupClickListeners() {
        // Language picker
        findViewById(R.id.layout_language_setting).setOnClickListener(v -> {
            animatePulse(v);
            showLanguagePicker();
        });

        // Logout button
        findViewById(R.id.layout_logout).setOnClickListener(v -> {
            animatePulse(v);
            showLogoutConfirmDialog();
        });
    }

    private void checkAuthAndLoad() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadPatientData(currentUser);
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entrance Animations
    // ─────────────────────────────────────────────────────────────────────────

    private void runEntranceAnimations() {
        View[] cards = {cardProfile, cardHealth, cardSettings, cardAppointments, cardRecords};
        long baseDelay = 80L;

        for (int i = 0; i < cards.length; i++) {
            View card = cards[i];
            long delay = i * baseDelay + 100L;

            ObjectAnimator fadeIn   = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f);
            ObjectAnimator slideUp  = ObjectAnimator.ofFloat(card, "translationY", 60f, 0f);

            fadeIn.setDuration(400);
            slideUp.setDuration(450);
            fadeIn.setStartDelay(delay);
            slideUp.setStartDelay(delay);
            slideUp.setInterpolator(new AccelerateDecelerateInterpolator());

            AnimatorSet set = new AnimatorSet();
            set.playTogether(fadeIn, slideUp);
            set.start();
        }
    }

    /** Subtle bounce-scale pulse on click */
    private void animatePulse(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.96f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.96f, 1f);
        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.setInterpolator(new OvershootInterpolator(4f));
        scaleY.setInterpolator(new OvershootInterpolator(4f));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Language Picker
    // ─────────────────────────────────────────────────────────────────────────

    private void showLanguagePicker() {
        new AlertDialog.Builder(this)
                .setTitle("Alarm Language / ഭാഷ / भाषा")
                .setItems(LANGUAGE_LABELS, (dialog, which) ->
                        saveLanguageToFirestore(LANGUAGE_CODES[which], LANGUAGE_LABELS[which]))
                .show();
    }

    private void saveLanguageToFirestore(String code, String label) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        db.collection("users").document(user.getUid())
                .update("alarmLanguage", code)
                .addOnSuccessListener(unused -> {
                    tvLanguage.setText(label);
                    Toast.makeText(this, "Alarm language: " + label, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save language", Toast.LENGTH_SHORT).show());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to log out of your account?")
                .setPositiveButton("Sign Out", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        // Fade out animation before logging out
        View root = findViewById(android.R.id.content);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(root, "alpha", 1f, 0f);
        fadeOut.setDuration(300);
        fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                mAuth.signOut();
                Intent intent = new Intent(PatientDashboardActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
        fadeOut.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bottom Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home)    { return true; }
            if (id == R.id.nav_plans)   {
                startActivity(new Intent(this, PatientPlansActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                return true;
            }
            if (id == R.id.nav_sos)     { showComingSoon("🚨 SOS", "Emergency alerts coming soon!"); return true; }
            if (id == R.id.nav_message) { showComingSoon("💬 Messages", "Doctor chat coming soon!"); return true; }
            if (id == R.id.nav_map)     { showComingSoon("📍 Map", "Nearby clinics — coming soon!"); return true; }
            return false;
        });
    }

    private void showComingSoon(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got it!", null)
                .show();
        bottomNavigationView.post(() ->
                bottomNavigationView.setSelectedItemId(R.id.nav_home));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load data + language
    // ─────────────────────────────────────────────────────────────────────────

    private void loadPatientData(FirebaseUser user) {
        tvPatientName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Patient");
        tvPatientEmail.setText(user.getEmail() != null ? user.getEmail() : "");

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        populateDetails(doc);
                        loadLanguageSetting(doc);
                    }
                    // Run entrance animations after data is ready
                    runEntranceAnimations();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not load profile.", Toast.LENGTH_SHORT).show();
                    runEntranceAnimations();
                });
    }

    private void populateDetails(DocumentSnapshot doc) {
        String name = doc.getString("name");
        if (name != null && !name.isEmpty()) tvPatientName.setText(name);

        Long age = doc.getLong("age");
        tvAgeValue.setText(age != null ? age + " yrs" : "—");

        String bg = doc.getString("bloodGroup");
        tvBloodGroupValue.setText(bg != null && !bg.isEmpty() ? bg : "—");

        Double h = doc.getDouble("heightCm");
        tvHeightValue.setText(h != null ? (int) Math.round(h) + " cm" : "—");

        Double w = doc.getDouble("weightKg");
        tvWeightValue.setText(w != null ? (int) Math.round(w) + " kg" : "—");

        String phone = doc.getString("phone");
        tvPhoneValue.setText(phone != null && !phone.isEmpty() ? phone : "—");
    }

    private void loadLanguageSetting(DocumentSnapshot doc) {
        String code = doc.getString("alarmLanguage");
        if (code == null || code.isEmpty()) { tvLanguage.setText("English"); return; }
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(code)) {
                tvLanguage.setText(LANGUAGE_LABELS[i]);
                return;
            }
        }
        tvLanguage.setText("English");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Back press → move to background (don't destroy dashboard)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}