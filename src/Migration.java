package cordova.plugins.crosswalk;

/*
 * Imports
 */

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import java.io.File;

import com.github.hf.LevelDB;

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

    private void migrateData(){
        XWalkRoot = constructFilePaths(appRoot, XwalkPath);

        webviewRoot = constructFilePaths(appRoot, getWebviewPath());

        boolean hasMigratedData = false;

        if(testFileExists(XWalkRoot, modernLocalStorageDir)){
            Log.d(TAG, "Local Storage data found");
            moveDirFromXWalkToWebView(modernLocalStorageDir, getWebviewLocalStoragePath());
            Log.d(TAG, "Moved Local Storage from XWalk to System Webview, now migrating from sqlite local storage to leveldb local storage");
            SQLiteDatabase ls = null;
            Cursor results = null;
            LevelDB levelDB = null;
            try {
                ls = SQLiteDatabase.openDatabase(constructFilePaths(webviewRoot, getWebviewLocalStoragePath()).getAbsolutePath() + "/file__0.localstorage", null, SQLiteDatabase.OPEN_READONLY);
                results = ls.rawQuery("SELECT * FROM ItemTable", null);
                levelDB = LevelDB.open(webviewRoot.getAbsolutePath() + "/app_webview/Default/Local Storage/LevelDB", LevelDB.configure().createIfMissing(true));
                while(results.moveToNext()) {
                    Log.d(TAG, "Local Storage Key:" + results.getString(0));
                    byte[] aBlobVariableThatIWillDoAbsolutelyNothingWithPeriodSmileyFace = results.getBlob(1);
                    Log.d(TAG, "Local Storage Value: this is a blob and can't really be displayed but, if you are seeing this message, we successfully got the blob from the result set.");

                    levelDB.put("leveldb".getBytes(), "Is awesome!".getBytes());
                    String value = levelDB.get("leveldb".getBytes());

                    leveldb.put("magic".getBytes(), new byte[] { 0, 1, 2, 3, 4 });
                    byte[] magic = levelDB.getBytes("magic".getBytes());

                    Log.d(TAG, "Used LevelDB");
                };
            } catch (Exception e) {
                Log.d(TAG, "Something went wrong. Here is an error message. " + e.getMessage());
            } finally {
                if (results != null) {
                    results.close();
                }
                if (ls != null) {
                    ls.close();
                }
                if (levelDB != null) {
                    levelDB.close();
                }
            }
            Log.d(TAG, "Finished migrating from localstorage to leveldb.");
            hasMigratedData = true;
        }

        if(isModernAndroid){
            for(String dirName : modernAndroidStorage){
                if(testFileExists(XWalkRoot, dirName)) {
                    moveDirFromXWalkToWebView(dirName);
                    Log.d(TAG, "Moved " + dirName + " from XWalk to System Webview");
                    hasMigratedData = true;
                }
            }
        }

        if(hasMigratedData){
            deleteRecursive(XWalkRoot);
            restartCordova();
        }
    }

    private void moveDirFromXWalkToWebView(String dirName){
        File XWalkLocalStorageDir = constructFilePaths(XWalkRoot, dirName);
        File webviewLocalStorageDir = constructFilePaths(webviewRoot, dirName);
        XWalkLocalStorageDir.renameTo(webviewLocalStorageDir);
    }

    private void moveDirFromXWalkToWebView(String sourceDirName, String targetDirName){
        File XWalkLocalStorageDir = constructFilePaths(XWalkRoot, sourceDirName);
        File webviewLocalStorageDir = constructFilePaths(webviewRoot, targetDirName);
        XWalkLocalStorageDir.renameTo(webviewLocalStorageDir);
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
