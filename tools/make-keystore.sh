#!/usr/bin/env bash
# Creates a private signing key for Athan TV and prints the values to paste into
# GitHub > your repo > Settings > Secrets and variables > Actions.
# Keep athan-release.jks and the password safe and OUT of the repository:
# the same key is needed to install future updates over the old version.
set -euo pipefail

KS=athan-release.jks
ALIAS=athantv
read -r -s -p "Choose a keystore password (6+ chars): " PW; echo

keytool -genkeypair -v -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PW" -keypass "$PW" \
  -dname "CN=Athan TV, OU=Personal, O=Personal, C=US"

echo
echo "Add these four repository secrets:"
echo "  ATHAN_KEYSTORE_BASE64  = (contents of athan-release.jks.b64, created next)"
echo "  ATHAN_KEYSTORE_PASSWORD = the password you just typed"
echo "  ATHAN_KEY_ALIAS         = $ALIAS"
echo "  ATHAN_KEY_PASSWORD      = the password you just typed"
base64 -w0 "$KS" > "$KS.b64" 2>/dev/null || base64 "$KS" | tr -d '\n' > "$KS.b64"
echo
echo "Wrote $KS.b64. Copy its contents into ATHAN_KEYSTORE_BASE64, then delete the .b64 file."
