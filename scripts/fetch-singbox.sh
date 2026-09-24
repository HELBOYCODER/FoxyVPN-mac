#!/bin/sh
# Downloads the pinned sing-box release used for the macOS TUN backend into vendor/.
set -e
cd "$(dirname "$0")/.."
mkdir -p vendor
if [ -x vendor/sing-box ]; then
  echo "vendor/sing-box already present"
  exit 0
fi
VERSION=1.14.2
case "$(uname -m)" in
  arm64) ARCH=arm64 ;;
  x86_64) ARCH=amd64 ;;
  *) echo "unsupported arch $(uname -m)" >&2; exit 1 ;;
esac
TMP=$(mktemp -d)
curl -sL -o "$TMP/sing-box.tar.gz" \
  "https://github.com/SagerNet/sing-box/releases/download/v${VERSION}/sing-box-${VERSION}-darwin-${ARCH}.tar.gz"
tar xzf "$TMP/sing-box.tar.gz" -C "$TMP"
mv "$TMP/sing-box-${VERSION}-darwin-${ARCH}/sing-box" vendor/sing-box
chmod +x vendor/sing-box
echo "installed vendor/sing-box ${VERSION} (darwin-${ARCH})"
