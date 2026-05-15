package com.example.whistlefinder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public class WhistleDetectionService extends Service {
    private static final String CHANNEL_ID = "WhistleDetectionChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int SAMPLE_RATE = 44100;
    private static final int MIN_FREQ = 1000;
    private static final int MAX_FREQ = 3500;

    private AudioRecord audioRecord;
    private Thread detectionThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private int sensitivity = 50;
    private CameraManager cameraManager;
    private String cameraId;
    
    private long lastDetectionTime = 0;
    private static final long DETECTION_COOLDOWN = 3000;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length > 0) cameraId = ids[0];
        } catch (CameraAccessException e) {
            Log.e("WhistleService", "Camera access error", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            sensitivity = intent.getIntExtra("sensitivity", 50);
        }

        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startDetection();
        return START_STICKY;
    }

    private void startDetection() {
        if (isRunning.get()) return;
        isRunning.set(true);

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        detectionThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            try {
                audioRecord.startRecording();
                while (isRunning.get()) {
                    int read = audioRecord.read(buffer, 0, bufferSize);
                    if (read > 0) {
                        double frequency = calculateDominantFrequency(buffer, read);
                        if (frequency >= MIN_FREQ && frequency <= MAX_FREQ) {
                            double amplitude = calculateAmplitude(buffer, read);
                            
                            // Hassasiyet optimize edildi
                            double threshold = (105 - sensitivity) * 60;
                            
                            if (amplitude > threshold) {
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastDetectionTime > DETECTION_COOLDOWN) {
                                    lastDetectionTime = currentTime;
                                    onWhistleDetected();
                                }
                            }
                        }
                    }
                }
                audioRecord.stop();
            } catch (Exception e) {
                Log.e("WhistleService", "Detection error", e);
            } finally {
                audioRecord.release();
            }
        });
        detectionThread.setPriority(Thread.MAX_PRIORITY);
        detectionThread.start();
    }

    private double calculateDominantFrequency(short[] buffer, int read) {
        int numZeroCrossings = 0;
        for (int i = 1; i < read; i++) {
            if ((buffer[i - 1] ^ buffer[i]) < 0) {
                numZeroCrossings++;
            }
        }
        return (double) numZeroCrossings * SAMPLE_RATE / (2.0 * read);
    }

    private double calculateAmplitude(short[] buffer, int read) {
        long sum = 0;
        for (int i = 0; i < read; i++) {
            sum += Math.abs(buffer[i]);
        }
        return (double) sum / read;
    }

    private void onWhistleDetected() {
        Log.d("WhistleService", "Whistle Detected!");
        playAlertSound();
        startStrobeFlash();
    }

    private void playAlertSound() {
        android.content.SharedPreferences prefs = getSharedPreferences("WhistleFinderPrefs", MODE_PRIVATE);
        String soundUriStr = prefs.getString("alarm_sound", null);
        
        if (soundUriStr != null) {
            try {
                Uri soundUri = Uri.parse(soundUriStr);
                // Kalıcı izin kontrolü ve MediaPlayer yüklemesi
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(getApplicationContext(), soundUri);
                mp.setAudioStreamType(android.media.AudioManager.STREAM_ALARM);
                mp.prepare();
                mp.start();
                mp.setOnCompletionListener(MediaPlayer::release);
                return; // Başarılıysa bip çalma
            } catch (Exception e) {
                Log.e("WhistleService", "Custom sound playback failed, falling back to beep", e);
            }
        }

        new Thread(() -> {
            android.media.ToneGenerator toneGen = new android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100);
            for (int i = 0; i < 3; i++) {
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            }
            toneGen.release();
        }).start();
    }

    private void startStrobeFlash() {
        final int flashCount = 10;
        final long delay = 100;
        android.os.Handler handler = new android.os.Handler(getMainLooper());

        for (int i = 0; i < flashCount; i++) {
            final boolean state = (i % 2 == 0);
            handler.postDelayed(() -> {
                try {
                    if (cameraId != null) cameraManager.setTorchMode(cameraId, state);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }, i * delay);
        }
        
        handler.postDelayed(() -> {
            try {
                if (cameraId != null) cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }, flashCount * delay);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Whistle Detection Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.monitoring))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        isRunning.set(false);
        if (detectionThread != null) {
            try {
                detectionThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}