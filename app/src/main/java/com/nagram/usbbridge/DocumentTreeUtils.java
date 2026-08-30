package com.nagram.usbbridge;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Locale;

final class DocumentTreeUtils {
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

    static String mimeFor(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name);
        if (ext != null && !ext.isEmpty()) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }
}
