package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.net.Uri;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.Enumeration;

@TargetApi(26)
public class VulkanDriverManager {

    private static final String TAG          = "RetroArch/VulkanDriver";
    private static final String EXTRACT       = "vulkan_driver";
    private static final String ZIP_SUBDIR    = "vulkan_drivers";
    private static final String META_FILE     = "meta.json";
    private static final String LIB_NAME_PREF = "cached_lib_name";

    private static final String DISPLAY_NAME_PREF = "cached_display_name";

    /** 
     * Extraction result: Contains the driver directory, the actual driver .so filename, 
     * and the display name (retrieved from the 'name' field in meta.json). */
    public static class ExtractResult {
        public final String driverDir;
        public final String libraryName;
        public final String displayName;
        public ExtractResult(String driverDir, String libraryName, String displayName) {
            this.driverDir   = driverDir;
            this.libraryName = libraryName;
            this.displayName = displayName;
        }
    }

    public static boolean isArm64() {
        return Build.SUPPORTED_ABIS != null &&
               Build.SUPPORTED_ABIS.length > 0 &&
               Build.SUPPORTED_ABIS[0].equals("arm64-v8a");
    }

    /**
     * Extracts the driver ZIP from any content:// URI and parses meta.json to obtain the libraryName.
     * The extraction directory is fixed to codeCacheDir/vulkan_driver.
     */
    public static ExtractResult extractDriverFromUri(Context ctx, Uri uri) {
        File outDir = new File(ctx.getCodeCacheDir(), EXTRACT);
        try {
            deleteDir(outDir);
            outDir.mkdirs();

            InputStream is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) {
                Log.e(TAG, "Failed to open input stream for URI: " + uri);
                return null;
            }

            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File outFile = new File(outDir, new File(entry.getName()).getName());
                copyStream(zis, outFile);
                if (outFile.getName().endsWith(".so")) {
                    outFile.setExecutable(true, false);
                    outFile.setReadable(true, false);
                }
                zis.closeEntry();
            }
            zis.close();

            String libraryName = readLibraryNameFromMeta(outDir);
            if (libraryName == null) {
                libraryName = findFirstSo(outDir);
            }
            if (libraryName == null) {
                Log.e(TAG, "No .so found in extracted driver zip");
                return null;
            }
            String displayName = readDisplayNameFromMeta(outDir);
            if (displayName == null) displayName = libraryName;

            ctx.getSharedPreferences("retroarch_vulkan", Context.MODE_PRIVATE)
               .edit()
               .putString(LIB_NAME_PREF, libraryName)
               .putString(DISPLAY_NAME_PREF, displayName)
               .apply();
            Log.i(TAG, "Extracted to: " + outDir.getAbsolutePath() + ", library=" + libraryName);
            return new ExtractResult(outDir.getAbsolutePath(), libraryName, displayName);

        } catch (Exception e) {
            Log.e(TAG, "Extract from URI failed: " + e.getMessage());
            return null;
        }
    }

    private static String readLibraryNameFromMeta(File dir) {
        File meta = new File(dir, META_FILE);
        if (!meta.exists()) return null;
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(meta);
            byte[] buf = new byte[(int) meta.length()];
            fis.read(buf);
            fis.close();
            JSONObject json = new JSONObject(new String(buf, "UTF-8"));
            if (json.has("libraryName"))
                return json.getString("libraryName");
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse meta.json: " + e.getMessage());
        }
        return null;
    }
    private static String readDisplayNameFromMeta(File dir) {
        File meta = new File(dir, META_FILE);
        if (!meta.exists()) return null;
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(meta);
            byte[] buf = new byte[(int) meta.length()];
            fis.read(buf);
            fis.close();
            JSONObject json = new JSONObject(new String(buf, "UTF-8"));
            if (json.has("name"))
                return json.getString("name");
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse meta.json (name): " + e.getMessage());
        }
        return null;
    }
    private static String findFirstSo(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.getName().endsWith(".so"))
                return f.getName();
        }
        return null;
    }

    /**
     * Backward compatibility interface: Extracts the driver ZIP from a local file path 
     * without parsing meta.json.
     */
    public static String extractDriver(Context ctx, String zipPath) {
        File outDir = new File(ctx.getCodeCacheDir(), EXTRACT);
        try {
            deleteDir(outDir);
            outDir.mkdirs();

            ZipFile zip = new ZipFile(zipPath);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                File outFile = new File(outDir, new File(entry.getName()).getName());
                copyStream(zip.getInputStream(entry), outFile);
                if (outFile.getName().endsWith(".so")) {
                    outFile.setExecutable(true, false);
                    outFile.setReadable(true, false);
                }
            }
            zip.close();
            String libraryName = readLibraryNameFromMeta(outDir);
            if (libraryName == null)
                libraryName = findFirstSo(outDir);
            if (libraryName != null) {
                String displayName = readDisplayNameFromMeta(outDir);
                if (displayName == null) displayName = libraryName;
                ctx.getSharedPreferences("retroarch_vulkan", Context.MODE_PRIVATE)
                   .edit()
                   .putString(LIB_NAME_PREF, libraryName)
                   .putString(DISPLAY_NAME_PREF, displayName)
                   .apply();
            }
            Log.i(TAG, "Extracted to: " + outDir.getAbsolutePath());
            return outDir.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Extract failed: " + e.getMessage());
            return null;
        }
    }

    /**
    * Checks whether the last extraction is still valid (including the .so file). 
    * Returns the directory path if valid, or null otherwise. */
    public static String getCachedDir(Context ctx) {
        File dir = new File(ctx.getCodeCacheDir(), EXTRACT);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".so")) return dir.getAbsolutePath();
            }
        }
        return null;
    }

    /** The previously cached driver filename */
    public static String getCachedLibraryName(Context ctx) {
        String cached = ctx.getSharedPreferences("retroarch_vulkan", Context.MODE_PRIVATE)
                            .getString(LIB_NAME_PREF, null);
        if (cached == null) return null;
        // Verify that the corresponding .so file actually exists
        File f = new File(new File(ctx.getCodeCacheDir(), EXTRACT), cached);
        return f.exists() ? cached : null;
    }
    /** The previously cached driver display name (from the 'name' field in meta.json) */
    public static String getCachedDisplayName(Context ctx) {
        return ctx.getSharedPreferences("retroarch_vulkan", Context.MODE_PRIVATE)
                  .getString(DISPLAY_NAME_PREF, null);
    }

    /**
     * Backward compatibility interface: Scans the list of ZIP files under a fixed directory 
     * (no longer the primary entry point; retained for reference purposes). */
    public static List<File> listZips(String systemDir) {
        List<File> result = new ArrayList<>();
        File dir = new File(systemDir, ZIP_SUBDIR);
        if (!dir.exists()) return result;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".zip")) result.add(f);
            }
        }
        return result;
    }

    /** Clears the extraction cache (called when switching drivers) */
    public static void clearCache(Context ctx) {
        File dir = new File(ctx.getCodeCacheDir(), EXTRACT);
        deleteDir(dir);
        ctx.getSharedPreferences("retroarch_vulkan", Context.MODE_PRIVATE)
                .edit().remove(LIB_NAME_PREF).apply();
    }

    private static void copyStream(InputStream in, File out) throws Exception {
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        fos.close();
    }

    static void deleteDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }
}
