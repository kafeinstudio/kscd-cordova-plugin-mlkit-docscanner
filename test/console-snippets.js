/*
 * Aides de test pour cordova-plugin-mlkit-docscanner.
 * À coller dans la console chrome://inspect (APK debug). Rien de ce fichier n'est inclus dans l'app.
 *
 * Installation :
 *   cordova plugin add ./plugins-local/cordova-plugin-mlkit-docscanner
 *   npm run run:android
 *
 * Désinstallation :
 *   cordova plugin rm cordova-plugin-mlkit-docscanner
 *
 * Suivre les logs :
 *   adb logcat -s MLKitDocScanner
 *
 * Vérifier les prérequis du terminal :
 *   adb shell dumpsys package com.google.android.gms | grep versionName   # Play Services présent ?
 *   adb shell cat /proc/meminfo | head -1                                 # RAM totale (ML Kit exige ~1,7 Go)
 */

/* ------------------------------------------------------------------
 * 1. Scan simple — c'est le test qui répond à la question du POC.
 *    Compare le nombre de gestes et le temps avec le parcours actuel.
 * ---------------------------------------------------------------- */
var t0 = Date.now();
MLKitDocScanner.scan().then(function (r) {
    console.log('Durée totale :', ((Date.now() - t0) / 1000).toFixed(1), 's');
    console.log('Annulé :', r.cancelled, '· pages :', r.pages.length);
    console.log(r.pages);
    showPages(r.pages);
}).catch(function (e) { console.error('ÉCHEC :', e); });


/* ------------------------------------------------------------------
 * 2. Multi-pages + PDF — ce que le pipeline actuel ne sait pas faire.
 * ---------------------------------------------------------------- */
MLKitDocScanner.scan({pageLimit: 5, pdf: true, galleryImport: true})
    .then(function (r) {
        console.log('Pages :', r.pages.length, '· PDF :', r.pdf);
        showPages(r.pages);
    })
    .catch(function (e) { console.error('ÉCHEC :', e); });


/* ------------------------------------------------------------------
 * 3. Comparer les modes de rendu sur le MÊME document.
 *    'base' = cadrage seul, 'filter' = avec les filtres N&B de ML Kit.
 *    C'est la comparaison à faire face au pipeline P2 du banc.
 * ---------------------------------------------------------------- */
MLKitDocScanner.scan({mode: 'base'}).then(function (r) { showPages(r.pages, 'base'); });
MLKitDocScanner.scan({mode: 'filter'}).then(function (r) { showPages(r.pages, 'filter'); });


/* ------------------------------------------------------------------
 * Affiche les pages par-dessus l'app, avec leurs dimensions réelles.
 * Cliquer sur l'image la referme.
 * ---------------------------------------------------------------- */
function showPages(urls, label) {
    var box = document.createElement('div');
    box.style.cssText = 'position:fixed;inset:0;z-index:99999;background:#000;overflow:auto;padding:10px';
    box.onclick = function () { box.remove(); };

    if (label) {
        var h = document.createElement('div');
        h.textContent = label;
        h.style.cssText = 'color:#fff;font:14px sans-serif;padding:4px 0';
        box.appendChild(h);
    }

    urls.forEach(function (u) {
        var i = new Image();
        i.onload = function () {
            console.log(u, '→', i.naturalWidth + '×' + i.naturalHeight);
        };
        i.src = u;
        i.style.cssText = 'width:100%;display:block;margin-bottom:8px';
        box.appendChild(i);
    });

    document.body.appendChild(box);
}


/* ------------------------------------------------------------------
 * Récupérer les fichiers sur le PC pour les charger dans bench-scan.html
 * (section « Sorties externes ») :
 *
 *   adb shell run-as com.kafein.cotiere ls cache/
 *   adb exec-out run-as com.kafein.cotiere cat cache/scan_XXXX.jpg > scan_XXXX.jpg
 * ---------------------------------------------------------------- */
