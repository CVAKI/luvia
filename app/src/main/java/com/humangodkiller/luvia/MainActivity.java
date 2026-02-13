package com.humangodkiller.luvia;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private TextView tvWelcome, tvSelectRole;
    private CardView cardDoctor, cardPatient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize UI
        tvWelcome = findViewById(R.id.tv_welcome);
        tvSelectRole = findViewById(R.id.tv_select_role);
        cardDoctor = findViewById(R.id.card_doctor);
        cardPatient = findViewById(R.id.card_patient);

        // Check if user is logged in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User not logged in, redirect to login
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        } else {
            // Display user info
            updateUI(currentUser);
        }

        // Set up click listeners for role selection
        cardDoctor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to Doctor Dashboard
                Intent intent = new Intent(MainActivity.this, DoctorDashboardActivity.class);
                startActivity(intent);
            }
        });

        cardPatient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to Patient Dashboard
                Intent intent = new Intent(MainActivity.this, PatientDashboardActivity.class);
                startActivity(intent);
            }
        });
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            String welcomeText = "Welcome, " + user.getDisplayName() + "!";
            tvWelcome.setText(welcomeText);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sign_out) {
            signOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void signOut() {
        // Sign out from Firebase
        mAuth.signOut();

        // Sign out from Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Redirect to login screen
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}