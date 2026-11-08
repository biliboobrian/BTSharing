# BTSharing

Active automatiquement le partage de connexion (point d'accès Wi‑Fi ou partage Bluetooth)
quand un appareil Bluetooth approuvé se connecte au téléphone, et le coupe quand il se déconnecte.

## Compiler / installer

1. Ouvrir ce dossier dans **Android Studio** (Ladybug ou plus récent). Il télécharge Gradle et le SDK.
2. Brancher le téléphone (débogage USB activé), puis **Run ▶**.
   Ou : *Build → Build APK(s)* puis installer `app/build/outputs/apk/debug/app-debug.apk`.

Android 11 minimum.

## Comment le partage est activé

1. **Méthode directe (par défaut, sans Shizuku ni root)** : BTSharing demande l'autorisation
   *« Modifier les paramètres système »*. Avec elle, le service système de partage de connexion
   (Android 11+) accepte d'activer le point d'accès Wi‑Fi ou le partage Bluetooth. Elle s'accorde
   une seule fois et **reste après un redémarrage**.
2. **Shizuku (secours)** : seulement si Android refuse la méthode directe — typiquement quand
   l'opérateur impose une vérification d'abonnement avant le partage. BTSharing ne propose alors
   Shizuku que dans ce cas. S'il tourne déjà, il est utilisé automatiquement.
3. **Notification** : si rien ne marche, elle ouvre l'écran de partage de connexion en un geste.

Diagnostic : `adb logcat -s TetherController DirectTethering`
(`Direct … -> 0` = succès, `14` ou `11` = refus opérateur/constructeur).

## Réglages proposés automatiquement

Au lancement, l'application propose un par un les réglages manquants (et les garde dans un bandeau) :

| Réglage | Effet |
|---|---|
| Autorisations Bluetooth + notifications | détecter les appareils, service permanent |
| Modifier les paramètres système | activer le partage sans intervention |
| Shizuku (uniquement si la méthode directe est refusée) | idem, via les droits ADB |
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
- Les deux méthodes reposent sur des API système non publiques (`TetheringManager`,
  `ITetheringConnector`) ; un constructeur ou une future version d'Android peut les modifier. En cas d'échec,
  la notification de secours prend le relais (voir `adb logcat -s TetherController`).
