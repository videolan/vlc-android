# Stratégie de fork VLC Android

Document de référence pour maintenir un fork **léger** de
[videolan/vlc-android](https://github.com/videolan/vlc-android) tout en gardant l'UI VLC,
le support **TV** et un flux de maintenance automatisé.

---

## 1. Objectifs

- Garder l'**UI VLC existante** (player, contrôles, écrans) sans la réécrire.
- Garder les builds **phone ET TV**.
- Ne **pas** embarquer la médiathèque VLC (pas d'onglets vidéo/musique/exploration).
- Ajouter nos besoins :
  - lecture depuis **URL** (http/https) ;
  - lecture de **fichiers locaux** ;
  - lecture de **données depuis une API** ;
  - **listener d'avancement** pour sauvegarder/restaurer la position ;
  - **deep link** pour ouvrir directement une lecture ;
  - publication automatique des **APK en GitHub Release**.

---

## 2. Décision de départ : pourquoi un fork ?

Nous voulons réutiliser l'UI VLC, donc pas de réécriture sur `libvlc` seul.
Un fork est assumé, mais **minimal** : on coupe ce dont on n'a pas besoin,
et on isole toutes nos modifications pour faciliter les merges upstream.

---

## 3. Structure du fork

### 3.1 Modules Gradle conservés

| Module | Rôle | Action |
|---|---|---|
| `libvlc/` | Cœur de lecture (C/C++ + API Java) | **Conserver tel quel**, ne jamais toucher |
| `application/` | L'app Android (UI, player, service) | **Conserver**, c'est ici qu'on travaille |
| `buildsystem/`, `tools/`, `gradle/` | Compilation | **Conserver**, ne pas modifier sauf besoin |

### 3.2 Modules / fonctionnalités supprimés ou neutralisés

| Élément | Pourquoi on le coupe | Comment |
|---|---|---|
| `mediadb/` | On n'indexe pas la médiathèque locale | Désactiver le module et son usage dans `application` |
| Flavour **wear** | Non supporté sur nos devices | Retirer du `productFlavors` de `application` |
| Onglets médiathèque de `MainActivity` | Pas d'explorateur local | Les neutraliser / ne plus exposer d'accès |
| Scan / indexation SQLite | Inutile sans médiathèque | Désactiver les services de scan |

> **Flavour conservés** : `phone` et `tv` uniquement.
> Les deux doivent rester buildables ; la CI devra les compiler tous les deux.

### 3.3 Point d'entrée

`MainActivity` (médiathèque) n'est **plus** l'écran d'accueil.
L'écran d'accueil devient notre propre écran (liste construite depuis l'API),
et le player reste `VideoPlayerActivity` + `PlaybackService` (intacts côté VLC).

---

## 4. Stratégie de modification de code (peu intrusive)

Le principe : **ne jamais modifier les fichiers VLC quand on peut s'en passer**,
et quand on doit le faire, le rendre trivial à merger.

### 4.1 Règles d'or

1. **Toute notre logique vit dans un package séparé**, ex. `org.videolan.vlc.addon`
   (ou `com.<société>.<app>.addon`), jamais dans les packages VLC existants.
2. **Ne jamais toucher à `libvlc/`**.
3. Dans `application/`, on modifie uniquement :
   - le `AndroidManifest.xml` (deep link, écran d'accueil) ;
   - `PlaybackService` **via des points d'extension** quand ils existent ;
   - le minimum de fichiers d'amorçage (menu d'accueil, navigation).
4. **Éviter les refactorings** : pas de renommage, pas de réorganisation de code VLC.

### 4.2 Où placer nos besoins

| Besoin | Implémentation | Touche-t-on VLC ? |
|---|---|---|
| Lire URLs / fichiers locaux | `PlaybackService.openMedia(...)` avec un `Media` construit par nos soins | Non |
| Données depuis l'API | Notre propre couche (récupérer le JSON → construire `MediaItem` → `openMedia`) | Non |
| Écran d'accueil API | Activité propre dans `addon` | Non |
| Listener d'avancement | `PlaybackService` expose la position ; notre listener la lit | Non |
| Sauvegarde/restauration position | `SharedPreferences` côté `addon`, restaurée au lancement | Non |
| Deep link | `DeepLinkActivity` (dans `addon`) + `intent-filter` dans le manifest | Manifest uniquement |

### 4.3 Points de contact minimum avec le code VLC

- `AndroidManifest.xml` : déclarer `DeepLinkActivity`, écran d'accueil custom,
  autorisations réseau. C'est le seul fichier VLC modifié de façon « structurelle ».
- Éventuellement le point de menu / lancement qui redirige `MainActivity` vers
  notre écran d'accueil (si on veut remplacer l'accueil VLC).
- Garder ces modifs **dans un commit séparé et tagué** (voir §5) pour les
  rejouer facilement après un merge.

### 4.4 Convention de commits

- Commits **minimes et mono-thématiques**.
- Un préfixe par type : `feat:` `fix:` `merge:` `chore:`.
- Les modifs « intrusives » (manifest, amorçage) sont **isolées dans un commit
  unique** nommé `chore: minimal vlc integration points`. Ainsi, après un merge
  upstream, c'est le seul commit à re-vérifier à la main.

---

## 5. Automatisation des merges upstream

Le coût principal d'un fork est la désynchronisation. Objectif :
**zero merges manuels routiniers**, seulement des interventions sur conflits.

### 5.1 Réglage Git

```bash
git remote add upstream https://github.com/videolan/vlc-android.git
git fetch upstream
```

### 5.2 Modèle de branches

```
main        ← branche de production (notre fork publié)
staging     ← intègre les merges upstream + nos devs, sert de zone de test
upstream-mirror ← copie régulière de upstream/main (jamais modifiée par nous)
feature/*   ← branches de travail éphémères
```

### 5.3 Workflow de merge automatisé

1. Un bot (cron) `git fetch upstream`.
2. Sur `staging` : `git merge upstream/main`.
3. Si le merge est **propre** → pousser `staging`, la CI compile phone+tv,
   et on promeut vers `main` (via PR ou merge direct automatisé).
4. Si **conflits** :
   - on n'automatise pas ; un humain résout ;
   - la résolution doit rester **du côté du code VLC** (accepter leur version)
     quand c'est possible, pour que nos modifs `addon` ne créent jamais de conflit.

### 5.4 Règle anti-conflit

Puisque notre code vit dans `addon/` (nouveaux fichiers) et que nous ne
touchons quasi que le manifest, la grande majorité des merges upstream
se font **sans conflit**. C'est le bénéfice central de la stratégie §4.

### 5.5 Workflow GitHub Actions : merge automatique

Un workflow `merge-upstream.yml` (cron) :

```yaml
name: Merge upstream

on:
  schedule:
    - cron: "0 6 * * *"   # chaque nuit
  workflow_dispatch: {}

jobs:
  merge:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: staging
          fetch-depth: 0
      - name: Fetch upstream
        run: |
          git remote add upstream https://github.com/videolan/vlc-android.git
          git fetch upstream
      - name: Merge
        run: |
          git config user.name "merge-bot"
          git config user.email "merge-bot@users.noreply.github.com"
          git merge --no-edit upstream/main
        continue-on-error: true
      - name: Push staging
        run: git push origin staging
      - name: Notify on conflict
        if: failure()
        run: |
          # envoie une alerte (issue ou workflow_run) pour intervention manuelle
          echo "Conflit détecté : intervention humaine requise sur staging"
```

> Si le merge échoue, `continue-on-error` pousse quand même `staging`
> (avec le conflit marqué) afin qu'un humain puisse le résoudre,
> ou on utilise un PR automatique « merge-upstream » à la place.

---

## 6. Build + publication GitHub Release (CI)

### 6.1 Prérequis clés de signature

- Keystore stocké en **secrets GitHub** :
  - `ANDROID_KEYSTORE_BASE64` (keystore encodé en base64) ;
  - `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
    `ANDROID_KEY_PASSWORD`.
- Clé de dépôt `GITHUB_TOKEN` fournie automatiquement par `actions/checkout`.
- Fichier `local.properties`/`keystore.properties` non commité (dans `.gitignore`).

### 6.2 Workflow `release.yml`

```yaml
name: Build & Release APK

on:
  push:
    tags: ["v*"]
  workflow_dispatch: {}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: true

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Restore keystore
        run: |
          echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > keystore.jks
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}

      - name: Build phone + tv APK
        run: ./gradlew assemblePhoneRelease assembleTvRelease

      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            application/build/outputs/apk/phone/release/*.apk
            application/build/outputs/apk/tv/release/*.apk
          generate_release_notes: true
```

### 6.3 Points d'attention VLC spécifiques

- VLC est un **multi-module Gradle** : vérifier le chemin réel des APK
  (`application/build/outputs/apk/...` selon le flavour).
- VLC consomme des **AAR distants** (`libvlc`, `medadb`) : pas de sous-modules
  à initialiser en général, mais garder `submodules: true` par sécurité.
- Le build complet VLC peut être long : augmenter le `timeout-minutes` du job.
- Les **versions signées** nécessitent `signingConfig` branché sur les secrets ;
  sans secrets, produire des APK **debug** en fallback.

### 6.4 Ajout d'un commit `merge: upstream` clôturant chaque cycle

Après promotion vers `main`, taguer `vX.Y.Z` pour déclencher la release.
Convention : `git tag vX.Y.Z && git push origin vX.Y.Z`.

---

## 7. Checklist de mise en place

- [ ] Clone du fork + ajout de `upstream`.
- [ ] Désactivation de `mediadb` et du flavour `wear`.
- [ ] Neutralisation de la médiathèque (accueil → notre écran API).
- [ ] Création du package `addon` avec : accueil API, `DeepLinkActivity`,
      listener de position, sauvegarde/restauration.
- [ ] Modifs minimales : manifest + point de lancement.
- [ ] Secrets keystore ajoutés dans GitHub.
- [ ] Workflow `merge-upstream.yml` opérationnel.
- [ ] Workflow `release.yml` testé en `workflow_dispatch`.
- [ ] Premier tag `v0.1.0` → vérifier la Release publique.

---

## 8. Résumé des règles de maintenance

| Règle | Pourquoi |
|---|---|
| Notre code uniquement dans `addon/` | Zéro conflit avec upstream |
| `libvlc/` intouchable | Stability du moteur de lecture |
| Modifs VLC limitées au manifest + amorçage | Rejouables après merge |
| Commits mono-thématiques avec préfixes | Historique clair, merges propres |
| Merge upstream automatisé, conflits résolus manuellement | Désynchronisation maîtrisée |
| Release déclenchée par tag | Build + publication APK phone+tv |
