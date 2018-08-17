package code;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import dex.Versioned;
import dex.Dex;
import dex.Utils;


public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final int LAYOUT = R.layout.activity_main;

    private static final int VERSION_ON_SERVER = 2;
    private static final String URL = "http://arget.com.ua/OTHER/parser.dex";
    private static final String MD5_DEX_ON_SERVER = "a1aeae662d801c6e1853614d213e9063";

    private static final String DOWNLOADED_FILE_NAME = "downloaded.dex";
    private static final String FILE_NAME = "parser.dex";
    private static final String CLASS_NAME = "parser.Parser";


    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(LAYOUT);

        // Init params for Dex
        Dex.Params params = new Dex.Params(MainActivity.this, FILE_NAME, BuildConfig.MD5_DEX_ASSETS, DOWNLOADED_FILE_NAME);
        Handler handler = new Handler(Looper.getMainLooper());

        // Prepare dex file
        Dex.getInstance(params)
           .prepareDexFile(handler, null, null);

        findViewById(R.id.btn_check).setOnClickListener(v -> checkVersionDex());
    }


    private void checkVersionDex() { Log.d(TAG, "checkVersionDex()");
        Utils.safelyWorkWithClass(getVersioned(),
                obj -> {
                    int currentVersion = obj.getVersion();
                    if (currentVersion < VERSION_ON_SERVER) {
                        showToast("has a new version = " + String.valueOf(VERSION_ON_SERVER) + " (current = " + String.valueOf(currentVersion) + ")", false);
                        loadDexFromServer();
                    } else {
                        showToast("latest version is installed (current = " + String.valueOf(currentVersion) + ")", false);
                    }
                },
                () -> showToast("error get version", false));
    }


    private void loadDexFromServer() { Log.d(TAG, "loadDexFromServer() Start download");

        String path = Utils.getFileFullName(MainActivity.this, DOWNLOADED_FILE_NAME);
        showToast("start download url = " + URL, false);
        Utils.downloadFileAsync(URL, path, MD5_DEX_ON_SERVER, new Handler(Looper.getMainLooper()),
                () -> {
                    showToast("success download", false);

                    Dex.getInstance()
                       .reset()
                       .updateOldDex();

                    checkVersionDex();
                },
                () -> showToast("error download", false));
    }


    @Nullable
    public Versioned getVersioned() {
        try {
            return Dex.getInstance().getInstanceClassFromDex(CLASS_NAME);
        } catch (Throwable ignored) {
            return null;
        }
    }


    public void showToast(String text, boolean longMessage) {
        Toast.makeText(MainActivity.this, text, longMessage ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        Log.e(TAG, text);
    }
}
