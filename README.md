# BTSharing

Active automatiquement le partage de connexion (point d'accès Wi‑Fi ou partage Bluetooth)
quand un appareil Bluetooth approuvé se connecte au téléphone, et le coupe quand il se déconnecte.

## Compiler / installer

1. Ouvrir ce dossier dans **Android Studio** (Ladybug ou plus récent). Il télécharge Gradle et le SDK.
2. Brancher le téléphone (débogage USB activé), puis **Run ▶**.
   Ou : *Build → Build APK(s)* puis installer `app/build/outputs/apk/debug/app-debug.apk`.

Android 11 minimum.

## Pourquoi Shizuku ?

Depuis Android 8, seules les applications système peuvent activer le partage de connexion.
[Shizuku](https://shizuku.rikka.app/) (gratuit, open source, **sans root**) donne à BTSharing
les droits « ADB » nécessaires :

1. Installer Shizuku (Play Store).
2. Options pour les développeurs → activer **Débogage sans fil**.
3. Dans Shizuku : *Démarrer via le débogage sans fil* (appairage une seule fois).
4. Dans BTSharing : accepter la demande d'autorisation Shizuku.

Shizuku doit être redémarré après chaque redémarrage du téléphone (sauf avec root / Sui).

Sans Shizuku : le partage **Bluetooth** peut encore fonctionner sur Android 11. Sinon, une
notification propose d'ouvrir l'écran de partage de connexion en un seul geste.

## Réglages proposés automatiquement

Au lancement, l'application propose un par un les réglages manquants (et les garde dans un bandeau) :

| Réglage | Effet |
|---|---|
| Autorisations Bluetooth + notifications | détecter les appareils, service permanent |
| Shizuku | activer le partage sans intervention |
| Ignorer l'optimisation de batterie | actif écran éteint / en économie d'énergie, Doze |
| Batterie « Non restreinte » | pas de restriction en arrière-plan |
| Ne pas suspendre l'appli inutilisée | pas de retrait des autorisations |
| Démarrage automatique (Xiaomi, Huawei, Oppo, Vivo, OnePlus, Samsung…) | évite les « tueurs de tâches » des constructeurs |

En plus : service de premier plan permanent, démarrage au boot, receiver Bluetooth déclaré dans
le manifeste (réveille l'appli même tuée) et vérification toutes les 15 min (WorkManager).

## Utilisation

- **+** : choisir un appareil (associé ou à proximité) et le type de partage.
- La carte devient **verte** quand l'appareil est connecté.
- L'icône à droite indique si le partage de connexion est **actif** (allumée) ou non (grisée).
- Appui sur un appareil : changer le type de partage ou le supprimer.

Le partage n'est coupé à la déconnexion que si c'est BTSharing qui l'avait activé.

## Limites connues

- Le mode **Bluetooth** suppose que l'appareil connecté accepte le partage Bluetooth (PAN) :
  tablettes, PC, certains autoradios. Sinon, choisir **Point d'accès Wi‑Fi**.
- L'appel Shizuku repose sur une interface système non publique (`ITetheringConnector`) ;
  un constructeur ou une future version d'Android peut la modifier. En cas d'échec,
  la notification de secours prend le relais (voir `adb logcat -s TetherController`).
