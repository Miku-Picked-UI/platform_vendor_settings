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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stores a user-picked image in app files and publishes a copy under
 * {@code Android/media/<package>/} so SystemUI can read it.
 *
 * Custom QS header art is CenterCropped to {@link #TARGET_WIDTH} x {@link #TARGET_HEIGHT},
 * matching {@code statusbar_miku} (3016x1282).
 */
public final class CustomImageStore {
    private static final String TAG = "CustomImageStore";
    private static final int JPEG_QUALITY = 90;

    public static final String RELATIVE_DIR = "miku_qs";
    public static final String FILE_NAME = "header.jpg";
    /** Shared via the pre-existing FileProvider cache-path named {@code my_cache}. */
    public static final String SHARE_FILE_NAME = "miku_qs_header.jpg";
    /** Pixel size of SystemUI {@code statusbar_miku}. */
    public static final int TARGET_WIDTH = 3016;
    public static final int TARGET_HEIGHT = 1282;

    private CustomImageStore() {}

    public static File getFile(Context context) {
        File dir = new File(context.getFilesDir(), RELATIVE_DIR);
        return new File(dir, FILE_NAME);
    }

    public static boolean exists(Context context) {
        return getFile(context).isFile();
    }

    public static boolean delete(Context context) {
        unpublish(context);
        File file = getFile(context);
        return !file.exists() || file.delete();
    }

    /** Shared copy under Android/media so SystemUI can read it without FileProvider. */
    public static File getPublishedFile(Context context) {
        File[] dirs = context.getExternalMediaDirs();
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir == null) {
                    continue;
                }
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.w(TAG, "Cannot create " + dir);
                    continue;
                }
                return new File(dir, SHARE_FILE_NAME);
            }
        }
        return null;
    }

    public static String publish(Context context) {
        File persistent = getFile(context);
        File published = getPublishedFile(context);
        if (!persistent.isFile() || published == null) {
            return null;
        }
        try {
            copyFile(persistent, published);
            if (!published.setReadable(true, false)) {
                Log.w(TAG, "Could not mark " + published + " world-readable");
            }
            return published.getAbsolutePath();
        } catch (IOException e) {
            Log.w(TAG, "Failed to publish header image", e);
            return null;
        }
    }

    public static void unpublish(Context context) {
        File published = getPublishedFile(context);
        if (published != null && published.exists() && !published.delete()) {
            Log.w(TAG, "Failed to delete " + published);
        }
    }

    public static Bitmap load(Context context) {
        File file = getFile(context);
        if (!file.isFile()) {
            return null;
        }
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(file);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) ->
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        } catch (IOException e) {
            Log.w(TAG, "Failed to load " + file.getAbsolutePath(), e);
            return null;
        }
    }

    public static boolean saveFromUri(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        Bitmap decoded = null;
        Bitmap cropped = null;
        try {
            ImageDecoder.Source source =
                    ImageDecoder.createSource(context.getContentResolver(), uri);
            decoded = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                float scale = Math.max(
                        TARGET_WIDTH / (float) width,
                        TARGET_HEIGHT / (float) height);
                decoder.setTargetSize(
                        Math.max(TARGET_WIDTH, Math.round(width * scale)),
                        Math.max(TARGET_HEIGHT, Math.round(height * scale)));
            });
            if (decoded == null) {
                return false;
            }
            cropped = centerCrop(decoded, TARGET_WIDTH, TARGET_HEIGHT);
            File dest = getFile(context);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.w(TAG, "Cannot create " + parent);
                return false;
            }
            try (FileOutputStream out = new FileOutputStream(dest)) {
                if (!cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    return false;
                }
                out.flush();
            }
            return true;
        } catch (RuntimeException | IOException e) {
            Log.w(TAG, "Failed to save image", e);
            return false;
        } finally {
            if (decoded != null && decoded != cropped) {
                decoded.recycle();
            }
            if (cropped != null) {
                cropped.recycle();
            }
        }
    }

    public static Drawable loadDrawableFromPackage(Context context, String packageName,
            String resName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            int id = res.getIdentifier(resName, "drawable", packageName);
            if (id == 0) {
                return null;
            }
            return res.getDrawable(id, context.getTheme());
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Missing package " + packageName, e);
            return null;
        }
    }

    public static Intent createPickImageIntent(Context context) {
        Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
        picker.setType("image/*");
        if (picker.resolveActivity(context.getPackageManager()) != null) {
            return picker;
        }
        Intent getContent = new Intent(Intent.ACTION_GET_CONTENT);
        getContent.addCategory(Intent.CATEGORY_OPENABLE);
        getContent.setType("image/*");
        return getContent;
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src);
                OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
    }

    private static Bitmap centerCrop(Bitmap src, int outW, int outH) {
        float scale = Math.max(outW / (float) src.getWidth(), outH / (float) src.getHeight());
        int scaledW = Math.max(outW, Math.round(src.getWidth() * scale));
        int scaledH = Math.max(outH, Math.round(src.getHeight() * scale));
        Bitmap scaled = src;
        if (scaledW != src.getWidth() || scaledH != src.getHeight()) {
            scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true);
        }
        int x = Math.max(0, (scaled.getWidth() - outW) / 2);
        int y = Math.max(0, (scaled.getHeight() - outH) / 2);
        int w = Math.min(outW, scaled.getWidth());
        int h = Math.min(outH, scaled.getHeight());
        Bitmap cropped = Bitmap.createBitmap(scaled, x, y, w, h);
        if (scaled != src && scaled != cropped) {
            scaled.recycle();
        }
        if (cropped.getWidth() != outW || cropped.getHeight() != outH) {
            Bitmap fitted = Bitmap.createScaledBitmap(cropped, outW, outH, true);
            if (cropped != src && cropped != fitted) {
                cropped.recycle();
            }
            return fitted;
        }
        return cropped;
    }
}
