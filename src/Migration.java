package cordova.plugins.crosswalk;

/*
 * Imports
 */

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import java.io.File;
import java.lang.reflect.Method;

import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.webkit.WebView;

public class Migration extends CordovaPlugin {

    public static final String TAG = "Migration";

    private static boolean hasRun = false;

    private static String XwalkPath = "app_xwalkcore/Default";

    // Root dir for system webview data used by Android 4.4+
    private static String modernWebviewDir = "app_webview";

    // Root dir for system webview data used by Android 4.3 and below
    private static String oldWebviewDir = "app_database";

    // Directory name for local storage files used by Android 4.4+ and XWalk
    private static String modernLocalStorageDir = "Local Storage";

    // Directory name for local storage files used by Android 4.3 and below
    private static String oldLocalStorageDir = "localstorage";

    // Storage directory names used by Android 4.4+ and XWalk
    private static String[] modernAndroidStorage = {
            "Cache",
            "Cookies",
            "Cookies-journal",
            "IndexedDB",
            "databases"
    };

    private Activity activity;
    private Context context;

    private boolean isModernAndroid;
    private File appRoot;
    private File XWalkRoot;
    private File webviewRoot;

    public Migration() {}

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        Log.d(TAG, "initialize()");

        if(!hasRun){
            hasRun = true;
            activity = cordova.getActivity();
            context = activity.getApplicationContext();
            isModernAndroid = Build.VERSION.SDK_INT >= 19;
            run();
        }

        super.initialize(cordova, webView);
    }

    private void run(){
        Log.d(TAG, "running Crosswalk migration shim");

        boolean found = lookForXwalk(context.getFilesDir());
        if(!found){
            lookForXwalk(context.getExternalFilesDir(null));
        }

        if(found){
            migrateData();
        }
    }

    private boolean lookForXwalk(File filesPath){
        File root = getStorageRootFromFiles(filesPath);
        boolean found = testFileExists(root, XwalkPath);
        if(found){
            Log.d(TAG, "found Crosswalk directory");
            appRoot = root;
        }else{
            Log.d(TAG, "Crosswalk directory NOT FOUND");
        }
        return found;
    }

    private String getHexString(byte[] bytes) {
        StringBuilder sb2 = new StringBuilder(bytes.length * 2);
        for (byte b: bytes) {
            sb2.append(String.format("%02x", b));
        }

        return sb2.toString();
    }

    private void migrateData(){
        XWalkRoot = constructFilePaths(appRoot, XwalkPath);
        webviewRoot = constructFilePaths(appRoot, getWebviewPath());

        boolean hasMigratedData = false;
        File localStorageDir;

        PackageInfo webviewPkg;
        if (Build.VERSION.SDK_INT >= 26) {
            webviewPkg = WebView.getCurrentWebViewPackage();
        }
        else {
            try {
                Class webViewFactory = Class.forName("android.webkit.WebViewFactory");
                Method method = webViewFactory.getMethod("getLoadedPackageInfo");
                webviewPkg = (PackageInfo) method.invoke(null);
                throw new Exception("test");
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        String versionParts[] = webviewPkg.versionName.split("\\.");
        String strVersion = versionParts[0];
        int version = Integer.parseInt(strVersion);

        if (version <= 78) {
            localStorageDir = constructFilePaths(appRoot, "app_webview/Local Storage");
        }
        else {
            localStorageDir = constructFilePaths(appRoot, "app_webview/Default/Local Storage");
        }

        if (testFileExists(XWalkRoot, modernLocalStorageDir)) {

            if (!localStorageDir.exists()) {
                Log.d(TAG, "Missing local storage directory, creating it...");
                localStorageDir.mkdirs();
            }

            File sqliteDBFile = constructFilePaths(XWalkRoot, "Local Storage/file__0.localstorage");
            File sqliteDBJournalFile = constructFilePaths(XWalkRoot, "Local Storage/file__0.localstorage-journal");

            sqliteDBFile.renameTo(constructFilePaths(localStorageDir, "https_localhost_0.localstorage"));
            sqliteDBJournalFile.renameTo(constructFilePaths(localStorageDir, "https_localhost_0.localstorage-journal"));

            hasMigratedData = true;
        }

        if (hasMigratedData){
            Log.d(TAG, "XWALK ROOT DELETE: " + XWalkRoot.getParentFile());
            deleteRecursive(XWalkRoot.getParentFile());
        }
    }

    private String getWebviewPath(){
        if(isModernAndroid){
            return modernWebviewDir;
        }else{
            return oldWebviewDir;
        }
    }

    private String getWebviewLocalStoragePath(){
        if(isModernAndroid){
            return modernLocalStorageDir;
        }else{
            return oldLocalStorageDir;
        }
    }

    private void restartCordova(){
        Log.d(TAG, "restarting Cordova activity");
        activity.recreate();
    }


    private boolean testFileExists(File root, String name) {
        boolean status = false;
        if (!name.equals("")) {
            File newPath = constructFilePaths(root.toString(), name);
            status = newPath.exists();
            Log.d(TAG, "exists '"+newPath.getAbsolutePath()+": "+status);
        }
        return status;
    }

    private File constructFilePaths (File file1, File file2) {
        return constructFilePaths(file1.getAbsolutePath(), file2.getAbsolutePath());
    }

    private File constructFilePaths (File file1, String file2) {
        return constructFilePaths(file1.getAbsolutePath(), file2);
    }

    private File constructFilePaths (String file1, String file2) {
        File newPath;
        if (file2.startsWith(file1)) {
            newPath = new File(file2);
        }
        else {
            newPath = new File(file1 + "/" + file2);
        }
        return newPath;
    }

    private File getStorageRootFromFiles(File filesDir){
        String filesPath = filesDir.getAbsolutePath();
        filesPath = filesPath.replaceAll("/files", "");
        return new File(filesPath);
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }
}
