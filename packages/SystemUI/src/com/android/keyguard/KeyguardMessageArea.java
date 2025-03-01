/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.keyguard;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.lang.ref.WeakReference;

import javax.inject.Inject;
import javax.inject.Named;

/***
 * Manages a number of views inside of the given layout. See below for a list of widgets.
 */
public class KeyguardMessageArea extends TextView implements SecurityMessageDisplay,
        ConfigurationController.ConfigurationListener {
    /** Handler token posted with accessibility announcement runnables. */
    private static final Object ANNOUNCE_TOKEN = new Object();

    /**
     * Delay before speaking an accessibility announcement. Used to prevent
     * lift-to-type from interrupting itself.
     */
    private static final long ANNOUNCEMENT_DELAY = 250;
    private static final int DEFAULT_COLOR = -1;

    private final Handler mHandler;
    private final ConfigurationController mConfigurationController;

    private ColorStateList mDefaultColorState;
    private CharSequence mMessage;
    private ColorStateList mNextMessageColorState = ColorStateList.valueOf(DEFAULT_COLOR);
    private boolean mBouncerVisible;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {
        public void onFinishedGoingToSleep(int why) {
            setSelected(false);
        }

        public void onStartedWakingUp() {
            setSelected(true);
        }

        @Override
        public void onKeyguardBouncerChanged(boolean bouncer) {
            mBouncerVisible = bouncer;
            update();
        }
    };

    public KeyguardMessageArea(Context context) {
        super(context, null);
        throw new IllegalStateException("This constructor should never be invoked");
    }

    @Inject
    public KeyguardMessageArea(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            ConfigurationController configurationController) {
        this(context, attrs, Dependency.get(KeyguardUpdateMonitor.class), configurationController);
    }

    public KeyguardMessageArea(Context context, AttributeSet attrs, KeyguardUpdateMonitor monitor,
            ConfigurationController configurationController) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null); // work around nested unclipped SaveLayer bug

        monitor.registerCallback(mInfoCallback);
        mHandler = new Handler(Looper.myLooper());
        mConfigurationController = configurationController;
        onThemeChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mConfigurationController.addCallback(this);
        onUiModeChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mConfigurationController.removeCallback(this);
    }

    @Override
    public void setNextMessageColor(ColorStateList colorState) {
        mNextMessageColorState = colorState;
    }

    @Override
    public void onUiModeChanged() {
        TypedArray array = mContext.obtainStyledAttributes(new int[] {
                android.R.attr.textColorPrimary
        });
        ColorStateList newTextColors = ColorStateList.valueOf(array.getColor(0, Color.RED));
        array.recycle();
        mDefaultColorState = newTextColors;
        update();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        TypedArray array = mContext.obtainStyledAttributes(R.style.Keyguard_TextView, new int[] {
                android.R.attr.textSize
        });
        setTextSize(TypedValue.COMPLEX_UNIT_PX, array.getDimensionPixelSize(0, 0));
        array.recycle();
    }

    @Override
    public void setMessage(CharSequence msg) {
        if (!TextUtils.isEmpty(msg)) {
            securityMessageChanged(msg);
        } else {
            clearMessage();
        }
    }

    @Override
    public void setMessage(int resId) {
        CharSequence message = null;
        if (resId != 0) {
            message = getContext().getResources().getText(resId);
        }
        setMessage(message);
    }

    @Override
    public void formatMessage(int resId, Object... formatArgs) {
        CharSequence message = null;
        if (resId != 0) {
            message = getContext().getString(resId, formatArgs);
        }
        setMessage(message);
    }

    public static KeyguardMessageArea findSecurityMessageDisplay(View v) {
        KeyguardMessageArea messageArea = v.findViewById(R.id.keyguard_message_area);
        if (messageArea == null) {
            messageArea = v.getRootView().findViewById(R.id.keyguard_message_area);
        }
        if (messageArea == null) {
            throw new RuntimeException("Can't find keyguard_message_area in " + v.getClass());
        }
        return messageArea;
    }

    @Override
    protected void onFinishInflate() {
        boolean shouldMarquee = Dependency.get(KeyguardUpdateMonitor.class).isDeviceInteractive();
        setSelected(shouldMarquee); // This is required to ensure marquee works
    }

    private void securityMessageChanged(CharSequence message) {
        mMessage = message;
        update();
        mHandler.removeCallbacksAndMessages(ANNOUNCE_TOKEN);
        mHandler.postAtTime(new AnnounceRunnable(this, getText()), ANNOUNCE_TOKEN,
                (SystemClock.uptimeMillis() + ANNOUNCEMENT_DELAY));
    }

    private void clearMessage() {
        mMessage = null;
        update();
    }

    private void update() {
        CharSequence status = mMessage;
        setVisibility(TextUtils.isEmpty(status) || !mBouncerVisible ? INVISIBLE : VISIBLE);
        setText(status);
        ColorStateList colorState = mDefaultColorState;
        if (mNextMessageColorState.getDefaultColor() != DEFAULT_COLOR) {
            colorState = mNextMessageColorState;
            mNextMessageColorState = ColorStateList.valueOf(DEFAULT_COLOR);
        }
        setTextColor(colorState);
    }


    /**
     * Runnable used to delay accessibility announcements.
     */
    private static class AnnounceRunnable implements Runnable {
        private final WeakReference<View> mHost;
        private final CharSequence mTextToAnnounce;

        AnnounceRunnable(View host, CharSequence textToAnnounce) {
            mHost = new WeakReference<View>(host);
            mTextToAnnounce = textToAnnounce;
        }

        @Override
        public void run() {
            final View host = mHost.get();
            if (host != null) {
                host.announceForAccessibility(mTextToAnnounce);
            }
        }
    }
}
