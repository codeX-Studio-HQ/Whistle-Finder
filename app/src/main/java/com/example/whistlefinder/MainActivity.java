package com.example.whistlefinder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private MaterialSwitch switchService;
    private TextView tvStatus;
    private View layoutPermissionWarning;
    private Button btnGrantPermission;
    private SharedPreferences prefs;

    private View viewGlow;
    private View bgRed, bgGreen;
    private String currentLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("WhistleFinderPrefs", MODE_PRIVATE);
        
        // Sistem dilini al
        String systemLang = java.util.Locale.getDefault().getLanguage();
        // Eğer kullanıcı manuel dil seçmediyse sistem dilini kullan, yoksa seçtiği dili getir
        currentLanguage = prefs.getString("language", systemLang);

        // Desteklediğimiz diller listesi
        java.util.List<String> supportedLangs = java.util.Arrays.asList("en", "tr", "de", "es", "fr", "ru", "zh", "ja", "ar", "pt", "hi");
        if (!supportedLangs.contains(currentLanguage)) {
            currentLanguage = "en"; // Desteklenmeyen dilse İngilizce yap
        }

        setLocale(currentLanguage);

        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        switchService = findViewById(R.id.switch_service);
        tvStatus = findViewById(R.id.tv_status);
        layoutPermissionWarning = findViewById(R.id.layout_permission_warning);
        btnGrantPermission = findViewById(R.id.btn_grant_permission);
        viewGlow = findViewById(R.id.view_glow);
        bgRed = findViewById(R.id.bg_red);
        bgGreen = findViewById(R.id.bg_green);

        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // Servis çalışıyor mu kontrol et ve switch'i ayarla
        boolean isRunning = isServiceRunning(WhistleDetectionService.class);
        
        // Initial state set before listener to avoid redundant service calls
        updateUIState(isRunning);
        switchService.setChecked(isRunning);

        switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startWhistleService();
                updateStatusText(R.string.monitoring);
                viewGlow.setBackgroundResource(R.drawable.neon_glow_circle);
                viewGlow.animate().alpha(1.0f).setDuration(500).start();
                
                bgGreen.animate().alpha(1.0f).setDuration(800).start();
                bgRed.animate().alpha(0.0f).setDuration(800).start();
            } else {
                stopWhistleService();
                updateStatusText(R.string.service_stopped);
                viewGlow.setBackgroundResource(R.drawable.red_glow_circle);
                viewGlow.animate().alpha(0.3f).setDuration(500).start();
                
                bgRed.animate().alpha(1.0f).setDuration(800).start();
                bgGreen.animate().alpha(0.0f).setDuration(800).start();
            }
        });

        btnGrantPermission.setOnClickListener(v -> checkAndRequestPermissions());

        checkAndRequestPermissions();
        
        updateUIState(isRunning);
    }

    private void updateUIState(boolean isRunning) {
        if (isRunning) {
            bgGreen.setAlpha(1.0f);
            bgRed.setAlpha(0.0f);
            viewGlow.setAlpha(1.0f);
            viewGlow.setBackgroundResource(R.drawable.neon_glow_circle);
            tvStatus.setText(R.string.monitoring);
        } else {
            bgGreen.setAlpha(0.0f);
            bgRed.setAlpha(1.0f);
            viewGlow.setAlpha(0.3f);
            viewGlow.setBackgroundResource(R.drawable.red_glow_circle);
            tvStatus.setText(R.string.service_stopped);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateStatusText(int stringResId) {
        if (tvStatus == null) return;
        tvStatus.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    if (tvStatus != null) {
                        tvStatus.setText(stringResId);
                        tvStatus.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start();
                    }
                })
                .start();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        String lang = prefs.getString("language", java.util.Locale.getDefault().getLanguage());
        if (!lang.equals(currentLanguage)) {
            recreate();
        }
    }

    private void setLocale(String lang) {
        java.util.Locale locale;
        if (lang.contains("-")) {
            String[] parts = lang.split("-");
            locale = new java.util.Locale(parts[0], parts[1]);
        } else {
            locale = new java.util.Locale(lang);
        }
        java.util.Locale.setDefault(locale);
        android.content.res.Resources resources = getResources();
        android.content.res.Configuration config = new android.content.res.Configuration(resources.getConfiguration());
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (!permissionsNeeded.isEmpty()) {
            layoutPermissionWarning.setVisibility(View.VISIBLE);
            switchService.setEnabled(false);
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            layoutPermissionWarning.setVisibility(View.GONE);
            switchService.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                layoutPermissionWarning.setVisibility(View.GONE);
                switchService.setEnabled(true);
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWhistleService() {
        Intent intent = new Intent(this, WhistleDetectionService.class);
        intent.putExtra("sensitivity", prefs.getInt("sensitivity", 50));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopWhistleService() {
        stopService(new Intent(this, WhistleDetectionService.class));
    }
}