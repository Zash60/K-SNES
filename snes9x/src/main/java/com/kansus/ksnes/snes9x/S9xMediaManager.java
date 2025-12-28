package com.kansus.ksnes.snes9x;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.view.SurfaceHolder;

import com.kansus.ksnes.abstractemulator.video.VideoModule;

@SuppressWarnings("unused")
class S9xMediaManager {

    private static SurfaceHolder holder;
    private static Rect region = new Rect();
    private static AudioTrack track;
    private static VideoModule.VideoFrameListener mVideoFrameListener;
    private static float volume = AudioTrack.getMaxVolume();

    static void destroy() {
        if (track != null) {
            track.stop();
            track = null;
        }
    }

    static void setOnFrameDrawnListener(VideoModule.VideoFrameListener frameDrawnListener) {
        mVideoFrameListener = frameDrawnListener;
    }

    static void setSurface(SurfaceHolder h) {
        holder = h;
    }

    static void setSurfaceRegion(int x, int y, int w, int h) {
        region.set(x, y, x + w, y + h);
    }

    static void bitBlt(int[] image, boolean flip) {
        // Fill background
        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(Color.BLACK);
        if (flip)
            canvas.rotate(180, canvas.getWidth() / 2, canvas.getHeight() / 2);

        canvas.drawBitmap(image, 0, region.width(), region.left, region.top,
                region.width(), region.height(), false, null);
        if (mVideoFrameListener != null)
            mVideoFrameListener.onFrameDrawn(canvas);

        holder.unlockCanvasAndPost(canvas);
    }

    static boolean audioCreate(int rate, int bits, int channels) {
        int format = (bits == 16 ?
                AudioFormat.ENCODING_PCM_16BIT :
                AudioFormat.ENCODING_PCM_8BIT);
        int channelConfig = (channels == 2 ?
                AudioFormat.CHANNEL_OUT_STEREO :
                AudioFormat.CHANNEL_OUT_MONO);

        // avoid recreation if no parameters change
        if (track != null &&
                track.getSampleRate() == rate &&
                track.getAudioFormat() == format &&
                track.getChannelCount() == channels)
            return true;

        int bufferSize = AudioTrack.getMinBufferSize(
                rate, channelConfig, format) * 2;
        if (bufferSize < 1500)
            bufferSize = 1500;

        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(format)
                    .setChannelMask(channelConfig)
                    .build();

            track = new AudioTrack(
                    audioAttributes,
                    audioFormat,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);

            if (track.getState() == AudioTrack.STATE_UNINITIALIZED)
                track = null;

        } catch (IllegalArgumentException e) {
            track = null;
        }
        if (track == null)
            return false;

        track.setVolume(volume);
        return true;
    }

    static void audioSetVolume(int vol) {
        final float min = AudioTrack.getMinVolume();
        final float max = AudioTrack.getMaxVolume();
        volume = min + (max - min) * vol / 100;

        if (track != null)
            track.setVolume(volume);
    }

    static void audioDestroy() {
        if (track != null) {
            track.stop();
            track = null;
        }
    }

    static void audioStart() {
        if (track != null)
            track.play();
    }

    static void audioStop() {
        if (track != null) {
            track.stop();
            track.flush();
        }
    }

    static void audioPause() {
        if (track != null)
            track.pause();
    }

    static void audioPlay(byte[] data, int size) {
        if (track != null)
            track.write(data, 0, size);
    }
}
