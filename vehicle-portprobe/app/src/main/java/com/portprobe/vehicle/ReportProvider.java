package com.portprobe.vehicle;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public class ReportProvider extends ContentProvider {
    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri == null || !"com.portprobe.vehicle.reports".equals(uri.getAuthority())) {
            throw new FileNotFoundException("Unknown report authority");
        }
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("PortProbe_[0-9]{8}_[0-9]{9}\\.json")) {
            throw new FileNotFoundException("Invalid report name");
        }
        File file = new File(new File(getContext().getFilesDir(), "reports"), name);
        if (!file.isFile()) throw new FileNotFoundException("No such report");
        return file;
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = resolve(uri);
            String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor c = new MatrixCursor(cols);
            Object[] data = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) data[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) data[i] = f.length();
            }
            c.addRow(data);
            return c;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
