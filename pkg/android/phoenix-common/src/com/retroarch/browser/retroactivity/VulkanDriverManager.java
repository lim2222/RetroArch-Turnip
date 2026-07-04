package com.retroarch.browser.retroactivity;

import android.content.Context;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;

@android.annotation.TargetApi(26)
public class VulkanDriverManager {

    private static final String TAG       = "RetroArch/VulkanDriver";
    private static final String EXTRACT   = "vulkan_driver";
    private static final String ZIP_SUBDIR = "vulkan_drivers";

    public static boolean isArm64() {
        return Build.SUPPORTED_ABIS != null &&
               Build.SUPPORTED_ABIS.length > 0 &&
               Build.SUPPORTED_ABIS[0].equals("arm64-v8a");
    }

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
            Log.i(TAG, "Extracted to: " + outDir.getAbsolutePath());
            return outDir.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Extract failed: " + e.getMessage());
            return null;
        }
    }

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

    private static void copyStream(java.io.InputStream in, File out) throws Exception {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        fos.close();
        in.close();
    }

    private static void deleteDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }
}
