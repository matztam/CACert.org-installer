package it.sieke.android.cacertorgcertificateinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;


import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;


public class MainActivity extends Activity {

    public static String packageName;

    private static String certFile1Path = "/system/etc/security/cacerts/5ed36f99.0";
    private static String certFile1Md5 = "fb262d55709427e2e9acadf2c1298c99";

    private static String certFile2Path = "/system/etc/security/cacerts/e5662767.0";
    private static String certFile2Md5 = "95c1c1820c0ed1de88d512cb10e25182";

    private boolean certsInstalled = false;

    Button installButton;
    TextView textViewLog;
    boolean operationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        installButton = (Button) findViewById(R.id.addCertButton);
        textViewLog = (TextView) findViewById(R.id.textViewLog);


        packageName = this.getPackageName();
        certsInstalled = checkCertInstalled();
        refreshInstallMode();


        if (dieIfRootUnavailable()) {
            AlertDialog ad = new AlertDialog.Builder(this).create();
            ad.setMessage("This app is licensed under GPL. However, you should not trust it blindly and check if the correct certificates were added.");
            ad.setButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            ad.show();
        }


        installButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                installButtonClicked();
            }
        });
    }

    private boolean dieIfRootUnavailable() {
        if (!RootTools.isRootAvailable() || !RootTools.isAccessGiven()) {
            AlertDialog ad = new AlertDialog.Builder(this).create();
            ad.setCancelable(false); // This blocks the 'BACK' button
            ad.setMessage("This app requires root access.\n\nBecause root seems not to be available, or root access was not granted it will now quit.");
            ad.setButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    System.exit(0);
                }
            });
            ad.show();
        } else {
            writeLog("root access was granted");
            return true;
        }
        return false;
    }


    private void installButtonClicked() {
        if (operationInProgress) {
            writeLog("Please wait. Operation in progress!");
            return;
        }
        operationInProgress = true;
        new InstallerTask().execute(!certsInstalled);
    }

    private void refreshInstallMode() {
        if (certsInstalled) {
            installButton.setText(R.string.delCertButtonText);
        } else {
            installButton.setText(R.string.addCertButtonText);
        }
    }

    private void checkInstallSuccess() {
        boolean certsInstalledOld = certsInstalled;
        certsInstalled = checkCertInstalled();

        if (certsInstalledOld != certsInstalled) {
            writeLog("=== FINISH === " + System.getProperty("line.separator") + "everything went fine");
        } else {
            writeLog("=== ERROR === " + System.getProperty("line.separator") + "the operation was not successful");
        }

        refreshInstallMode();
    }

    private void moveRawFileToSdCard(int ressource, String targetFilename) throws IOException {
        InputStream in = getResources().openRawResource(ressource);
        FileOutputStream out = new FileOutputStream(Environment.getExternalStorageDirectory() + "/" + targetFilename);

        byte[] buff = new byte[1024];
        int read = 0;
        while ((read = in.read(buff)) > 0) {
            out.write(buff, 0, read);
        }
        in.close();
        out.close();
    }

    private void executeCommand(String command) throws TimeoutException, RootDeniedException, IOException {
        Log.w(packageName, "executing \"" + command + "\"");

        Command cmd = new Command(0, command) {
            @Override
            public void commandOutput(int i, String s) {
                if (!s.isEmpty())
                    Log.w(packageName, "command output: \"" + s + "\"");
            }

            @Override
            public void commandTerminated(int i, String s) {
                if (!s.isEmpty())
                    Log.w(packageName, "command terminated: \"" + s + "\"");
            }

            @Override
            public void commandCompleted(int i, int i2) {
            }

        };

        RootTools.getShell(true).add(cmd);
    }

    private boolean checkCertInstalled() {
        File certFile1 = new File(certFile1Path);
        File certFile2 = new File(certFile2Path);

        if (!certFile1.exists() || !certFile2.exists()) {
            return false;
        }

        try {
            if (!getFileMd5(certFile1).equals(certFile1Md5) || !getFileMd5(certFile2).equals(certFile2Md5)) {
                return false;
            }
        } catch (IOException e) {
            Log.w(packageName, "error: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            Log.w(packageName, "error: " + e.getMessage());
        }

        return true;
    }

    private String getFileMd5(File file) throws IOException, NoSuchAlgorithmException {
        FileInputStream fileInputStream = new FileInputStream(file.getPath());
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");

        //Using MessageDigest update() method to provide input
        byte[] buffer = new byte[8192];
        int numOfBytesRead;
        while ((numOfBytesRead = fileInputStream.read(buffer)) > 0) {
            messageDigest.update(buffer, 0, numOfBytesRead);
        }

        byte[] digestResult = messageDigest.digest();


        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < digestResult.length; i++)
            sb.append(Integer.toString((digestResult[i] & 0xff) + 0x100, 16).substring(1));


        return sb.toString();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void writeLog(String text) {
        textViewLog.setText(textViewLog.getText() + System.getProperty("line.separator") + System.getProperty("line.separator") + text);
        final ScrollView sc = (ScrollView) findViewById(R.id.scrollView);
        sc.post(new Runnable() {
            @Override
            public void run() {
                sc.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }


    public class InstallerTask extends AsyncTask<Boolean, String, Void> {
        TextView textViewLog;

        @Override
        protected void onPreExecute() {
            textViewLog = (TextView) findViewById(R.id.textViewLog);
        }

        @Override
        protected Void doInBackground(Boolean... params) {
            boolean install = params[0];

            try {
                if (install) {
                    installCerts();
                } else {
                    uninstallCerts();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            } catch (RootDeniedException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            MainActivity.this.writeLog(values[0]);
        }

        private void installCerts() throws IOException, TimeoutException, RootDeniedException {
            publishProgress("writing temporary files to sdcard");

            moveRawFileToSdCard(R.raw.cert_5ed36f99_0, "5ed36f99.0");
            moveRawFileToSdCard(R.raw.cert_e5662767_0, "e5662767.0");

            publishProgress("remounting system partition for writing");
            executeCommand("su -c 'mount -o remount,rw /system'");

            publishProgress("copying certificates to system partition");
            executeCommand("su -c 'cat " + Environment.getExternalStorageDirectory() + "/5ed36f99.0 > /system/etc/security/cacerts/5ed36f99.0'");
            executeCommand("su -c 'cat " + Environment.getExternalStorageDirectory() + "/e5662767.0 > /system/etc/security/cacerts/e5662767.0'");

            publishProgress("writing file permissions");
            executeCommand("su -c 'chmod 644 /system/etc/security/cacerts/5ed36f99.0'");
            executeCommand("su -c 'chmod 644 /system/etc/security/cacerts/e5662767.0'");

            publishProgress("remounting system partition to read only");
            executeCommand("su -c 'mount -o remount,ro /system'");
        }


        private void uninstallCerts() throws IOException, RootDeniedException, TimeoutException {
            publishProgress("remounting system partition for writing");
            executeCommand("su -c 'mount -o remount,rw /system'");

            publishProgress("removing certificate files");
            executeCommand("su -c 'rm /system/etc/security/cacerts/5ed36f99.0'");
            executeCommand("su -c 'rm /system/etc/security/cacerts/e5662767.0'");

            publishProgress("remounting system partition to read only");
            executeCommand("su -c 'mount -o remount,ro /system'");
        }

        protected void onPostExecute(Void result) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
            }

            operationInProgress = false;

            checkInstallSuccess();
        }


    }
}
