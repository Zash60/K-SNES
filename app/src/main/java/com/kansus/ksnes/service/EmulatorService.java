package com.kansus.ksnes.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
// CORREÇÃO: Import correto do AndroidX
import androidx.core.app.NotificationCompat; 

import com.kansus.ksnes.R;
import com.kansus.ksnes.activity.EmulatorActivity;

/**
 * Service to keep the emulator running in the foreground.
 * Updated for AndroidX and Android 14 compliance.
 */
public class EmulatorService extends Service {
    
    public static final String ACTION_FOREGROUND = "com.kansus.actions.FOREGROUND";
    public static final String ACTION_BACKGROUND = "com.kansus.actions.BACKGROUND";

    private static final String CHANNEL_ID = "ksnes_emulator_channel";
    // Usando um ID fixo > 0 para a notificação
    private static final int NOTIFICATION_ID = 1337; 

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    /**
     * Creates the NotificationChannel, required for Android 8.0+ (API 26+).
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_label);
            String description = getString(R.string.emulator_service_running);
            int importance = NotificationManager.IMPORTANCE_LOW;
            
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_FOREGROUND.equals(intent.getAction())) {
                startForegroundServiceCompat();
            } else if (ACTION_BACKGROUND.equals(intent.getAction())) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startForegroundServiceCompat() {
        CharSequence text = getString(R.string.emulator_service_running);

        // Android 12+ requires explicit mutability flag
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 
                0,
                new Intent(this, EmulatorActivity.class), 
                pendingIntentFlags
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(getString(R.string.app_label))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            // Em versões muito novas do Android, startForeground pode falhar se o app estiver em background
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
