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

/* adrenotools 的 handle 不允许被外部 dlclose，
 * 否则 RetroArch 的 dylib_close 会破坏 hook 状态。
 * 用一个 sentinel 让 vulkan_common.c 知道这个 handle 是受管理的。 */
bool android_vulkan_library_managed      = false;

JNIEXPORT void JNICALL
Java_com_retroarch_browser_retroactivity_RetroActivityCommon_nativeSetVulkanDriverDir(
      JNIEnv *env, jobject obj,
      jstring driver_dir, jstring hook_lib_dir,
      jstring tmp_dir, jstring file_redirect_dir)
{
    const char *d = driver_dir ? (*env)->GetStringUTFChars(env, driver_dir, NULL) : NULL;
    const char *h = hook_lib_dir ? (*env)->GetStringUTFChars(env, hook_lib_dir, NULL) : NULL;
    const char *t = tmp_dir ? (*env)->GetStringUTFChars(env, tmp_dir, NULL) : NULL;
    const char *fr = file_redirect_dir ? (*env)->GetStringUTFChars(env, file_redirect_dir, NULL)
                                       : NULL;

    if (d && d[0] != '\0' && h && h[0] != '\0' && t && t[0] != '\0') {
        strncpy(android_vulkan_driver_dir, d, sizeof(android_vulkan_driver_dir) - 1);
        strncpy(android_vulkan_hook_lib_dir, h, sizeof(android_vulkan_hook_lib_dir) - 1);
        snprintf(android_vulkan_adrenotools_path,
                 sizeof(android_vulkan_adrenotools_path),
                 "%s/libadrenotools.so", h);
        android_vulkan_driver_enabled = true;

        /* driver_dir 加尾部斜杠 (adrenotools 内部用 snprintf 拼路径) */
        char driver_dir_slash[4096];
        snprintf(driver_dir_slash, sizeof(driver_dir_slash), "%s/", d);

        /* tmp_dir 加尾部斜杠 */
        char tmp_dir_slash[4096];
        snprintf(tmp_dir_slash, sizeof(tmp_dir_slash), "%s/", t);

        /* file_redirect_dir 加尾部斜杠 (如果传了) */
        char file_redirect_dir_slash[4096] = {0};
        const char *file_redirect_ptr = NULL;
        if (fr && fr[0] != '\0') {
            snprintf(file_redirect_dir_slash, sizeof(file_redirect_dir_slash), "%s/", fr);
            file_redirect_ptr = file_redirect_dir_slash;
        }

        /* feature flags:
         *   CUSTOM           - 加载用户提供的 turnip .so
         *   FILE_REDIRECT    - 把 driver 对 /system/lib/.../kgsl 等只读路径的访问
         *                      重定向到应用私有目录, 绕过 SELinux
         *   GSL_ALLOC_HOOK   - hook GSL 内存分配, 部分 turnip 版本必需 */
        int feature_flags = ADRENOTOOLS_DRIVER_CUSTOM
                            | ADRENOTOOLS_DRIVER_FILE_REDIRECT;
#ifdef ADRENOTOOLS_DRIVER_GSL_ALLOC_HOOK
        feature_flags |= ADRENOTOOLS_DRIVER_GSL_ALLOC_HOOK;
#endif
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                            "adrenotools_open_libvulkan: flags=0x%x driver=%s hook=%s tmp=%s redirect=%s",
                            feature_flags, driver_dir_slash, h, tmp_dir_slash,
                            file_redirect_ptr ? file_redirect_ptr : "(null)");

        void *vk = adrenotools_open_libvulkan(
                RTLD_NOW,
                feature_flags,
                tmp_dir_slash,           /* tmpDir: 必须可写 */
                h,                       /* hookLibDir */
                driver_dir_slash,        /* customDriverDir */
                "libvulkan_freedreno.so",/* customDriverName */
                file_redirect_ptr,       /* fileRedirectDir (可为 NULL) */
                NULL);                   /* userMapping */

        if (vk) {
            android_vulkan_library_handle = vk;
            android_vulkan_library_managed = true;
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                                "adrenotools hook installed: vk=%p managed=1", vk);
        } else {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                                "adrenotools_open_libvulkan failed: %s", dlerror());
        }
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                            "nativeSetVulkanDriverDir: missing args (d=%s h=%s t=%s)",
                            d ? d : "(null)", h ? h : "(null)", t ? t : "(null)");
    }

    if (d) (*env)->ReleaseStringUTFChars(env, driver_dir, d);
    if (h) (*env)->ReleaseStringUTFChars(env, hook_lib_dir, h);
    if (t) (*env)->ReleaseStringUTFChars(env, tmp_dir, t);
    if (fr) (*env)->ReleaseStringUTFChars(env, file_redirect_dir, fr);
}

#endif /* ANDROID && HAVE_ADRENOTOOLS */
