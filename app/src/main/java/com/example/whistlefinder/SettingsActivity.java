package com.example.whistlefinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private AutoCompleteTextView dropdownLanguage;

    private final String[] languages = {
            "🇺🇸 English", "🇹🇷 Türkçe", "🇩🇪 Deutsch", "🇪🇸 Español", 
            "🇫🇷 Français", "🇷🇺 Русский", "🇨🇳 中文", "🇯🇵 日本語", 
            "🇸🇦 العربية", "🇧🇷 Português", "🇮🇳 हिन्दी"
    };
    private final String[] langCodes = {"en", "tr", "de", "es", "fr", "ru", "zh", "ja", "ar", "pt", "hi"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("WhistleFinderPrefs", MODE_PRIVATE);
        String currentLang = prefs.getString("language", java.util.Locale.getDefault().getLanguage());
        setLocaleInActivity(currentLang);

        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        SeekBar seekbarSensitivity = findViewById(R.id.seekbar_sensitivity);
        dropdownLanguage = findViewById(R.id.dropdown_language);

        // Dropdown'u doldur
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, languages) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = languages;
                        results.count = languages.length;
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        dropdownLanguage.setAdapter(adapter);

        // Mevcut dili seçili göster
        String currentLangCode = prefs.getString("language", "en");
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(currentLangCode)) {
                // 'false' parametresi filtrelemeyi ve popup'ın açılmasını engeller
                dropdownLanguage.setText(languages[i], false);
                break;
            }
        }
        
        // Odaklanmayı temizle ki liste otomatik açılmasın
        dropdownLanguage.clearFocus();

        dropdownLanguage.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = (String) parent.getItemAtPosition(position);
            int realIndex = -1;
            for (int i = 0; i < languages.length; i++) {
                if (languages[i].equals(selectedItem)) {
                    realIndex = i;
                    break;
                }
            }

            if (realIndex != -1) {
                String selectedLangCode = langCodes[realIndex];
                if (!selectedLangCode.equals(prefs.getString("language", "en"))) {
                    dropdownLanguage.dismissDropDown();
                    setLocale(selectedLangCode);
                }
            }
        });

        int savedSensitivity = prefs.getInt("sensitivity", 50);
        seekbarSensitivity.setProgress(savedSensitivity);
        android.widget.TextView tvSensitivityValue = findViewById(R.id.tv_sensitivity_value);
        tvSensitivityValue.setText(savedSensitivity + "%");
        updateSeekBarColor(seekbarSensitivity, savedSensitivity);

        findViewById(R.id.btn_select_sound).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            startActivityForResult(intent, 200);
        });

        String savedSound = prefs.getString("alarm_sound_name", getString(R.string.default_sound));
        ((android.widget.TextView)findViewById(R.id.tv_selected_sound)).setText(savedSound);

        seekbarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("sensitivity", progress).apply();
                tvSensitivityValue.setText(progress + "%");
                updateSeekBarColor(seekBar, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // URI'yi kaydet
                prefs.edit().putString("alarm_sound", uri.toString()).apply();
                
                // Dosya adını alıp göster (opsiyonel ama şık durur)
                String fileName = getFileName(uri);
                prefs.edit().putString("alarm_sound_name", fileName).apply();
                ((android.widget.TextView)findViewById(R.id.tv_selected_sound)).setText(fileName);
                
                // Kalıcı izin al (servis için önemli)
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void updateSeekBarColor(SeekBar seekBar, int progress) {
        int green = 0xFF39FF14;
        int yellow = 0xFFFFFF00;
        int red = 0xFFFF3131;
        
        try {
            green = androidx.core.content.ContextCompat.getColor(this, R.color.neon_green);
            yellow = androidx.core.content.ContextCompat.getColor(this, R.color.neon_yellow);
            red = androidx.core.content.ContextCompat.getColor(this, R.color.neon_red);
        } catch (Exception ignored) {}

        android.animation.ArgbEvaluator evaluator = new android.animation.ArgbEvaluator();
        int color;
        if (progress <= 50) {
            float fraction = progress / 50f;
            color = (int) evaluator.evaluate(fraction, green, yellow);
        } else {
            float fraction = (progress - 50) / 50f;
            color = (int) evaluator.evaluate(fraction, yellow, red);
        }
        
        android.content.res.ColorStateList colorStateList = android.content.res.ColorStateList.valueOf(color);
        seekBar.setProgressTintList(colorStateList);
        seekBar.setThumbTintList(colorStateList);
        
        android.widget.TextView tvValue = findViewById(R.id.tv_sensitivity_value);
        if (tvValue != null) {
            tvValue.setTextColor(color);
        }
    }

    private void setLocale(String lang) {
        prefs.edit().putString("language", lang).apply();
        setLocaleInActivity(lang);
        
        // Sayfayı tertemiz yeniden başlatmak için
        Intent intent = getIntent();
        finish();
        startActivity(intent);
        overridePendingTransition(0, 0); // Ekran kırpışmasını engelle
    }

    private void setLocaleInActivity(String lang) {
        Locale locale;
        if (lang.contains("-")) {
            String[] parts = lang.split("-");
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(lang);
        }
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}