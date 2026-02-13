package com.humangodkiller.luvia;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3000; // 3 seconds
    private FirebaseAuth mAuth;

    private ImageView ivLogo;
    private TextView tvPoweredBy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        ivLogo = findViewById(R.id.iv_logo);
        tvPoweredBy = findViewById(R.id.tv_powered_by);

        // Set initial visibility
        ivLogo.setAlpha(0f);
        ivLogo.setScaleX(0.3f);
        ivLogo.setScaleY(0.3f);
        tvPoweredBy.setAlpha(0f);
        tvPoweredBy.setTranslationY(50f);

        // Apply gradient to "Powered by cvaki" text
        applyGradientToText(tvPoweredBy);

        // Start animations
        startAnimations();

        // Delay and navigate
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                navigateToNextScreen();
            }
        }, SPLASH_DELAY);
    }

    private void startAnimations() {
        // Logo animations
        ObjectAnimator logoFadeIn = ObjectAnimator.ofFloat(ivLogo, "alpha", 0f, 1f);
        logoFadeIn.setDuration(800);

        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(ivLogo, "scaleX", 0.3f, 1f);
        logoScaleX.setDuration(800);
        logoScaleX.setInterpolator(new OvershootInterpolator(1.5f));

        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(ivLogo, "scaleY", 0.3f, 1f);
        logoScaleY.setDuration(800);
        logoScaleY.setInterpolator(new OvershootInterpolator(1.5f));

        // Subtle rotation for extra flair
        ObjectAnimator logoRotate = ObjectAnimator.ofFloat(ivLogo, "rotation", -5f, 0f);
        logoRotate.setDuration(800);
        logoRotate.setInterpolator(new AccelerateDecelerateInterpolator());

        // Logo animation set
        AnimatorSet logoAnimSet = new AnimatorSet();
        logoAnimSet.playTogether(logoFadeIn, logoScaleX, logoScaleY, logoRotate);
        logoAnimSet.setStartDelay(200);

        // Powered by text animations
        ObjectAnimator textFadeIn = ObjectAnimator.ofFloat(tvPoweredBy, "alpha", 0f, 1f);
        textFadeIn.setDuration(600);

        ObjectAnimator textSlideUp = ObjectAnimator.ofFloat(tvPoweredBy, "translationY", 50f, 0f);
        textSlideUp.setDuration(600);
        textSlideUp.setInterpolator(new AccelerateDecelerateInterpolator());

        // Text animation set
        AnimatorSet textAnimSet = new AnimatorSet();
        textAnimSet.playTogether(textFadeIn, textSlideUp);
        textAnimSet.setStartDelay(800);

        // Start all animations
        logoAnimSet.start();
        textAnimSet.start();
    }

    private void applyGradientToText(TextView textView) {
        int[] colors = {
                0xFFD42221, // #D42221
                0xFFFD6B26, // #FD6B26
                0xFFFDD92D  // #FDD92D
        };

        textView.post(new Runnable() {
            @Override
            public void run() {
                float width = textView.getPaint().measureText(textView.getText().toString());
                Shader shader = new LinearGradient(
                        0, 0, width, 0,
                        colors,
                        null,
                        Shader.TileMode.CLAMP
                );
                textView.getPaint().setShader(shader);
                textView.invalidate();
            }
        });
    }

    private void navigateToNextScreen() {
        // Fade out animation before navigating
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(findViewById(R.id.splash_container), "alpha", 1f, 0f);
        fadeOut.setDuration(400);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                FirebaseUser currentUser = mAuth.getCurrentUser();

                Intent intent;
                if (currentUser != null) {
                    // User is logged in, go to MainActivity
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                } else {
                    // User is not logged in, go to LoginActivity
                    intent = new Intent(SplashActivity.this, LoginActivity.class);
                }

                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
        fadeOut.start();
    }
}