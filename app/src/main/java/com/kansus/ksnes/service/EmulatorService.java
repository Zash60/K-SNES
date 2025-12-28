/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kansus.ksnes.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.ServiceCompat;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.kansus.ksnes.R;
import com.kansus.ksnes.activity.EmulatorActivity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This is an example of implementing an application service that can
 * run in the "foreground".  It shows how to code this to work well by using
 * the improved Android 2.0 APIs when available and otherwise falling back
 * to the original APIs.  Yes: you can take this exact code, compile it
 * against the Android 2.0 SDK, and it will against everything down to
 * Android 1.0.
 */
public class EmulatorService extends Service {
    
    public static final String ACTION_FOREGROUND = "com.kansus.actions.FOREGROUND";
    public static final String ACTION_BACKGROUND = "com.kansus.actions.BACKGROUND";

    private static final String LOG_TAG = "EmulatorService";

    private NotificationManager mNM;

    @Override
    public void onCreate() {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    // This is the old onStart method that will be called on the pre-2.0
    // platform.  On 2.0 or later we override onStartCommand() so this
    // method will not be called.
    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY_COMPATIBILITY;
    }

    void handleCommand(Intent intent) {
        if (ACTION_FOREGROUND.equals(intent.getAction())) {
            // In this sample, we'll use the same text for the ticker and the expanded notification
            CharSequence text = getText(R.string.emulator_service_running);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    this);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                    PendingIntent.FLAG_IMMUTABLE : 0;
            Notification notification = builder.setContentIntent(PendingIntent.getActivity(this, 0,
                    new Intent(this, EmulatorActivity.class), flags))
                    .setSmallIcon(R.drawable.app_icon).setTicker(text).setWhen(System.currentTimeMillis())
                    .setAutoCancel(true).setContentTitle(text)
                    .setContentText(text).build();

            // The PendingIntent to launch our activity if the user selects this notification
            // Set the info for the views that show in the notification panel.
            //notification.setLatestEventInfo(this, getText(R.string.app_label), text, contentIntent);

            startForegroundCompat(R.string.emulator_service_running, notification);

        } else if (ACTION_BACKGROUND.equals(intent.getAction())) {
            stopForegroundCompat(R.string.emulator_service_running);
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use ServiceCompat for modern Android versions
                ServiceCompat.startForeground(this, id, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_GAME);
            } else {
                // Fall back to direct call for older versions
                startForeground(id, notification);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Unable to start foreground service", e);
            // Fall back on the old API as last resort
            mNM.notify(id, notification);
        }
    }

    /**
     * This is a wrapper around the stopForeground method.
     */
    void stopForegroundCompat(int id) {
        try {
            stopForeground(true);
            mNM.cancel(id);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Unable to stop foreground service", e);
            mNM.cancel(id);
        }
    }

    @Override
    public void onDestroy() {
        // Make sure our notification is gone.
        stopForegroundCompat(R.string.emulator_service_running);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
