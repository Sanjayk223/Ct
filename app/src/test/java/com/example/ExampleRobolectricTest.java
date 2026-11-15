package com.example;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {36})
public class ExampleRobolectricTest {
    @Test
    public void readStringFromContext() {
        Context context = ApplicationProvider.getApplicationContext();
        String appName = context.getString(R.string.app_name);
        assertEquals("VibeReel", appName);
    }
}
