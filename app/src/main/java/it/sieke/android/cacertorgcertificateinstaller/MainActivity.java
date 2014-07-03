package it.sieke.android.cacertorgcertificateinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        installButton = (Button) findViewById(R.id.addCertButton);
        textViewLog = (TextView) findViewById(R.id.textViewLog);

        packageName = this.getPackageName();
        certsInstalled = checkCertInstalled();
        refreshInstallMode();

        AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setMessage(getResources().getString(R.string.welcometext) + "\n\n" + "This app is licensed under GPL. However you should not trust it blindly and check if the correct certificates were added.");
        ad.setButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();

        if (!RootTools.isRootAvailable()) {
            //TODO root not available!
        } else {
            writeLog("found su");
        }

        if (!RootTools.isAccessGiven()) {
            //TODO no access
            Log.w(packageName, Environment.getRootDirectory().toString() + "NOOOOOO ACCESS!!!! :-(");
        } else {
            writeLog("root access was granted");
        }


        installButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                installButtonClicked();
            }
        });
    }

    private void installButtonClicked() {
        boolean certsInstalledOld = certsInstalled;
        if (!certsInstalled) {
            try {
                writeLog("starting to install certificates");
                installCerts();
            } catch (IOException e) {
                Log.w(packageName, "error: " + e.getMessage());
            } catch (RootDeniedException e) {
                Log.w(packageName, "error: " + e.getMessage());
            } catch (TimeoutException e) {
                Log.w(packageName, "error: " + e.getMessage());
            }
        } else {
            try {
                writeLog("starting to uninstall certificates");
                uninstallCerts();
            } catch (IOException e) {
                Log.w(packageName, "error: " + e.getMessage());
            } catch (RootDeniedException e) {
                Log.w(packageName, "error: " + e.getMessage());
            } catch (TimeoutException e) {
                Log.w(packageName, "error: " + e.getMessage());
            }
        }

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
        }

        certsInstalled = checkCertInstalled();

        if (!certsInstalledOld && certsInstalled) {
            writeLog("=== FINISH === " + System.getProperty("line.separator") + "everything went fine, certificates are now installed!");
        }

        if (certsInstalledOld && !certsInstalled) {
            writeLog("=== FINISH === " + System.getProperty("line.separator") + "everything went fine, certificates are now removed!");
        }

        refreshInstallMode();

    }

    private void refreshInstallMode() {
        if (certsInstalled) {
            installButton.setText(R.string.delCertButtonText);
        } else {
            installButton.setText(R.string.addCertButtonText);
        }
    }


    private void installCerts() throws IOException, TimeoutException, RootDeniedException {
        writeLog("writing temporary files to sdcard");

        moveRawFileToSdCard(R.raw.cert_5ed36f99_0, "5ed36f99.0");
        moveRawFileToSdCard(R.raw.cert_e5662767_0, "e5662767.0");

        writeLog("remounting system partition for writing");
        executeCommand("su -c 'mount -o remount,rw /system'");

        writeLog("copying certificates to system partition");
        executeCommand("su -c 'cat " + Environment.getExternalStorageDirectory() + "/5ed36f99.0 > /system/etc/security/cacerts/5ed36f99.0'");
        executeCommand("su -c 'cat " + Environment.getExternalStorageDirectory() + "/e5662767.0 > /system/etc/security/cacerts/e5662767.0'");

        writeLog("writing file permissions");
        executeCommand("su -c 'chmod 644 /system/etc/security/cacerts/5ed36f99.0'");
        executeCommand("su -c 'chmod 644 /system/etc/security/cacerts/e5662767.0'");

        writeLog("remounting system partition to read only");
        executeCommand("su -c 'mount -o remount,ro /system'");
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


    private void uninstallCerts() throws IOException, RootDeniedException, TimeoutException {
        writeLog("remounting system partition for writing");
        executeCommand("su -c 'mount -o remount,rw /system'");

        writeLog("removing certificate files");
        executeCommand("su -c 'rm /system/etc/security/cacerts/5ed36f99.0'");
        executeCommand("su -c 'rm /system/etc/security/cacerts/e5662767.0'");

        writeLog("remounting system partition to read only");
        executeCommand("su -c 'mount -o remount,ro /system'");
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
        textViewLog.setText(text + System.getProperty("line.separator") + System.getProperty("line.separator") + textViewLog.getText());
    }
}
