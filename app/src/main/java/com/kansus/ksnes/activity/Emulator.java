package com.kansus.ksnes.abstractemulator;

import android.view.SurfaceHolder;

import com.kansus.ksnes.abstractemulator.cheats.CheatsModule;
import com.kansus.ksnes.abstractemulator.input.InputModule;
import com.kansus.ksnes.abstractemulator.video.VideoModule;

import java.nio.Buffer;

public interface Emulator {

    // Interface para o listener de atualização de frames, que estava faltando
    interface FrameUpdateListener {
        void onFrameUpdate(int keys);
    }

    boolean loadROM(String file);
    void unloadROM();

    InputModule getInputModule();
    VideoModule getVideoModule();
    CheatsModule getCheatsModule();

    void setVideoFrameListener(VideoModule.VideoFrameListener listener);
    void setFrameUpdateListener(FrameUpdateListener listener);
    void setScreenFlipped(boolean flipped);
    void setInputModule(InputModule module);
    void setRenderingModule(VideoModule module);
    void setCheatsModule(CheatsModule module);

    void setOption(String name, boolean value);
    void setOption(String name, int value);
    void setOption(String name, String value);

    int getOption(String name);
    int getVideoWidth();
    int getVideoHeight();

    void reset();
    void power();
    void pause();
    void resume();

    void getScreenshot(Buffer buffer);
    boolean saveState(String file);
    boolean loadState(String file);

    void setSurface(SurfaceHolder surface);
    void setSurfaceRegion(int x, int y, int w, int h);
    void setKeyStates(int states);
    void processTrackball(int key1, int duration1, int key2, int duration2);
    void fireLightGun(int x, int y);
}
