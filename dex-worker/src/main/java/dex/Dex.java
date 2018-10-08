package dex;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;

import java.io.File;
import java.io.FileNotFoundException;

import dalvik.system.DexClassLoader;

import java.util.HashMap;


public class Dex {

    private static Dex instance;
    private final static Object lockObject = new Object();

    private DexClassLoader dexClassLoader = null;
    private HashMap<String, Class> classes = new HashMap<>();
    private HashMap<String, Versioned> objects = new HashMap<>();
    private boolean prepared = false;
    private Params params;


    /**
     * Like {@link #getInstance(Params params)}
     * with {@code params = null}
     */
    public static Dex getInstance() {
        return getInstance(null);
    }

    /**
     * Singleton for Dex
     * @param params - Required parameters are needed for work
     * @return instance of Dex
     */
    public static Dex getInstance(Params params) {

        if (instance == null) {

            synchronized(lockObject) {

                if (instance == null) {
                    instance = new Dex();
                }
            }

        }

        if (params != null) {

            synchronized(lockObject) {
                instance.params = params;
            }
        }

        return instance;
    }


    /**
     * Private constructor
     */
    private Dex() {}


    /**
     * Check if the Dex is ready to work
     * @return  true - if already, false otherwise
     */
    public boolean isPrepared() {
        return prepared;
    }


    /**
     * Returns the {@code Params} parameters
     * @return  the {@code Params} parameters are needed for work
     */
    public Params getParams() {
        return params;
    }


    /**
     * Prepare instance file before start working, if file not exist, then copy from assets
     * @param handler - Handler for work with UI thread
     * @param success - Interface to return success result finish work
     * @param failure - Interface to return failure result finish work
     */
    public void prepareDexFile(Handler handler, ToDoInterface success, ToDoInterface failure) {
        stopIfEmptyParams();

        Utils.runInNewThread(() -> {

            prepared = false;
            if (Utils.isExists(params.context, params.fileName)) {
                prepared = true;

                try {

                    if (params.fileVersion > getInstanceClassFromDex("parser.Parser").getVersion()) {
                        reset();

                        new File(Utils.getFileFullName(params.context, params.fileName)).delete();
                        if (Utils.copyAssets(params.context, params.fileName, Utils.getFileFullName(params.context, params.fileName))) {
                            prepared = params.md5AssetsFile.equals(Utils.getMD5(Utils.getFileFullName(params.context, params.fileName)));
                        }
                    }
                } catch (Throwable ignored) { }
            } else {
                if (Utils.copyAssets(params.context, params.fileName, Utils.getFileFullName(params.context, params.fileName))) {
                    prepared = params.md5AssetsFile.equals(Utils.getMD5(Utils.getFileFullName(params.context, params.fileName)));
                }
            }

            if (handler != null) {

                handler.post(() -> {

                    if (prepared) {
                        if (success != null) success.todo();
                    } else {
                        if (failure != null)  failure.todo();
                    }
                });
            }
        });
    }


    /**
     * Update old instance on new downloaded from server
     * @return  true - if update finished success, false - if update failed.
     */
    public boolean updateOldDex() {
        stopIfEmptyParams();

        if (new File(Utils.getFileFullName(params.context, params.fileName)).delete()) {
            File dir = params.context.getDir("dex", Context.MODE_PRIVATE);
            if(dir.exists()) {
                File from = new File(dir, params.downloadFileName);
                File to = new File(dir, params.fileName);
                if(from.exists()) {
                    return from.renameTo(to);
                }
            }
        }

        return false;
    }


    /**
     * Reset var dexClassLoader for new init
     */
    public Dex reset() {

        prepared = false;
        classes.clear();
        objects.clear();
        resetDexClassLoader();
        return this;
    }


    /**
     * Reset var dexClassLoader for new init
     */
    public Dex resetDexClassLoader() {
        dexClassLoader = null;
        return this;
    }


    /**
     * Like {@link #getInstanceClassFromDex(String className, boolean needNewInstance)}
     * with {@code needNewInstance = false}
     */
    public Versioned getInstanceClassFromDex(String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException, FileNotFoundException {
        return getInstanceClassFromDex(className, false);
    }

    /**
     * Singleton for few classes from instance file
     * @param className - class name with package, sample "com.example.Class"
     * @param needNewInstance - true - if need replace old instance, false - use current instance if exist
     * @return  Instance of the specified class, by {@code className}
     * @throws  FileNotFoundException - if the file was not found
     * @throws  ClassNotFoundException - if the class was not found
     * @throws  IllegalAccessException - if the class or its nullary constructor is not accessible.
     * @throws  InstantiationException - if this {@code Class} represents an abstract class,
     *          an interface, an array class, a primitive type, or void;
     *          or if the class has no nullary constructor;
     *          or if the instantiation fails for some other reason.
     */
    public Versioned getInstanceClassFromDex(String className, boolean needNewInstance) throws ClassNotFoundException, IllegalAccessException, InstantiationException, FileNotFoundException {

        if (needNewInstance) {
            classes.remove(className);
            objects.remove(className);
        }

        Class clazz = null;
        if (classes.containsKey(className)) {
            clazz = classes.get(className);
        } else {
            clazz = getClassFromDex(className);
            classes.put(className, clazz);
        }

        Versioned obj = null;
        if (objects.containsKey(className)) {
            obj = objects.get(className);
        } else {
            obj = (Versioned) clazz.newInstance();
            objects.put(className, obj);
        }

        return obj;
    }


    /**
     * Load and create class from instance file
     * @param className - class name with package, sample "com.example.Class"
     * @return  Instance of the specified class, by {@code className}
     * @throws  FileNotFoundException - if the file was not found
     * @throws  ClassNotFoundException - if the class was not found
     */
    public Class getClassFromDex(String className) throws ClassNotFoundException, FileNotFoundException {
        return getDexClassLoader().loadClass(className);
    }


    /**
     * Singleton for DexClassLoader
     * @return  Instance of the DexClassLoader
     * @throws  FileNotFoundException - if the file was not found
     */
    private DexClassLoader getDexClassLoader() throws FileNotFoundException {

        if (dexClassLoader == null) {

            String fullNameFile = Utils.getFileFullName(params.context, params.fileName);
            if (!Utils.isExists(fullNameFile)) throw new FileNotFoundException();

            File codeCacheDir = Utils.getCodeCacheDir(params.context);
            ClassLoader classLoader = params.context.getClassLoader();
            dexClassLoader = new DexClassLoader(fullNameFile, codeCacheDir.getAbsolutePath(), null, classLoader);
        }

        return dexClassLoader;
    }

    /**
     * Checks parameters and throws an exception if one or more parameters are not specified
     */
    private void stopIfEmptyParams() {
        if (params == null || params.context == null
                || params.fileName == null || params.fileName.isEmpty()
                || params.md5AssetsFile == null || params.md5AssetsFile.isEmpty()
                || params.downloadFileName == null || params.downloadFileName.isEmpty())
            throw new IllegalStateException("One or more parameters are not specified.");
    }


    public static class Params {

        Context context;

        /**
         * File name with extension. Sample: "file.instance"
         */
        String fileName;

        /**
         * File version with extension. Sample: "file.instance"
         */
        int fileVersion;

        /**
         * MD5 hash of the file in assets
         */
        String md5AssetsFile;

        /**
         * File name downloaded from server with extension. Sample: "downloaded.instance"
         */
        String downloadFileName;


        public Params(Context context, String fileName, int fileVersion, String md5AssetsFile, String downloadFileName) {
            this.context = context;
            this.fileName = fileName;
            this.fileVersion = fileVersion;
            this.md5AssetsFile = md5AssetsFile;
            this.downloadFileName = downloadFileName;
        }


        public Params setContext(ContextWrapper context) {
            this.context = context;
            return this;
        }

        public Params setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Params setFileVersion(int fileVersion) {
            this.fileVersion = fileVersion;
            return this;
        }

        public Params setMd5AssetsFile(String md5AssetsFile) {
            this.md5AssetsFile = md5AssetsFile;
            return this;
        }

        public Params setDownloadFileName(String downloadFileName) {
            this.downloadFileName = downloadFileName;
            return this;
        }
    }
}
