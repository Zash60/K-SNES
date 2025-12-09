package com.kansus.ksnes.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.kansus.ksnes.DefaultVideoModule;
import com.kansus.ksnes.KSNESApplication;
import com.kansus.ksnes.R;
import com.kansus.ksnes.abstractemulator.Emulator;
import com.kansus.ksnes.abstractemulator.input.InputModule;
import com.kansus.ksnes.abstractemulator.video.VideoModule;
import com.kansus.ksnes.dagger.DaggerDgEmulatorComponent;
import com.kansus.ksnes.dagger.DgActivityModule;
import com.kansus.ksnes.dagger.DgEmulatorComponent;
import com.kansus.ksnes.dagger.DgEmulatorModule;
import com.kansus.ksnes.media.MediaScanner;
import com.kansus.ksnes.service.EmulatorService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

public class EmulatorActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOG_TAG = "K-SNES";

    private static final String SCREENSHOTS_FOLDER = Environment
            .getExternalStorageDirectory().getPath() + "/K-SNES Screenshots";

    private static final int REQUEST_LOAD_STATE = 1;
    private static final int REQUEST_SAVE_STATE = 2;

    private static final int DIALOG_QUIT_GAME = 1;
    private static final int DIALOG_REPLACE_GAME = 2;

    @Inject
    Emulator mEmulator;

    private VideoModule mVideoModule;

    private int quickLoadKey;
    private int quickSaveKey;
    private int fastForwardKey;
    private int screenshotKey;

    private boolean inFastForward;
    private float fastForwardSpeed;

    private SharedPreferences sharedPrefs;
    private Intent newIntent;
    private MediaScanner mediaScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            finish();
            return;
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);

        DgEmulatorComponent component = DaggerDgEmulatorComponent.builder()
                .dgApplicationComponent(((KSNESApplication) getApplication()).getApplicationComponent())
                .dgActivityModule(new DgActivityModule(this))
                .dgEmulatorModule(new DgEmulatorModule())
                .build();
        component.inject(this);

        setContentView(R.layout.emulator);

        setupEmulatorView();
        mVideoModule = mEmulator.getVideoModule();

        final String[] prefKeys = {
                "fullScreenMode", "flipScreen", "fastForwardSpeed",
                "frameSkipMode", "maxFrameSkips", "refreshRate",
                "enableLightGun", "enableGamepad2", "soundEnabled",
                "soundVolume", "transparencyEnabled", "enableHiRes",
                "enableTrackball", "trackballSensitivity", "useSensor",
                "sensorSensitivity", "enableVKeypad", "scalingMode",
                "aspectRatio", "enableCheats", "orientation",
                "useInputMethod", "quickLoad", "quickSave",
                "fastForward", "screenshot",
        };

        for (String key : prefKeys) {
            onSharedPreferenceChanged(sharedPrefs, key);
        }

        mEmulator.getInputModule().loadKeyBindings(sharedPrefs);
        mEmulator.setOption("enableSRAM", true);
        mEmulator.setOption("apuEnabled", sharedPrefs.getBoolean("apuEnabled", true));

        if (!loadROM()) {
            finish();
            return;
        }
        startService(new Intent(this, EmulatorService.class).setAction(EmulatorService.ACTION_FOREGROUND));
    }

    private void setupEmulatorView() {
        if (mEmulator.getVideoModule() instanceof View) {
            LinearLayout myLayout = findViewById(R.id.main);
            View emulatorView = (View) mEmulator.getVideoModule();
            emulatorView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            myLayout.addView(emulatorView);
            emulatorView.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mEmulator != null) {
            mEmulator.unloadROM();
        }
        stopService(new Intent(this, EmulatorService.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseEmulator();
        mEmulator.getInputModule().disable();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEmulator.getInputModule().enable();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setFlipScreen(sharedPrefs, newConfig);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            mEmulator.getInputModule().reset();
            mEmulator.setKeyStates(0);
            resumeEmulator();
        } else {
            pauseEmulator();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (!Intent.ACTION_VIEW.equals(intent.getAction()))
            return;
        newIntent = intent;
        pauseEmulator();
        showDialog(DIALOG_REPLACE_GAME);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_QUIT_GAME:
                return createQuitGameDialog();
            case DIALOG_REPLACE_GAME:
                return createReplaceGameDialog();
        }
        return super.onCreateDialog(id);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == quickLoadKey) {
            quickLoad();
            return true;
        }
        if (keyCode == quickSaveKey) {
            quickSave();
            return true;
        }
        if (keyCode == fastForwardKey) {
            onFastForward();
            return true;
        }
        if (keyCode == screenshotKey) {
            onScreenshot();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_SEARCH)
            return true;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            pauseEmulator();
            showDialog(DIALOG_QUIT_GAME);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.emulator, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        pauseEmulator();
        menu.findItem(R.id.menu_cheats).setEnabled(mEmulator.getCheatsModule() != null);
        int fastForwardTitle = inFastForward ? R.string.no_fast_forward : R.string.fast_forward;
        menu.findItem(R.id.menu_fast_forward).setTitle(fastForwardTitle);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, EmulatorSettings.class));
            return true;
        } else if (id == R.id.menu_reset) {
            mEmulator.reset();
            return true;
        } else if (id == R.id.menu_fast_forward) {
            onFastForward();
            return true;
        } else if (id == R.id.menu_screenshot) {
            onScreenshot();
            return true;
        } else if (id == R.id.menu_cheats) {
            startActivity(new Intent(this, CheatsActivity.class));
            return true;
        } else if (id == R.id.menu_save_state) {
            onSaveState();
            return true;
        } else if (id == R.id.menu_load_state) {
            onLoadState();
            return true;
        } else if (id == R.id.menu_close) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result != RESULT_OK) {
            return;
        }
        switch (request) {
            case REQUEST_LOAD_STATE:
                if (data != null && data.getData() != null)
                    loadState(data.getData().getPath());
                break;
            case REQUEST_SAVE_STATE:
                if (data != null && data.getData() != null)
                    saveState(data.getData().getPath());
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.startsWith("gamepad")) {
            mEmulator.getInputModule().loadKeyBindings(prefs);
        } else if ("enableLightGun".equals(key)) {
            boolean lightGunEnabled = prefs.getBoolean(key, false);
            mEmulator.getInputModule().setLightGunEnabled(lightGunEnabled);
            mEmulator.setOption(key, lightGunEnabled);
        } else if ("enableTrackball".equals(key)) {
            boolean trackballEnabled = prefs.getBoolean(key, true);
            InputModule inputModule = mEmulator.getInputModule();
            inputModule.setTrackballEnabled(trackballEnabled);
            mVideoModule.setOnTrackballListener(trackballEnabled ? inputModule : null);
        } else if ("trackballSensitivity".equals(key)) {
            int trackballSensitivity = prefs.getInt(key, 2) * 5 + 10;
            mEmulator.getInputModule().setTrackballSensitivity(trackballSensitivity);
        } else if ("useSensor".equals(key)) {
            String sensorType = prefs.getString(key, "none");
            boolean useSensor = !"none".equals(sensorType);
            mEmulator.getInputModule().setSensorInputSourceEnabled(useSensor);
            if (useSensor) {
                mEmulator.getInputModule().getInputSensor().setSensitivity(prefs.getInt("sensorSensitivity", 7));
            }
        } else if ("sensorSensitivity".equals(key)) {
            if (mEmulator.getInputModule().getInputSensor() != null) {
                mEmulator.getInputModule().getInputSensor().setSensitivity(prefs.getInt(key, 7));
            }
        } else if ("enableVKeypad".equals(key)) {
            boolean enableVirtualKeypad = prefs.getBoolean(key, true);
            mEmulator.getInputModule().setTouchInputSourceEnabled(enableVirtualKeypad);
        } else if ("fullScreenMode".equals(key)) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            if (prefs.getBoolean("fullScreenMode", true))
                attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            else
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
            getWindow().setAttributes(attrs);
        } else if ("flipScreen".equals(key)) {
            setFlipScreen(prefs, getResources().getConfiguration());
        } else if ("fastForwardSpeed".equals(key)) {
            String value = prefs.getString(key, "2x");
            fastForwardSpeed = Float.parseFloat(value.substring(0, value.length() - 1));
            if (inFastForward)
                setGameSpeed(fastForwardSpeed);
        } else if ("frameSkipMode".equals(key)) {
            mEmulator.setOption(key, prefs.getString(key, "auto"));
        } else if ("maxFrameSkips".equals(key)) {
            mEmulator.setOption(key, Integer.toString(prefs.getInt(key, 2)));
        } else if ("refreshRate".equals(key)) {
            mEmulator.setOption(key, prefs.getString(key, "default"));
        } else if ("enableGamepad2".equals(key)) {
            mEmulator.setOption(key, prefs.getBoolean(key, false));
        } else if ("soundEnabled".equals(key)) {
            mEmulator.setOption(key, prefs.getBoolean(key, true));
        } else if ("soundVolume".equals(key)) {
            mEmulator.setOption(key, prefs.getInt(key, 100));
        } else if ("transparencyEnabled".equals(key)) {
            mEmulator.setOption(key, prefs.getBoolean(key, false));
        } else if ("enableHiRes".equals(key)) {
            mEmulator.setOption(key, prefs.getBoolean(key, true));
        } else if ("scalingMode".equals(key)) {
            mVideoModule.setScalingMode(getScalingMode(prefs.getString(key, "proportional")));
        } else if ("aspectRatio".equals(key)) {
            float ratio = Float.parseFloat(prefs.getString(key, "1.3333"));
            mVideoModule.setAspectRatio(ratio);
        } else if ("enableCheats".equals(key)) {
            boolean enable = prefs.getBoolean(key, true);
            if (mEmulator.getCheatsModule() != null) {
                if (enable) {
                    mEmulator.getCheatsModule().enable();
                } else {
                    mEmulator.getCheatsModule().disable();
                }
            }
        } else if ("orientation".equals(key)) {
            String orientation = prefs.getString(key, "unspecified");
            switch (orientation) {
                case "landscape":
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    break;
                case "portrait":
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    break;
                default:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    break;
            }
        } else if ("useInputMethod".equals(key)) {
            getWindow().setFlags(prefs.getBoolean(key, false) ?
                            0 : WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        } else if ("quickLoad".equals(key)) {
            quickLoadKey = prefs.getInt(key, 0);
        } else if ("quickSave".equals(key)) {
            quickSaveKey = prefs.getInt(key, 0);
        } else if ("fastForward".equals(key)) {
            fastForwardKey = prefs.getInt(key, 0);
        } else if ("screenshot".equals(key)) {
            screenshotKey = prefs.getInt(key, 0);
        }
    }

    private void pauseEmulator() {
        mEmulator.pause();
    }

    private void resumeEmulator() {
        if (hasWindowFocus()) {
            mEmulator.resume();
        }
    }

    private void setFlipScreen(SharedPreferences prefs, Configuration config) {
        boolean flipScreen = false;
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            flipScreen = prefs.getBoolean("flipScreen", false);
        }
        mEmulator.setScreenFlipped(flipScreen);
        mEmulator.setOption("flipScreen", flipScreen);
    }

    private static int getScalingMode(String mode) {
        if ("original".equals(mode))
            return DefaultVideoModule.SCALING_ORIGINAL;
        if ("2x".equals(mode))
            return DefaultVideoModule.SCALING_2X;
        if ("proportional".equals(mode))
            return DefaultVideoModule.SCALING_PROPORTIONAL;
        return DefaultVideoModule.SCALING_STRETCH;
    }

    private Dialog createQuitGameDialog() {
        DialogInterface.OnClickListener l = (dialog, which) -> {
            switch (which) {
                case 1:
                    quickSave();
                    // fall through
                case 2:
                    finish();
                    break;
            }
        };
        return new AlertDialog.Builder(this)
                .setTitle(R.string.quit_game_title)
                .setItems(R.array.exit_game_options, l)
                .create();
    }

    private Dialog createReplaceGameDialog() {
        DialogInterface.OnClickListener l = (dialog, which) -> {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                setIntent(newIntent);
                loadROM();
            }
            newIntent = null;
        };
        return new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.replace_game_title)
                .setMessage(R.string.replace_game_message)
                .setPositiveButton(android.R.string.yes, l)
                .setNegativeButton(android.R.string.no, l)
                .create();
    }

    private String getROMFilePath() {
        if (getIntent() == null || getIntent().getData() == null) {
            return null;
        }
        return getIntent().getData().getPath();
    }

    private boolean isROMSupported(String file) {
        if (file == null) return false;
        file = file.toLowerCase();
        String[] filters = getResources().getStringArray(R.array.file_chooser_filters);
        for (String f : filters) {
            if (file.endsWith(f))
                return true;
        }
        return false;
    }

    private boolean loadROM() {
        String path = getROMFilePath();
        if (path == null) {
            Toast.makeText(this, "Invalid ROM path.", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        if (!isROMSupported(path)) {
            Toast.makeText(this, R.string.rom_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        if (!mEmulator.loadROM(path)) {
            Toast.makeText(this, R.string.load_rom_failed, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        inFastForward = false;
        mVideoModule.setActualSize(mEmulator.getVideoWidth(), mEmulator.getVideoHeight());
        if (sharedPrefs.getBoolean("quickLoadOnStart", true))
            quickLoad();
        return true;
    }

    private void onLoadState() {
        Intent intent = new Intent(this, StateSlotsActivity.class);
        intent.setData(getIntent().getData());
        startActivityForResult(intent, REQUEST_LOAD_STATE);
    }

    private void onSaveState() {
        Intent intent = new Intent(this, StateSlotsActivity.class);
        intent.setData(getIntent().getData());
        intent.putExtra(StateSlotsActivity.EXTRA_SAVE_MODE, true);
        startActivityForResult(intent, REQUEST_SAVE_STATE);
    }

    private void setGameSpeed(float speed) {
        pauseEmulator();
        mEmulator.setOption("gameSpeed", Float.toString(speed));
        resumeEmulator();
    }



    private void onFastForward() {
        inFastForward = !inFastForward;
        setGameSpeed(inFastForward ? fastForwardSpeed : 1.0f);
    }

    @SuppressLint("SetWorldReadable")
    private void onScreenshot() {
        File dir = new File(SCREENSHOTS_FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(LOG_TAG, "Could not create directory for screenshots");
            return;
        }
        String name = System.currentTimeMillis() + ".png";
        File file = new File(dir, name);
        pauseEmulator();
        try (FileOutputStream out = new FileOutputStream(file)) {
            Bitmap bitmap = getScreenshot();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            bitmap.recycle();
            Toast.makeText(this, R.string.screenshot_saved, Toast.LENGTH_SHORT).show();
            if (mediaScanner == null)
                mediaScanner = new MediaScanner(this);
            mediaScanner.scanFile(file.getAbsolutePath(), "image/png");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to save screenshot", e);
        }
        resumeEmulator();
    }

    private void saveState(String fileName) {
        pauseEmulator();
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)))) {
            out.putNextEntry(new ZipEntry("screenshot.png"));
            Bitmap bitmap = getScreenshot();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to save screenshot in state file", e);
        }
        mEmulator.saveState(fileName);
        resumeEmulator();
    }

    private void loadState(String fileName) {
        File file = new File(fileName);
        if (!file.exists())
            return;
        pauseEmulator();
        mEmulator.loadState(fileName);
        resumeEmulator();
    }

    private Bitmap getScreenshot() {
        final int w = mEmulator.getVideoWidth();
        final int h = mEmulator.getVideoHeight();
        if (w <= 0 || h <= 0) return null; // Safety check
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 2);
        mEmulator.getScreenshot(buffer);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private String getQuickSlotFileName() {
        return StateSlotsActivity.getSlotFileName(getROMFilePath(), 0);
    }

    private void quickSave() {
        String path = getQuickSlotFileName();
        if (path != null) {
            saveState(path);
        }
    }

    private void quickLoad() {
        String path = getQuickSlotFileName();
        if (path != null) {
            loadState(path);
        }
    }
}
