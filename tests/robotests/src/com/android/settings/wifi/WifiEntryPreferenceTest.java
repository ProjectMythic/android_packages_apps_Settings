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
package com.android.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.R;
import com.android.settingslib.wifi.WifiUtils;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class WifiEntryPreferenceTest {

    private Context mContext;

    @Mock
    private WifiEntry mMockWifiEntry;
    @Mock
    private WifiUtils.InternetIconInjector mMockIconInjector;

    @Mock
    private Drawable mMockDrawable0;
    @Mock
    private Drawable mMockDrawable1;
    @Mock
    private Drawable mMockDrawable2;
    @Mock
    private Drawable mMockDrawable3;
    @Mock
    private Drawable mMockDrawable4;

    @Mock
    private Drawable mMockShowXDrawable0;
    @Mock
    private Drawable mMockShowXDrawable1;
    @Mock
    private Drawable mMockShowXDrawable2;
    @Mock
    private Drawable mMockShowXDrawable3;
    @Mock
    private Drawable mMockShowXDrawable4;

    private static final String MOCK_TITLE = "title";
    private static final String MOCK_SUMMARY = "summary";
    private static final String FAKE_URI_STRING = "fakeuri";

    WifiEntryPreference mPref;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;

        MockitoAnnotations.initMocks(this);

        when(mMockWifiEntry.getTitle()).thenReturn(MOCK_TITLE);
        when(mMockWifiEntry.getSummary(false /* concise */)).thenReturn(MOCK_SUMMARY);

        when(mMockIconInjector.getIcon(false /* showX */, 0)).thenReturn(mMockDrawable0);
        when(mMockIconInjector.getIcon(false /* showX */, 1)).thenReturn(mMockDrawable1);
        when(mMockIconInjector.getIcon(false /* showX */, 2)).thenReturn(mMockDrawable2);
        when(mMockIconInjector.getIcon(false /* showX */, 3)).thenReturn(mMockDrawable3);
        when(mMockIconInjector.getIcon(false /* showX */, 4)).thenReturn(mMockDrawable4);

        when(mMockIconInjector.getIcon(true /* showX */, 0))
                .thenReturn(mMockShowXDrawable0);
        when(mMockIconInjector.getIcon(true /* showX */, 1))
                .thenReturn(mMockShowXDrawable1);
        when(mMockIconInjector.getIcon(true /* showX */, 2))
                .thenReturn(mMockShowXDrawable2);
        when(mMockIconInjector.getIcon(true /* showX */, 3))
                .thenReturn(mMockShowXDrawable3);
        when(mMockIconInjector.getIcon(true /* showX */, 4))
                .thenReturn(mMockShowXDrawable4);

        mPref = new WifiEntryPreference(mContext, mMockWifiEntry, mMockIconInjector);
    }

    @Test
    public void constructor_shouldSetWifiEntryTitleAndSummary() {
        final WifiEntryPreference pref =
                new WifiEntryPreference(mContext, mMockWifiEntry, mMockIconInjector);

        assertThat(pref.getTitle()).isEqualTo(MOCK_TITLE);
        assertThat(pref.getSummary()).isEqualTo(MOCK_SUMMARY);
    }

    @Test
    public void constructor_shouldSetIcon() {
        when(mMockWifiEntry.getLevel()).thenReturn(0);

        final WifiEntryPreference pref =
                new WifiEntryPreference(mContext, mMockWifiEntry, mMockIconInjector);

        assertThat(pref.getIcon()).isEqualTo(mMockDrawable0);
    }

    @Test
    public void titleChanged_refresh_shouldUpdateTitle() {
        final String updatedTitle = "updated title";
        when(mMockWifiEntry.getTitle()).thenReturn(updatedTitle);

        mPref.refresh();

        assertThat(mPref.getTitle().toString()).isEqualTo(updatedTitle);
    }

    @Test
    public void summaryChanged_refresh_shouldUpdateSummary() {
        final String updatedSummary = "updated summary";
        when(mMockWifiEntry.getSummary(false /* concise */)).thenReturn(updatedSummary);

        mPref.refresh();

        assertThat(mPref.getSummary().toString()).isEqualTo(updatedSummary);
    }

    @Test
    public void levelChanged_refresh_shouldUpdateLevelIcon() {
        final List<Drawable> iconList = new ArrayList<>();

        when(mMockWifiEntry.getLevel()).thenReturn(0);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(1);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(2);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(3);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(4);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(-1);
        mPref.refresh();
        iconList.add(mPref.getIcon());

        assertThat(iconList).containsExactly(mMockDrawable0, mMockDrawable1,
                mMockDrawable2, mMockDrawable3, mMockDrawable4, null);
    }

    @Test
    public void levelChanged_showXWifiRefresh_shouldUpdateLevelIcon() {
        final List<Drawable> iconList = new ArrayList<>();
        when(mMockWifiEntry.shouldShowXLevelIcon()).thenReturn(true);

        when(mMockWifiEntry.getLevel()).thenReturn(0);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(1);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(2);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(3);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(4);
        mPref.refresh();
        iconList.add(mPref.getIcon());
        when(mMockWifiEntry.getLevel()).thenReturn(-1);
        mPref.refresh();
        iconList.add(mPref.getIcon());

        assertThat(iconList).containsExactly(mMockShowXDrawable0, mMockShowXDrawable1,
                mMockShowXDrawable2, mMockShowXDrawable3, mMockShowXDrawable4, null);
    }

    @Test
    public void notNull_whenGetHelpUriString_shouldSetImageButtonVisible() {
        when(mMockWifiEntry.getHelpUriString()).thenReturn(FAKE_URI_STRING);
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View view = inflater.inflate(mPref.getLayoutResource(), new LinearLayout(mContext),
                false);
        final PreferenceViewHolder holder = PreferenceViewHolder.createInstanceForTests(view);

        mPref.onBindViewHolder(holder);

        assertThat(view.findViewById(R.id.icon_button).getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void helpButton_whenGetHelpUriStringNotNull_shouldSetCorrectContentDescription() {
        when(mMockWifiEntry.getHelpUriString()).thenReturn(FAKE_URI_STRING);
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View view = inflater.inflate(mPref.getLayoutResource(), new LinearLayout(mContext),
                false);
        final PreferenceViewHolder holder = PreferenceViewHolder.createInstanceForTests(view);

        mPref.onBindViewHolder(holder);

        assertThat(view.findViewById(R.id.icon_button).getContentDescription()).isEqualTo(
                mContext.getString(R.string.help_label));
    }

    @Test
    public void subscriptionEntry_shouldSetImageButtonGone() {
        when(mMockWifiEntry.isSubscription()).thenReturn(true);
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View view = inflater.inflate(mPref.getLayoutResource(), new LinearLayout(mContext),
                false);
        final PreferenceViewHolder holder = PreferenceViewHolder.createInstanceForTests(view);

        mPref.onBindViewHolder(holder);

        assertThat(view.findViewById(R.id.icon_button).getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void updateIcon_shouldSetTintListForDrawable() {
        mPref.updateIcon(false /* showX */, 4 /* level */);

        verify(mMockDrawable4).setTintList(any());
    }

    @Test
    public void getSecondTargetResId_shouldNotReturnZero() {
        assertThat(mPref.getSecondTargetResId()).isNotEqualTo(0);
    }
}
