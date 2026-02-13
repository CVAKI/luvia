package com.humangodkiller.luvia;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseFirestore db;

    private Button btnGoogleSignIn;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);
        progressBar = findViewById(R.id.progress_bar);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleSignIn.setOnClickListener(v -> signIn());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Already logged in? Check role and route immediately
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            showProgressBar(true);
            checkUserRoleAndNavigate(currentUser);
        }
    }

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
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    /**
     * Routing logic:
     * - No doc yet              → create doc → role selection (MainActivity)
     * - Doc exists, no role     → role selection (MainActivity)
     * - Doc exists, role=doctor → DoctorDashboardActivity
     * - Doc exists, role=patient, registered=true  → PatientDashboardActivity
     * - Doc exists, role=patient, registered=false → PatientRegistrationActivity
     */
    private void checkUserRoleAndNavigate(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get()
                .addOnCompleteListener(task -> {
                    showProgressBar(false);
                    if (task.isSuccessful()) {
                        DocumentSnapshot doc = task.getResult();
                        if (doc.exists()) {
                            String role = doc.getString("role");
                            if (role != null && !role.isEmpty()) {
                                // Role already selected — route to correct destination
                                navigateToDashboard(role, doc);
                            } else {
                                // Logged in before but never picked a role
                                goToRoleSelection();
                            }
                        } else {
                            // First time logging in — create Firestore document
                            createNewUserInFirestore(user);
                        }
                    } else {
                        Log.w(TAG, "Error fetching user doc", task.getException());
                        Toast.makeText(this, "Database error. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createNewUserInFirestore(FirebaseUser user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", user.getUid());
        userMap.put("name", user.getDisplayName());
        userMap.put("email", user.getEmail());
        userMap.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        userMap.put("role", "");        // Will be set when user picks a role
        userMap.put("registered", false); // Will be true after PatientRegistrationActivity
        userMap.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(user.getUid()).set(userMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        goToRoleSelection();
                    } else {
                        Log.w(TAG, "Failed to create user doc", task.getException());
                        Toast.makeText(this, "Failed to set up account. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Doctors go straight to their dashboard.
     * Patients are checked for completed registration first.
     */
    private void navigateToDashboard(String role, DocumentSnapshot doc) {
        Intent intent;

        if ("doctor".equals(role)) {
            // Doctors have no registration form
            intent = new Intent(this, DoctorDashboardActivity.class);
        } else {
            // Patient: check if they already completed the registration form
            Boolean registered = doc.getBoolean("registered");
            if (registered != null && registered) {
                // Profile already filled in — go straight to dashboard
                intent = new Intent(this, PatientDashboardActivity.class);
            } else {
                // Profile not yet filled in — go to registration form
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

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnGoogleSignIn.setEnabled(!show);
    }
}