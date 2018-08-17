package dex;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;


public class Utils {


    public static <T extends Versioned> void safelyWorkWithClass(T obj, ToDoWithDataCallback<T> success, ToDoInterface failure) {
        try {
            if (obj != null && success != null) {
                success.todo(obj);
            } else {
                if (failure != null) failure.todo();
            }
        } catch (Throwable throwable) {
            if (failure != null) failure.todo();
        }
    }


    /**
     * Get full name for incoming dex file ("fileName")
     * @param context - Context for method getDir
     * @param fileName - File name with extension. Sample: "file.dex"
     * @return full name file Sample: "/data/user/0/ua.com.arget.dex_worker/app_dex/file.dex"
     */
    public static String getFileFullName(ContextWrapper context, String fileName) {
        return context.getDir("dex", Context.MODE_PRIVATE) + "/" + fileName;
    }


    /**
     * Get result copy dex file from assets. Run only in background thread.
     * @param context - Context for method getDir
     * @param nameAssets - File name with extension. Sample: "file.dex"
     * @param path - File path. Sample: "/data/user/0/package/project"
     * @return boolean result of operation. Sample: {@code true}
     */
    public static boolean copyAssets(Context context, String nameAssets, String path) {
        Utils.stopIfMainThread();

        boolean result = false;
        InputStream is = null;
        OutputStream os = null;

        try {

            is = context.getAssets().open(nameAssets);
            os = new FileOutputStream(path);
            byte[] buffer = new byte[1024];
            int length;

            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }

            result = true;
        } catch (Throwable ignored) {
            // Nothing
        } finally {
            Utils.closeQuietly(os, is);
        }

        return result;
    }


    /**
     * Checks if the file exists
     * @param fullName - file name with path
     * @return true - if exist, false - if not exist.
     */
    public static boolean isExists(String fullName) {
        return new File(fullName).exists();
    }

    /**
     * Checks if the file exists
     * @param context - Context for method getDir
     * @param fileName - file name, sample "file.dex"
     * @return true - if exist, false - if not exist.
     */
    public static boolean isExists(ContextWrapper context, String fileName) {
        return new File(getFileFullName(context, fileName)).exists();
    }


    /**
     * Get md5 hash from file. Run only in background thread.
     * @param fileFullName - path to the file, that md5 hash need to check. Sample: "/data/user/0/ua.com.arget.dex_worker/app_dex/file.dex"
     * @return String result of operation. Sample: {@code "74f8d316c95b4d0b4702a76930f4e127"}
     */
    public static String getMD5(String fileFullName) {
        return getMD5(new File(fileFullName));
    }

    /**
     * Get md5 hash from file. Run only in background thread.
     * @param file - File that md5 hash need to check
     * @return String result of operation. Sample: {@code "74f8d316c95b4d0b4702a76930f4e127"}
     */
    public static String getMD5(File file) {
        stopIfMainThread();

        String result = "";
        InputStream is = null;

        try {

            MessageDigest digest = MessageDigest.getInstance("MD5");
            is = new FileInputStream(file);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }

            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            result = String.format("%32s", output).replace(' ', '0');
        } catch (Throwable ignored) {
            // Nothing
        } finally {
            closeQuietly(is);
        }

        return result;
    }


    /**
     * Download file (async) by url in {@code fromUrl} to file by path in {@code toFullFileName}
     * @param fromUrl - direct link to file download file
     * @param toFullFileName - path to download and save the file
     * @param handler - handler for work with in UI Thread
     * @param success - callback for success download
     * @param failure - callback for failure download
     */
    public static void downloadFileAsync(String fromUrl, String toFullFileName, String md5, Handler handler, ToDoInterface success, ToDoInterface failure) {
        Utils.runInNewThread(() -> {

            AtomicBoolean result = new AtomicBoolean(false);

            if (Utils.downloadFile(fromUrl, toFullFileName))  {
                result.set(md5.equals(Utils.getMD5(toFullFileName)));
            }

            handler.post(() -> {
                if (result.get()) {
                    if (success != null) success.todo();
                } else {
                    if (failure != null) failure.todo();
                }
            });
        });
    }


    /**
     * Download file (sync) by url in {@code fromUrl} to file by path in {@code toFullFileName}
     * @param fromUrl - direct link to file download file
     * @param toFullFileName - path to download and save the file
     * @return boolean result of operation, true - successfully downloaded, false - download error.
     */
    public static boolean downloadFile(String fromUrl, String toFullFileName) {

        boolean result = false;
        InputStream input = null;
        OutputStream output = null;
        int count;

        try {

            if (isExists(toFullFileName)) new File(toFullFileName).delete();

            URL url = new URL(fromUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(toFullFileName, true);

            byte []data = new byte[1024];
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }

            connection.disconnect();
            result = true;
        } catch (Throwable throwable) { throwable.printStackTrace();//TODO
            if (isExists(toFullFileName)) new File(toFullFileName).delete();
        } finally {
            closeQuietly(output, input);
        }

        return result;
    }

    /**
     * Throwing IllegalStateException if the method run in main thread
     */
    public static void stopIfMainThread() {
        if (isInUiThread()) throw new IllegalStateException("Must not be invoked from the main thread.");
    }

    /**
     *  Checking the thread of the method
     *  @return boolean result of operation, true - method run in main thread, false - method run in background thread.
     */
    public static boolean isInUiThread() {
        return Thread.currentThread().equals(Looper.getMainLooper().getThread());
    }

    /**
     * Starting a new thread for executable code
     * @param runnable - executable code.
     */
    public static void runInNewThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    /**
     * Close all opened streams
     * @param cls - array of opened streams.
     */
    public static void closeQuietly(Closeable... cls) {
        for (Closeable cl : cls) {
            if (cl != null) {
                try {
                    cl.close();
                } catch (Throwable ignored) { /* TODO */ }
            }
        }
    }

    /**
     * Returns absolute path to application-specific directory on the primary
     * shared/external storage device where the application can place cache
     * files it owns. These files are internal to the application, and not
     * typically visible to the user as media.
     */
    public static File getCodeCacheDir(Context context) {
        return Build.VERSION.SDK_INT >= 21
                ? context.getCodeCacheDir()
                : context.getDir("odex", Context.MODE_PRIVATE);
    }
}
