/*
 * Copyright (C) 2014 The Android Open Source Project
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

package wesker.yanqin.zodicclocklight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class ZodicLight extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(75);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<ZodicLight.Engine> mWeakReference;

        public EngineHandler(ZodicLight.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            ZodicLight.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        final ArrayList<Bitmap> flameImgs = new ArrayList<Bitmap>();
        final Bitmap backGround1 = BitmapFactory.decodeResource(getResources(), R.drawable.background2);
        final Bitmap backGround2 = BitmapFactory.decodeResource(getResources(), R.drawable.background1);
        final Paint watchPaint = new Paint();
        final RectF watchRectf = new RectF();
        final ArrayList<int[]> flameLocation = new ArrayList<>();
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        int index = 0;
        int secFraction = 0;
        int lastSec = -1;
        int secFill = 0;
        int minFill = 0;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(ZodicLight.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = ZodicLight.this.getResources();


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            watchRectf.set(0, 0, 320, 320);

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setAlpha(217);
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.SQUARE);
            mHandPaint.setStyle(Paint.Style.STROKE);
            mHandPaint.setTextSize(25);
            mTime = new Time();

            watchPaint.setAntiAlias(true);
            watchPaint.setFilterBitmap(true);
            watchPaint.setDither(true);
            TypedArray imgs = getResources().obtainTypedArray(R.array.flame);
            for (int i = 0; i < 25; i++) {
                int bitMapId = imgs.getResourceId(i, -1);
                if (bitMapId == -1)
                    throw new NullPointerException("Cannot find index:" + ((Integer) i).toString());
                flameImgs.add(BitmapFactory.decodeResource(getResources(), bitMapId));
                flameImgs.get(i).setDensity(Bitmap.DENSITY_NONE);
            }
            flameLocation.add(new int[]{128, -15});
            flameLocation.add(new int[]{68, 0});
            flameLocation.add(new int[]{23, 50});
            flameLocation.add(new int[]{3, 113});
            flameLocation.add(new int[]{23, 170});
            flameLocation.add(new int[]{68, 215});
            flameLocation.add(new int[]{128, 235});
            flameLocation.add(new int[]{188, 215});
            flameLocation.add(new int[]{238, 170});
            flameLocation.add(new int[]{253, 113});
            flameLocation.add(new int[]{238, 50});
            flameLocation.add(new int[]{188, 0});
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = ZodicLight.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            if (index == 24) {
                index = 0;
            } else {
                index++;
            }
            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;
            int minutes = mTime.minute;
            int sec = mTime.second;
            int hour = mTime.hour % 12;
            if (lastSec != sec) {
                lastSec = sec;
                secFraction = 0;
            }
            float secRot = ((float) sec / 60 * 360) + 0.45f * secFraction;
            float minRot = (float) minutes / 60 * 360;
            int mRadius = 103;
            int sRadius = 60;
            if (!mAmbient) {
                canvas.drawBitmap(backGround1, 0, 0, null);
                if (sec == 0) {
                    canvas.drawArc(centerX - sRadius, centerY - sRadius, centerX + sRadius, centerY + sRadius, -75 - secFill, secFill - secRot, false, mHandPaint);
                    secFill += 27;
                } else {
                    canvas.drawArc(centerX - sRadius, centerY - sRadius, centerX + sRadius, centerY + sRadius, -75, 360 - secRot, false, mHandPaint);
                    secFill = 0;
                }
                if (minutes == 0 && sec == 0){
                    canvas.drawArc(centerX - mRadius, centerY - mRadius, centerX + mRadius, centerY + mRadius, -75 - minFill, minFill - secRot, false, mHandPaint);
                    minFill += 27;
                }
                else {
                    canvas.drawArc(centerX - mRadius, centerY - mRadius, centerX + mRadius, centerY + mRadius, -75, 360 - minRot, false, mHandPaint);
                    minFill = 0;
                }
            } else {
                canvas.drawBitmap(backGround2, 0, 0, null);
                canvas.drawArc(centerX - mRadius, centerY - mRadius, centerX + mRadius, centerY + mRadius, -75, 360 - minRot, false, mHandPaint);
            }
            for (int i = hour; i < 12; i++) {
                canvas.drawBitmap(flameImgs.get(index), flameLocation.get(i)[0], flameLocation.get(i)[1], watchPaint);
            }
            secFraction = secFraction + 1;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ZodicLight.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ZodicLight.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
