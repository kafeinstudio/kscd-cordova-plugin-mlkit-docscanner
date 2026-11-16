# cordova-plugin-mlkit-docscanner

Enveloppe Cordova autour de **ML Kit Document Scanner** (Google Play Services).

Statut : **POC**. Écrit pour répondre à une question de décision — le scanner natif
justifie-t-il d'abandonner le pipeline JS actuel — pas pour partir en production tel quel.

## Ce que fait ML Kit, et que l'app ne fait pas aujourd'hui

- Cadre le document **en direct dans le viseur**, avec déclenchement automatique.
- Redresse la perspective et recadre sans intervention.
- Propose des filtres de rendu, dont un mode noir & blanc type scanner.
- Gère le **multi-pages** et l'export **PDF**.
- Tourne **on-device**, hors ligne, sans coût par image.

Le plugin ne contient ni gestion de caméra, ni traitement d'image : il lance un
`Intent` fourni par Play Services et récupère le résultat. C'est ce qui rend sa
maintenance réaliste sur une stack Cordova.

## Installation

```bash
cordova plugin add ./plugins-local/cordova-plugin-mlkit-docscanner
npm run run:android
```

Retrait :

```bash
cordova plugin rm cordova-plugin-mlkit-docscanner
```

## Utilisation

```js
MLKitDocScanner.scan({pageLimit: 1})
    .then(function (r) {
        if (r.cancelled) { return; }
        console.log(r.pages);   // ['file:///.../scan_1739…_0.jpg']
    })
    .catch(function (e) { console.error(e); });
```

### Options

| Option | Défaut | Rôle |
|---|---|---|
| `pageLimit` | `1` | Nombre de pages maximum |
| `galleryImport` | `false` | Autorise l'import depuis la galerie |
| `pdf` | `false` | Renvoie aussi un PDF en plus des JPEG |
| `mode` | `'full'` | `'base'` cadrage seul · `'filter'` + filtres de rendu · `'full'` + retouche |
| `copyToCache` | `true` | Recopie dans le cache de l'app et renvoie des `file://` |
| `backgroundMode` | `false` | Passe l'app en arrière-plan pendant le scan (voir ci-dessous) |

### Résultat

```js
{ cancelled: false, pages: ['file:///…'], pdf: { uri: 'file:///…', pageCount: 2 } }
```

Un abandon par l'utilisateur **résout** avec `cancelled: true` — ce n'est pas une erreur.

## Pourquoi `copyToCache`

ML Kit renvoie des `content://` dont l'autorisation est temporaire et que la WebView
ne sait pas afficher. Le plugin recopie donc les fichiers dans le cache de
l'application et renvoie des `file://`, directement utilisables par `<img>` et par
`FileService`. Passe à `false` pour obtenir les URIs brutes.

Le cache n'est pas purgé par le plugin : pour une mise en production, il faudrait
déplacer les fichiers retenus vers `cordova.file.dataDirectory`, comme le fait
`FileService.saveBase64ToFile()`.

## Pourquoi `backgroundMode`

Le scanner s'ouvre dans une **activité externe**, donc l'application passe en
arrière-plan pendant le scan. Sur les ROM agressives — MIUI, One UI Core, c'est-à-dire
l'essentiel du parc Côtière — le système peut la tuer à ce moment-là.

L'application contourne déjà ce problème pour l'appareil photo, dans
`app.controller.js:416`, via `cordova.plugins.backgroundMode.moveToBackground()`
puis `moveToForeground()`, sous condition du paramètre `cameraCompat`. Le plugin
reproduit ce mécanisme, mais piloté par l'appelant : il reste générique et n'a pas
à connaître `cameraCompat`.

```js
MLKitDocScanner.scan({
    backgroundMode: appVm.params && appVm.params.cameraCompat === true
});
```

Le retour au premier plan est effectué dans tous les cas — succès, erreur et
annulation. Si `cordova-plugin-background-mode` est absent, l'option est ignorée
sans erreur ; `MLKitDocScanner.hasBackgroundMode()` permet de le savoir.

## Prérequis du terminal

- Google Play Services installés (pas de GMS ⇒ le plugin échoue proprement).
- Environ **1,7 Go de RAM** minimum.
- Android 5.0+ — sans objet ici, le projet est en `minSdkVersion 29`.
- À la **première utilisation**, Play Services télécharge le modèle : il faut du
  réseau une fois. Point à anticiper sur une application pensée hors ligne.

Ces trois cas remontent via `.catch()` avec un message explicite.

## Limites connues

- Android uniquement. Pas d'équivalent iOS dans ce plugin (ce serait `VNDocumentCameraViewController`).
- L'UI du scanner est celle de Google : pas de personnalisation aux couleurs Côtière.
- La version de la dépendance Play Services est en bêta et bouge ; elle est isolée
  dans la préférence `MLKIT_DOCSCANNER_VERSION` du `plugin.xml`.
- Pas encore branché sur `OrderService.postImages()` : c'est volontaire, le POC ne
  touche pas au code de production.

## Tester

Voir `test/console-snippets.js` — à coller dans la console `chrome://inspect`.
Aucune modification du code de l'app n'est nécessaire.
