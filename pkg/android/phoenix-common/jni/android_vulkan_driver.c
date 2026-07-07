#if defined(ANDROID) && defined(HAVE_ADRENOTOOLS)

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <adrenotools/driver.h>

#define LOG_TAG "RetroArch/VulkanDriver"

char android_vulkan_driver_dir[4096]    = {0};
char android_vulkan_hook_lib_dir[4096]  = {0};
char android_vulkan_adrenotools_path[4096] = {0};
bool android_vulkan_driver_enabled      = false;
void *android_vulkan_library_handle     = NULL;

/* handle of adrenotools disallow outside dlclose，
 * if not, dylib_close of RetroArch break hook situation.
 * Use a sentinel for vulkan_common.c knew handle. */
bool android_vulkan_library_managed      = false;
char android_vulkan_driver_display_name[256] = {0};
void *android_vulkan_gpu_mapping_handle = NULL;

JNIEXPORT void JNICALL
Java_com_retroarch_browser_retroactivity_RetroActivityCommon_nativeSetVulkanDriverDir(
      JNIEnv *env, jobject obj,
      jstring driver_dir, jstring hook_lib_dir,
      jstring tmp_dir, jstring file_redirect_dir,
      jstring driver_lib_name, jstring driver_display_name)
{
   const char *d  = driver_dir        ? (*env)->GetStringUTFChars(env, driver_dir,        NULL) : NULL;
   const char *h  = hook_lib_dir      ? (*env)->GetStringUTFChars(env, hook_lib_dir,      NULL) : NULL;
   const char *t  = tmp_dir           ? (*env)->GetStringUTFChars(env, tmp_dir,           NULL) : NULL;
   const char *fr = file_redirect_dir ? (*env)->GetStringUTFChars(env, file_redirect_dir, NULL) : NULL;
   const char *ln = driver_lib_name   ? (*env)->GetStringUTFChars(env, driver_lib_name,   NULL) : NULL;
   const char *dn = driver_display_name ? (*env)->GetStringUTFChars(env, driver_display_name, NULL) : NULL;

   if (d && d[0] != '\0' && h && h[0] != '\0' && t && t[0] != '\0' && ln && ln[0] != '\0')
   {
      strncpy(android_vulkan_driver_dir,   d, sizeof(android_vulkan_driver_dir)   - 1);
      strncpy(android_vulkan_hook_lib_dir, h, sizeof(android_vulkan_hook_lib_dir) - 1);
      snprintf(android_vulkan_adrenotools_path,
               sizeof(android_vulkan_adrenotools_path),
               "%s/libadrenotools.so", h);
      android_vulkan_driver_enabled = true;
      char driver_dir_slash[4096];
      snprintf(driver_dir_slash, sizeof(driver_dir_slash), "%s/", d);
      char tmp_dir_slash[4096];
      snprintf(tmp_dir_slash, sizeof(tmp_dir_slash), "%s/", t);
      char file_redirect_dir_slash[4096] = {0};
      const char *file_redirect_ptr = NULL;
      if (fr && fr[0] != '\0')
      {
         snprintf(file_redirect_dir_slash, sizeof(file_redirect_dir_slash), "%s/", fr);
         file_redirect_ptr = file_redirect_dir_slash;
      }

       /* feature flags:
        *   CUSTOM           - Load custom Turnip driver (.so)
        *   FILE_REDIRECT    - Redirect driver access from read-only paths (e.g., /system/lib/.../kgsl)
        *                      to the app's private directory to bypass SELinux restrictions.
        *   GSL_ALLOC_HOOK   - Hook GSL memory allocation (required by certain Turnip versions). */
       int feature_flags = ADRENOTOOLS_DRIVER_CUSTOM
                           | ADRENOTOOLS_DRIVER_FILE_REDIRECT
                           | ADRENOTOOLS_DRIVER_GPU_MAPPING_IMPORT;
      __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
            "adrenotools_open_libvulkan: flags=0x%x driver=%s hook=%s tmp=%s redirect=%s lib=%s",
            feature_flags, driver_dir_slash, h, tmp_dir_slash,
            file_redirect_ptr ? file_redirect_ptr : "(null)", ln);

      void *vk = adrenotools_open_libvulkan(
              RTLD_NOW,
              feature_flags,
              tmp_dir_slash,           /* tmpDir: Must be writable */
              h,                       /* hookLibDir */
              driver_dir_slash,        /* customDriverDir */
              ln,                      /* customDriverName: Now dynamically passed from meta.json */
              file_redirect_ptr,       /* fileRedirectDir (Can be NULL) */
              &android_vulkan_gpu_mapping_handle); /* userMapping: must be non-NULL when
                                                       GPU_MAPPING_IMPORT flag is set */

      if (vk)
      {
         android_vulkan_library_handle  = vk;
         android_vulkan_library_managed = true;
         if (dn && dn[0] != '\0')
            strncpy(android_vulkan_driver_display_name, dn,
                    sizeof(android_vulkan_driver_display_name) - 1);
         __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
               "adrenotools hook installed: vk=%p managed=1", vk);
      }
      else
      {
         __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
               "adrenotools_open_libvulkan failed: %s", dlerror());
      }
   }
   else
   {
      __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
            "nativeSetVulkanDriverDir: missing args (d=%s h=%s t=%s ln=%s)",
            d ? d : "(null)", h ? h : "(null)", t ? t : "(null)", ln ? ln : "(null)");
   }

   if (d)  (*env)->ReleaseStringUTFChars(env, driver_dir,        d);
   if (h)  (*env)->ReleaseStringUTFChars(env, hook_lib_dir,      h);
   if (t)  (*env)->ReleaseStringUTFChars(env, tmp_dir,           t);
   if (fr) (*env)->ReleaseStringUTFChars(env, file_redirect_dir, fr);
   if (ln) (*env)->ReleaseStringUTFChars(env, driver_lib_name,   ln);
   if (dn) (*env)->ReleaseStringUTFChars(env, driver_display_name, dn);
}

#endif /* ANDROID && HAVE_ADRENOTOOLS */
