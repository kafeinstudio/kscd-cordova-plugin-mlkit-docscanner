package com.cotiere.docscanner;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Enveloppe mince autour de ML Kit Document Scanner.
 *
 * Tout le travail — viseur, détection des bords en direct, déclenchement
 * automatique, redressement, filtres, multi-pages — est fait par Play Services.
 * Ce plugin ne fait que lancer l'Intent et récupérer le résultat : c'est ce qui
 * rend l'approche réaliste à maintenir sur une stack Cordova.
 */
public class MLKitDocScannerPlugin extends CordovaPlugin {

    private static final String TAG = "MLKitDocScanner";
    private static final int REQUEST_SCAN = 0x5CA7;

    private CallbackContext pending;
    private boolean copyToCache = true;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext cb) throws JSONException {
        if ("scan".equals(action)) {
            JSONObject opts = args.optJSONObject(0);
            scan(opts == null ? new JSONObject() : opts, cb);
            return true;
        }
        return false;
    }

    private void scan(final JSONObject opts, final CallbackContext cb) {
        if (pending != null) {
            cb.error("Un scan est déjà en cours.");
            return;
        }
        pending = cb;

        final Activity activity = cordova.getActivity();

        GmsDocumentScannerOptions.Builder b = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(opts.optBoolean("galleryImport", false))
                .setPageLimit(Math.max(1, opts.optInt("pageLimit", 1)));

        if (opts.optBoolean("pdf", false)) {
            b.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                               GmsDocumentScannerOptions.RESULT_FORMAT_PDF);
        } else {
            b.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG);
        }

        // base          : cadrage + redressement seulement
        // filter        : + filtres de rendu (N&B, couleur)
        // full (défaut) : + retouche, rotation, suppression de page
        String mode = opts.optString("mode", "full");
        if ("base".equals(mode)) {
            b.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE);
        } else if ("filter".equals(mode)) {
            b.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER);
        } else {
            b.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL);
        }

        this.copyToCache = opts.optBoolean("copyToCache", true);

        final GmsDocumentScanner scanner = GmsDocumentScanning.getClient(b.build());

        scanner.getStartScanIntent(activity)
                .addOnSuccessListener(new OnSuccessListener<IntentSender>() {
                    @Override
                    public void onSuccess(IntentSender sender) {
                        try {
                            cordova.setActivityResultCallback(MLKitDocScannerPlugin.this);
                            activity.startIntentSenderForResult(sender, REQUEST_SCAN, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            fail("Lancement impossible : " + e.getMessage());
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        // Cas attendus : pas de Play Services, RAM < 1,7 Go,
                        // ou modèle pas encore téléchargé et pas de réseau.
                        fail("ML Kit indisponible : " + e.getMessage());
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_SCAN) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (pending == null) {
            return;
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            try {
                JSONObject r = new JSONObject();
                r.put("cancelled", true);
                r.put("pages", new JSONArray());
                pending.success(r);
            } catch (JSONException e) {
                pending.error("Annulation : " + e.getMessage());
            }
            pending = null;
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            fail("Scan interrompu (code " + resultCode + ").");
            return;
        }

        GmsDocumentScanningResult result = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (result == null) {
            fail("Résultat vide.");
            return;
        }

        try {
            JSONObject out = new JSONObject();
            out.put("cancelled", false);

            JSONArray pages = new JSONArray();
            if (result.getPages() != null) {
                int i = 0;
                for (GmsDocumentScanningResult.Page p : result.getPages()) {
                    Uri uri = p.getImageUri();
                    if (uri == null) {
                        continue;
                    }
                    pages.put(copyToCache ? localCopy(uri, "scan_" + System.currentTimeMillis() + "_" + (i++) + ".jpg")
                                          : uri.toString());
                }
            }
            out.put("pages", pages);

            GmsDocumentScanningResult.Pdf pdf = result.getPdf();
            if (pdf != null && pdf.getUri() != null) {
                JSONObject jp = new JSONObject();
                jp.put("uri", copyToCache ? localCopy(pdf.getUri(), "scan_" + System.currentTimeMillis() + ".pdf")
                                          : pdf.getUri().toString());
                jp.put("pageCount", pdf.getPageCount());
                out.put("pdf", jp);
            }

            pending.success(out);
        } catch (Exception e) {
            Log.e(TAG, "Lecture du résultat impossible", e);
            pending.error("Lecture du résultat impossible : " + e.getMessage());
        }
        pending = null;
    }

    /**
     * ML Kit renvoie des content:// dont l'autorisation est temporaire et que la
     * WebView ne sait pas afficher. On recopie donc dans le cache de l'app et on
     * renvoie un file:// directement utilisable par <img> et par FileService.
     */
    private String localCopy(Uri src, String name) throws Exception {
        File dest = new File(cordova.getActivity().getCacheDir(), name);
        InputStream in = null;
        OutputStream out = null;
        try {
            in = cordova.getActivity().getContentResolver().openInputStream(src);
            if (in == null) {
                return src.toString();
            }
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            if (in != null) { try { in.close(); } catch (Exception ignored) {} }
            if (out != null) { try { out.close(); } catch (Exception ignored) {} }
        }
        return Uri.fromFile(dest).toString();
    }

    private void fail(String msg) {
        Log.e(TAG, msg);
        if (pending != null) {
            pending.error(msg);
            pending = null;
        }
    }
}
