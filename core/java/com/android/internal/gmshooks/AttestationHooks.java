/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.gmshooks;

import android.app.Application;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.gmshooks.GmsInfo;

import java.lang.reflect.Field;

/** @hide */
public final class AttestationHooks {
    private static final String TAG = "GmsHooks/Attestation";

    private static final String PRODUCT_STOCK_FINGERPRINT =
            SystemProperties.get("ro.build.stock_fingerprint");

    private AttestationHooks() { }

    private static void setBuildField(String key, String value) {
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void spoofBuildGms() {
        // Set fingerprint for SafetyNet CTS profile
        if (PRODUCT_STOCK_FINGERPRINT.length() > 0) {
            setBuildField("FINGERPRINT", PRODUCT_STOCK_FINGERPRINT);
        }
    }

    public static void initApplicationBeforeOnCreate(Application app) {
        if (GmsInfo.PACKAGE_GMS.equals(app.getPackageName())) {
            spoofBuildGms();
        }
    }
}
