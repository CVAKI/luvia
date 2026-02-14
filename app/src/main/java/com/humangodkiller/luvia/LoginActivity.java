package com.humangodkiller.luvia;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG     = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    // Firebase
    private FirebaseAuth       mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore  db;

    // Views
    private Button      btnGoogleSignIn;   // plain Button — matches <Button> in XML
    private ProgressBar progressBar;
    private View        glowOrbTop;
    private View        glowOrbBottom;
    private View        accentLine;
    private View        ivLogoMark;
    private TextView    tvAppName;
    private TextView    tvTagline;
    private View        dividerBrand;
    private CardView    cardSignIn;
    private TextView    tvTerms;

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initFirebase();
        bindViews();
        setupGoogleSignIn();
        setupClickListeners();
        runEntranceAnimations();
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            showProgressBar(true);
            checkUserRoleAndNavigate(currentUser);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Init helpers
    // ─────────────────────────────────────────────────────────────

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();
    }

    private void bindViews() {
        glowOrbTop      = findViewById(R.id.glow_orb_top);
        glowOrbBottom   = findViewById(R.id.glow_orb_bottom);
        accentLine      = findViewById(R.id.accent_line);
        ivLogoMark      = findViewById(R.id.iv_logo_mark);
        tvAppName       = findViewById(R.id.tv_app_name);
        tvTagline       = findViewById(R.id.tv_tagline);
        dividerBrand    = findViewById(R.id.divider_brand);
        cardSignIn      = findViewById(R.id.card_sign_in);
        tvTerms         = findViewById(R.id.tv_terms);
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);  // Button ✓
        progressBar     = findViewById(R.id.progress_bar);
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupClickListeners() {
        btnGoogleSignIn.setOnClickListener(v -> signIn());
        attachCardPressEffect(cardSignIn);
    }

    // ─────────────────────────────────────────────────────────────
    // Google Sign-In flow
    // ─────────────────────────────────────────────────────────────

    private void signIn() {
        showProgressBar(true);
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this,
                        "Google sign in failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                showProgressBar(false);
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) checkUserRoleAndNavigate(user);
            } else {
                Log.w(TAG, "signInWithCredential:failure", task.getException());
                Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                showProgressBar(false);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Routing logic
    //   No doc yet              → create doc → role selection (MainActivity)
    //   Doc exists, no role     → role selection (MainActivity)
    //   Doc exists, role=doctor → DoctorDashboardActivity
    //   Doc exists, role=patient, registered=true  → PatientDashboardActivity
    //   Doc exists, role=patient, registered=false → PatientRegistrationActivity
    // ─────────────────────────────────────────────────────────────

    private void checkUserRoleAndNavigate(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get()
                .addOnCompleteListener(task -> {
                    showProgressBar(false);
                    if (task.isSuccessful()) {
                        DocumentSnapshot doc = task.getResult();
                        if (doc.exists()) {
                            String role = doc.getString("role");
                            if (role != null && !role.isEmpty()) {
                                navigateToDashboard(role, doc);
                            } else {
                                goToRoleSelection();
                            }
                        } else {
                            createNewUserInFirestore(user);
                        }
                    } else {
                        Log.w(TAG, "Error fetching user doc", task.getException());
                        Toast.makeText(this,
                                "Database error. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createNewUserInFirestore(FirebaseUser user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid",        user.getUid());
        userMap.put("name",       user.getDisplayName());
        userMap.put("email",      user.getEmail());
        userMap.put("photoUrl",   user.getPhotoUrl() != null
                ? user.getPhotoUrl().toString() : "");
        userMap.put("role",       "");
        userMap.put("registered", false);
        userMap.put("createdAt",  System.currentTimeMillis());

        db.collection("users").document(user.getUid()).set(userMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        goToRoleSelection();
                    } else {
                        Log.w(TAG, "Failed to create user doc", task.getException());
                        Toast.makeText(this,
                                "Failed to set up account. Try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToDashboard(String role, DocumentSnapshot doc) {
        Intent intent;
        if ("doctor".equals(role)) {
            intent = new Intent(this, DoctorDashboardActivity.class);
        } else {
            Boolean registered = doc.getBoolean("registered");
            if (registered != null && registered) {
                intent = new Intent(this, PatientDashboardActivity.class);
            } else {
                intent = new Intent(this, PatientRegistrationActivity.class);
            }
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToRoleSelection() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ─────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnGoogleSignIn.setEnabled(!show);
    }

    // ─────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────

    private void runEntranceAnimations() {
        // Glow orbs fade in slowly
        animateGlowOrb(glowOrbTop,    0f, 0.35f, 1200, 100);
        animateGlowOrb(glowOrbBottom, 0f, 0.20f, 1400, 300);

        // Breathing pulse starts after the top orb finishes fading
        if (glowOrbTop != null) {
            glowOrbTop.postDelayed(this::startOrbBreathingPulse, 1400);
        }

        // Content — staggered slide-up + fade-in
        View[] views  = {
                accentLine, ivLogoMark, tvAppName,
                tvTagline,  dividerBrand, cardSignIn, tvTerms
        };
        long[] delays = { 0L, 150L, 250L, 350L, 450L, 580L, 720L };

        for (int i = 0; i < views.length; i++) {
            if (views[i] == null) continue;
            views[i].setAlpha(0f);
            views[i].setTranslationY(48f);
            views[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(650)
                    .setStartDelay(delays[i])
                    .setInterpolator(new DecelerateInterpolator(2.2f))
                    .start();
        }
    }

    private void animateGlowOrb(View orb, float from, float to,
                                long duration, long delay) {
        if (orb == null) return;
        orb.setAlpha(from);
        orb.animate()
                .alpha(to)
                .setDuration(duration)
                .setStartDelay(delay)
                .start();
    }

    private void startOrbBreathingPulse() {
        if (glowOrbTop == null) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(glowOrbTop, "alpha", 0.25f, 0.45f);
        pulse.setDuration(2800);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    private void attachCardPressEffect(View card) {
        if (card == null) return;
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(new DecelerateInterpolator(1.5f))
                            .start();
                    break;
            }
            return false;
        });
    }
}