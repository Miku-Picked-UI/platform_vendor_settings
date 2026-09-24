/*
 * Copyright (C) 2026 Miku UI
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

package com.miku.settings.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.miku.settings.R;

/**
 * Dialog with a live image preview, restore / gallery actions, and an opacity slider.
 * Action buttons do not dismiss the dialog; outside tap and back still cancel it.
 */
public class CustomImagePickerDialog extends AlertDialog implements SeekBar.OnSeekBarChangeListener {

    public interface Callbacks {
        void onRestoreDefault();
        void onPickFromGallery();
        void onAlphaChanged(int alpha);
    }

    private final ImageView mPreview;
    private final TextView mAlphaValue;
    private final SeekBar mAlphaSeekBar;
    private final Callbacks mCallbacks;
    private int mAlpha;

    public CustomImagePickerDialog(Context context, CharSequence title,
            CharSequence restoreText, CharSequence pickText, CharSequence alphaTitle,
            int alpha, Callbacks callbacks) {
        super(context);
        mCallbacks = callbacks;
        mAlpha = clampAlpha(alpha);

        setTitle(title);
        setCancelable(true);
        setCanceledOnTouchOutside(true);

        View content = LayoutInflater.from(context).inflate(
                R.layout.dialog_custom_image_picker, null, false);
        mPreview = content.findViewById(R.id.custom_image_preview);
        Button restore = content.findViewById(R.id.custom_image_restore);
        Button pick = content.findViewById(R.id.custom_image_pick);
        TextView alphaLabel = content.findViewById(R.id.custom_image_alpha_title);
        mAlphaSeekBar = content.findViewById(R.id.custom_image_alpha_seekbar);
        mAlphaValue = content.findViewById(R.id.custom_image_alpha_value);

        restore.setText(restoreText);
        pick.setText(pickText);
        alphaLabel.setText(alphaTitle);
        mAlphaSeekBar.setProgress(mAlpha);
        mAlphaValue.setText(mAlpha + "%");
        mAlphaSeekBar.setOnSeekBarChangeListener(this);

        restore.setOnClickListener(v -> {
            if (mCallbacks != null) {
                mCallbacks.onRestoreDefault();
            }
        });
        pick.setOnClickListener(v -> {
            if (mCallbacks != null) {
                mCallbacks.onPickFromGallery();
            }
        });

        applyPreviewAlpha();
        setView(content);
    }

    public void setPreviewDrawable(Drawable drawable) {
        mPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mPreview.setImageDrawable(drawable);
        applyPreviewAlpha();
    }

    public void setPreviewBitmap(Bitmap bitmap) {
        mPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mPreview.setImageBitmap(bitmap);
        applyPreviewAlpha();
    }

    public void setAlpha(int alpha) {
        mAlpha = clampAlpha(alpha);
        if (mAlphaSeekBar.getProgress() != mAlpha) {
            mAlphaSeekBar.setProgress(mAlpha);
        }
        mAlphaValue.setText(mAlpha + "%");
        applyPreviewAlpha();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mAlpha = clampAlpha(progress);
        mAlphaValue.setText(mAlpha + "%");
        applyPreviewAlpha();
        if (fromUser && mCallbacks != null) {
            mCallbacks.onAlphaChanged(mAlpha);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    private void applyPreviewAlpha() {
        mPreview.setImageAlpha(mAlpha * 255 / 100);
    }

    private static int clampAlpha(int alpha) {
        return Math.max(0, Math.min(100, alpha));
    }
}
