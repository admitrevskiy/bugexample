package com.github.admitrevskiy.bugexample;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;

public class SecondFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_second).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDownload(getActivity(), "https://d-06.winudf.com/b/apk/cnUub2subW9kZXJhdG9yXzMwMDMyOF80NzA4YTdmMQ?_fn=T2Rub2tsYXNzbmlraSBNb2RlcmF0b3JfdjMuM19hcGtwdXJlLmNvbS5hcGs&_p=cnUub2subW9kZXJhdG9y&am=or5Cy3pl5qrny8jycxOTjw&at=1599571752&k=bc85c2acdf83cde6ec27dc7a4ae2cbc35f58d8aa");
            }
        });
    }

    private void startDownload(Activity activity, String url) {
        System.out.println("Start update application task");

        String apkFilePath = getApkFilePath(activity);
        if (apkFilePath == null) {
            Toast.makeText(activity, "Failed to get a file path", Toast.LENGTH_LONG).show();
            System.out.println("Can't get writeable folder for file: " + url);
            return;
        }

        File file = new File(apkFilePath);
        if (file.exists() && !file.delete()) {
            Toast.makeText(activity, "Failed to get a file", Toast.LENGTH_LONG).show();
            return;
        }

        ProgressDialog progressDialog = createProgressDialog(activity);

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setDestinationUri(getFileUri(activity));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

            DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                throw new IOException("Download manager does not exist in this context");
            }

            long downloadId = downloadManager.enqueue(request);

            System.out.println("Downloading apk file from " + url);

            BroadcastReceiver receiver = new DownloadBroadcastReceiver(downloadManager, activity, downloadId, progressDialog);
            activity.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

            System.out.println("Submitted updating application task");
        } catch (Exception e) {
            progressDialog.dismiss();
            Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static Uri getFileUri(Context context) {
        return Uri.parse("file://" + getApkFilePath(context));
    }

    private ProgressDialog createProgressDialog(Activity activity) {
        ProgressDialog dialog = ProgressDialog.show(getActivity(), "Please wait", "Downloading");
        dialog.setOwnerActivity(activity);
        return dialog;
    }

    private static String getApkFilePath(Context context) {
        File cacheDir = context.getExternalCacheDir();

        if (cacheDir == null || !cacheDir.canWrite()) {
            cacheDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }

        if (cacheDir == null || !cacheDir.canWrite()) {
            cacheDir = context.getCacheDir();
        }

        return cacheDir.canWrite() ? cacheDir.getAbsolutePath() + "/" + "bugexample.apk" : null;
    }

    /**
     * Broadcast receiver
     */
    private static class DownloadBroadcastReceiver extends BroadcastReceiver {

        private final DownloadManager downloadManager;

        private final Activity activity;
        private final long downloadId;
        private final ProgressDialog progressDialog;

        DownloadBroadcastReceiver(DownloadManager downloadManager, Activity activity, long downloadId, ProgressDialog progressDialog) {
            super();

            this.downloadManager = downloadManager;
            this.activity = activity;
            this.downloadId = downloadId;
            this.progressDialog = progressDialog;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            System.out.println("Receiver got action " + action);

            activity.unregisterReceiver(this);
            progressDialog.dismiss();

            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);

                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor.moveToFirst()) {
                        applyDownloadedFile(context, cursor);
                    } else {
                        System.out.println("Error while downloading file");
                        Toast.makeText(activity, "Failed to perform cursor.moveToFirst()", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }

        /**
         * Starts downloaded apk
         *
         * @param context Application context
         * @param cursor  Download manager cursor
         */
        private void applyDownloadedFile(Context context, Cursor cursor) {
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                try {
                    Uri downloadUri = getDownloadUri(context);
                    startInstallApk(activity, downloadUri);
                } catch (Exception e) {
                    System.out.println("Error while installing downloaded file: " + e);
                    Toast.makeText(activity, "Error during installation " + e.getMessage(), Toast.LENGTH_LONG).show();

                }
            } else if (DownloadManager.STATUS_FAILED == cursor.getInt(columnIndex)) {
                System.out.println("Error while downloading file: Status failed");
                Toast.makeText(activity, "Status FAILED", Toast.LENGTH_LONG).show();
            }
        }

        private static Uri getDownloadUri(Context context) throws IllegalArgumentException {
            File apkFile = new File(getApkFilePath(context));
            String authority = BuildConfig.APPLICATION_ID + ".file.provider";
            return FileProvider.getUriForFile(context, authority, apkFile);
        }
    }

    private static void startInstallApk(Activity activity, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }
}