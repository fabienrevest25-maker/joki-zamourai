#!/usr/bin/env bash
# Télécharge les deux polices du jeu pour qu'elles soient dans l'appli (jeu 100 % hors ligne).
# Sans cette étape, l'appli les charge sur Internet au lancement, ou utilise la police du téléphone.
set -e
D=app/src/main/assets/fonts
mkdir -p "$D/kaisei" "$D/cinzel" /tmp/jzf && cd /tmp/jzf
npm pack @fontsource/kaisei-decol @fontsource/cinzel >/dev/null
for f in fontsource-kaisei-decol-*.tgz; do tar xzf "$f"; cp -r package/files package/400.css package/500.css package/700.css "$OLDPWD/$D/kaisei/"; rm -rf package; done
for f in fontsource-cinzel-*.tgz; do tar xzf "$f"; cp -r package/files package/600.css "$OLDPWD/$D/cinzel/"; rm -rf package; done
echo "Polices embarquées."
