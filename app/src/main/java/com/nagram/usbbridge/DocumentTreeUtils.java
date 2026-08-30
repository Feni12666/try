package com.nagram.usbbridge;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DocumentTreeUtils {
    static final class Child {
        final Uri uri;
        final String name;
        final long size;
        final String mime;
        Child(Uri uri, String name, long size, String mime) {
            this.uri = uri; this.name = name; this.size = size; this.mime = mime;
        }
    }

    private DocumentTreeUtils() {}

    static Uri rootDocumentUri(Uri treeUri) {
        return DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
        );
    }

    static String getDisplayName(ContentResolver resolver, Uri docUri) {
        String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor c = resolver.query(docUri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    static long getSize(ContentResolver resolver, Uri docUri) {
        String[] projection = {DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor c = resolver.query(docUri, projection, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {}
        return -1L;
    }

    static boolean readable(ContentResolver resolver, Uri docUri) {
        try (android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(docUri, "r")) {
            return pfd != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Uri findChild(ContentResolver resolver, Uri treeUri, Uri parentDoc, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getDocumentId(parentDoc));
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor c = resolver.query(children, projection, null, null, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String display = c.getString(1);
                if (name.equals(display)) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    static List<Child> childrenWithSize(ContentResolver resolver, Uri treeUri, Uri parentDoc, long size, int limit) {
        List<Child> out = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getDocumentId(parentDoc));
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor c = resolver.query(children, projection, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < limit) {
                long s = c.isNull(2) ? -1L : c.getLong(2);
                String mime = c.getString(3);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                if (s != size) continue;
                String docId = c.getString(0);
                String display = c.getString(1);
                Uri u = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                out.add(new Child(u, display, s, mime));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static String uniqueName(ContentResolver resolver, Uri treeUri, Uri parentDoc, String original) {
        if (findChild(resolver, treeUri, parentDoc, original) == null) return original;

        int dot = original.lastIndexOf('.');
        String stem = dot > 0 ? original.substring(0, dot) : original;
        String ext = dot > 0 ? original.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            String candidate = stem + " (" + i + ")" + ext;
            if (findChild(resolver, treeUri, parentDoc, candidate) == null) return candidate;
        }
        return System.currentTimeMillis() + "_" + original;
    }

    static String safeTempName(String original) {
        String clean = original.replaceAll("[^A-Za-z0-9._() -]", "_");
        if (clean.length() > 90) clean = clean.substring(clean.length() - 90);
        return ".bridge_" + System.currentTimeMillis() + "_" + clean + ".part";
    }

    static Uri rename(ContentResolver resolver, Uri docUri, String finalName) {
        try {
            return DocumentsContract.renameDocument(resolver, docUri, finalName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String mimeFor(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name);
        if (ext != null && !ext.isEmpty()) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }
}
