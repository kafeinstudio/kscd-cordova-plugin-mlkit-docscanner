var exec = require('cordova/exec');

var SERVICE = 'MLKitDocScanner';

/**
 * Le scanner ML Kit s'ouvre dans une activité externe, donc l'application passe
 * en arrière-plan pendant ce temps. Sur les ROM agressives (MIUI, One UI Core),
 * le système peut alors la tuer — c'est le même problème que l'appareil photo,
 * contourné dans app.controller.js par enableBackgroundMode()/disableBackgroundMode().
 *
 * On reproduit ce contournement ici, mais piloté par l'appelant : le plugin
 * reste générique et n'a pas à connaître le paramètre cameraCompat de l'app.
 */
function hasBackgroundMode() {
    return !!(window.cordova && window.cordova.plugins && window.cordova.plugins.backgroundMode);
}

module.exports = {
    /**
     * Ouvre le scanner de documents natif (ML Kit / Google Play Services).
     *
     * Le viseur, la détection des bords en direct, le déclenchement automatique,
     * le redressement et les filtres sont fournis par Android. L'application ne
     * gère ni la caméra ni le traitement d'image.
     *
     * options :
     *   pageLimit      nombre de pages maximum (défaut 1)
     *   galleryImport  autorise l'import depuis la galerie (défaut false)
     *   pdf            renvoie aussi un PDF en plus des JPEG (défaut false)
     *   mode           'base'   : cadrage + redressement
     *                  'filter' : + filtres de rendu (N&B, couleur)
     *                  'full'   : + retouche, rotation, suppression de page (défaut)
     *   copyToCache    recopie les fichiers dans le cache de l'app et renvoie des
     *                  file:// utilisables par <img> et FileService (défaut true).
     *                  À false, renvoie les content:// bruts de ML Kit.
     *   backgroundMode passe l'app en arrière-plan pendant le scan pour éviter
     *                  qu'une ROM agressive ne la tue (défaut false). À activer
     *                  dans les mêmes conditions que pour l'appareil photo,
     *                  c'est-à-dire quand le paramètre cameraCompat est actif.
     *                  Sans cordova-plugin-background-mode, l'option est ignorée.
     *
     * Résout avec { cancelled, pages: [url, …], pdf?: { uri, pageCount } }
     * Un abandon par l'utilisateur résout avec cancelled: true — ce n'est pas une erreur.
     */
    scan: function (options) {
        var opts  = options || {};
        var useBg = opts.backgroundMode === true && hasBackgroundMode();

        if (useBg) {
            console.log('[MLKitDocScanner] passage en arrière-plan');
            window.cordova.plugins.backgroundMode.moveToBackground();
        }

        // Le retour doit se faire quelle que soit l'issue : succès, erreur, annulation.
        function restore() {
            if (!useBg) {
                return;
            }
            try {
                console.log('[MLKitDocScanner] retour au premier plan');
                window.cordova.plugins.backgroundMode.moveToForeground();
            } catch (e) {
                console.log('[MLKitDocScanner] retour au premier plan impossible : ' + e);
            }
        }

        return new Promise(function (resolve, reject) {
            exec(
                function (res) { restore(); resolve(res); },
                function (err) { restore(); reject(err); },
                SERVICE,
                'scan',
                [opts]
            );
        });
    },

    /** Indique si le contournement arrière-plan est disponible sur ce build. */
    hasBackgroundMode: hasBackgroundMode
};
