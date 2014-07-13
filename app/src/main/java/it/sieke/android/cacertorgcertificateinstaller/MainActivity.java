/*
This file is part of CACert.org certificate installer.

CACert.org certificate installer is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

CACert.org certificate installer is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with CACert.org certificate installer.  If not, see <http://www.gnu.org/licenses/>.
*/


package it.sieke.android.cacertorgcertificateinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeoutException;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;

public class MainActivity extends Activity {

    private static final String CERTFILE1_PATH = "/system/etc/security/cacerts/5ed36f99.0";
    private static final String CERTFILE1_FINGERPRINT = "a6:1b:37:5e:39:0d:9c:36:54:ee:bd:20:31:46:1f:6b";
    private static final String CERTFILE2_PATH = "/system/etc/security/cacerts/e5662767.0";
    private static final String CERTFILE2_FINGERPRINT = "f7:25:12:82:4e:67:b5:d0:8d:92:b7:7c:0b:86:7a:42";
    private static String CACHE_DIR;

    public static String packageName;
    Button installButton;
    TextView textViewLog;
    boolean operationInProgress = false;
    private boolean certsInstalled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CACHE_DIR = getApplicationContext().getCacheDir().getAbsolutePath();
        installButton = (Button) findViewById(R.id.addCertButton);
        textViewLog = (TextView) findViewById(R.id.textViewLog);
        packageName = this.getPackageName();

        certsInstalled = checkCertInstalled();


        if (dieIfRootUnavailable()) {
            AlertDialog adWelcome = new AlertDialog.Builder(this).create();
            adWelcome.setMessage(this.getString(R.string.welcome_message));
            adWelcome.setButton(this.getString(R.string.btOk), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            adWelcome.show();
        }

        refreshInstallMode();

        installButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                installButtonClicked();
            }
        });


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
        if (id == R.id.action_about) {
            AboutBox.Show(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private boolean dieIfRootUnavailable() {
        if (!RootTools.isRootAvailable() || !RootTools.isAccessGiven()) {
            AlertDialog ad = new AlertDialog.Builder(this).create();
            ad.setCancelable(false); // This blocks the 'BACK' button
            ad.setMessage(this.getString(R.string.no_root_message));
            ad.setButton(this.getString(R.string.btOk), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    System.exit(0);
                }
            });
            ad.show();
        } else {
            writeLog(this.getString(R.string.root_granted));
            return true;
        }
        return false;
    }


    private void installButtonClicked() {
        if (operationInProgress) {
            writeLog(this.getString(R.string.op_in_progress));
            return;
        }
        operationInProgress = true;
        new InstallerTask().execute(!certsInstalled);
    }

    private void refreshInstallMode() {
        if (certsInstalled) {
            installButton.setText(R.string.del_cert_button_text);
        } else {
            installButton.setText(R.string.add_cert_button_text);
        }
    }

    private void checkInstallSuccess() {
        boolean certsInstalledOld = certsInstalled;
        certsInstalled = checkCertInstalled();

        if (certsInstalledOld != certsInstalled) {
            writeLog(this.getString(R.string.finish_fine));
        } else {
            writeLog(this.getString(R.string.finish_error));
        }

        refreshInstallMode();
    }

    private void moveRawFileToSdCard(int resource, String targetFilename) throws IOException {
        InputStream in = getResources().openRawResource(resource);
        FileOutputStream out = new FileOutputStream(CACHE_DIR + "/" + targetFilename);

        byte[] buff = new byte[1024];
        int read;
        while ((read = in.read(buff)) > 0) {
            out.write(buff, 0, read);
        }
        in.close();
        out.close();
    }

    private void executeCommand(String command) throws TimeoutException, RootDeniedException, IOException {
        Log.w(packageName, getString(R.string.executing) + " \"" + command + "\"");

        Command cmd = new Command(0, command) {
            @Override
            public void commandOutput(int i, String s) {
                if (!s.isEmpty())
                    Log.w(packageName, getString(R.string.command_output) + ": \"" + s + "\"");
            }

            @Override
            public void commandTerminated(int i, String s) {
                if (!s.isEmpty())
                    Log.w(packageName, getString(R.string.command_terminated) + ": \"" + s + "\"");
            }

            @Override
            public void commandCompleted(int i, int i2) {
            }

        };

        RootTools.getShell(true).add(cmd);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean checkCertInstalled() {
        File certFile1 = new File(CERTFILE1_PATH);
        File certFile2 = new File(CERTFILE2_PATH);

        if (!certFile1.exists() || !certFile2.exists()) {
            return false;
        }

        String computedCertFile1Fingerprint = getThumbPrintString(certFile1);
        String computedCertFile2Fingerprint = getThumbPrintString(certFile2);

        writeLog(getString(R.string.fingerprint_for) + " " + certFile1.getName() + ": " + computedCertFile1Fingerprint);
        writeLog(getString(R.string.fingerprint_for) + " " + certFile2.getName() + ": " + computedCertFile2Fingerprint);


        if (!computedCertFile1Fingerprint.equalsIgnoreCase(CERTFILE1_FINGERPRINT) || !computedCertFile2Fingerprint.equalsIgnoreCase(CERTFILE2_FINGERPRINT)) {
            writeLog(getString(R.string.fingerprints_invalid));
            return false;
        } else {
            writeLog(getString(R.string.fingerprints_valid));
        }

        return true;
    }

    /*private String getFileMd5(File file) throws IOException, NoSuchAlgorithmException {
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
    }*/

    private void writeLog(String text) {
        textViewLog.setText(textViewLog.getText() + "\n\n" + text);
        final ScrollView sc = (ScrollView) findViewById(R.id.scrollView);
        sc.post(new Runnable() {
            @Override
            public void run() {
                sc.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

        Log.w(this.getPackageName(), text);
    }

    public String getThumbPrint(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("md5");
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return hexify(digest);
    }

    public String getThumbPrintString(File certfile) {
        FileInputStream is;
        String output = "ERROR";

        try {
            is = new FileInputStream(certfile);
            CertificateFactory x509CertFact = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) x509CertFact.generateCertificate(is);
            output = getThumbPrint(cert);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }


        return output;
    }

    public String hexify(byte bytes[]) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        StringBuffer buf = new StringBuffer(bytes.length * 2 + bytes.length);

        for (byte aByte : bytes) {
            buf.append(hexDigits[(aByte & 0xf0) >> 4]);
            buf.append(hexDigits[aByte & 0x0f]);
            buf.append(':');
        }

        return buf.toString().substring(0, buf.toString().length() - 1);
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

            publishProgress("\n\n\n");

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
            publishProgress(getString(R.string.write_tmp_file_sd));

            moveRawFileToSdCard(R.raw.cert_5ed36f99_0, "5ed36f99.0");
            moveRawFileToSdCard(R.raw.cert_e5662767_0, "e5662767.0");

            publishProgress(getString(R.string.remounting_rw));
            executeCommand("mount -o remount,rw /system");

            publishProgress(getString(R.string.copying_cert_files_to_system));
            executeCommand("cat " + CACHE_DIR + "/5ed36f99.0 > /system/etc/security/cacerts/5ed36f99.0");
            executeCommand("cat " + CACHE_DIR + "/e5662767.0 > /system/etc/security/cacerts/e5662767.0");

            publishProgress(getString(R.string.writing_file_permissions));
            executeCommand("chmod 644 /system/etc/security/cacerts/5ed36f99.0");
            executeCommand("chmod 644 /system/etc/security/cacerts/e5662767.0");

            publishProgress(getString(R.string.remounting_ro));
            executeCommand("mount -o remount,ro /system");

            publishProgress(getString(R.string.deleteing_tmp_files));
            executeCommand("rm " + CACHE_DIR + "/5ed36f99.0");
            executeCommand("rm " + CACHE_DIR + "/e5662767.0");

        }

        private void uninstallCerts() throws IOException, RootDeniedException, TimeoutException {
            publishProgress(getString(R.string.remounting_rw));
            executeCommand("mount -o remount,rw /system");

            publishProgress(getString(R.string.removing_cert_files));
            executeCommand("rm /system/etc/security/cacerts/5ed36f99.0");
            executeCommand("rm /system/etc/security/cacerts/e5662767.0");

            publishProgress(getString(R.string.remounting_ro));
            executeCommand("mount -o remount,ro /system");
        }

        protected void onPostExecute(Void result) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            operationInProgress = false;

            checkInstallSuccess();
        }
    }
}
