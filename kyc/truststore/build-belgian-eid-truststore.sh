#!/usr/bin/env bash
# Builds a JKS containing the Belgian eID CA chains published by the Belgian government.
#
# Scope: Belgian Citizen CA and Foreigner CA (the issuers of physical eID signing certificates),
# including the legacy RSA hierarchy and the current EC hierarchy below Belgium Root CA6.
# This intentionally does not add unrelated Belgian QTSPs or remote-sealing CAs.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
output_file="${1:-$script_dir/belgian-eid.jks}"
store_password="${WEBEID_TRUSTED_CA_TRUSTSTORE_PASSWORD:-changeit}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

download() {
  local url="$1"
  local destination="$2"
  curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
    --output "$destination" "$url"
}

require_ca_certificate() {
  local certificate="$1"
  openssl x509 -inform DER -in "$certificate" -noout -text |
    rg -q 'CA:TRUE'
}

import_certificate() {
  local alias="$1"
  local certificate="$2"
  require_ca_certificate "$certificate"
  keytool -importcert -noprompt -storetype JKS \
    -keystore "$output_file" -storepass "$store_password" \
    -alias "$alias" -file "$certificate" >/dev/null
}

mkdir -p "$(dirname -- "$output_file")"
rm -f "$output_file"

# The legacy repository is the official source for RSA cards (BRCA1–4).
legacy_root_base='https://certs.eid.belgium.be'
for root in belgiumrca belgiumrca2 belgiumrca3 belgiumrca4; do
  certificate="$work_dir/$root.crt"
  download "$legacy_root_base/$root.crt" "$certificate"
  import_certificate "$root" "$certificate"
done

# The current repository is the official source for EC cards (BRCA6) and its intermediates.
current_repository='https://repository.eidpki.belgium.be'
current_manifest="$work_dir/current-manifest.json"
download "$current_repository/documents/crt.json" "$current_manifest"

brca6="$work_dir/brca6.crt"
download 'https://crt.eidpki.belgium.be/eid/brca6.crt' "$brca6"
import_certificate 'brca6' "$brca6"

# The current manifest has only the Citizen/Foreigner issuing CAs selected below.  It deliberately
# excludes timestamping, remote signing and sealing CAs because they do not issue physical eID cards.
jq -r '
  .data[]
  | select(.title.en | startswith("Belgium Citizen Certificate Authority") or startswith("Belgium Foreigner Certificate Authority"))
  | .certificates[].crt.url
' "$current_manifest" | sort -u > "$work_dir/current-urls.txt"

while IFS= read -r url; do
  filename="$(basename -- "$url")"
  certificate="$work_dir/$filename"
  download "$url" "$certificate"
  import_certificate "${filename%.crt}" "$certificate"
done < "$work_dir/current-urls.txt"

# Fetch every published legacy Citizen/Foreigner intermediate.  Several old card generations have
# a distinct issuing CA; retaining them is necessary because the browser sends us only the leaf cert.
for type in Citizen Foreigner; do
  page="$work_dir/${type,,}.html"
  download "https://repository.eid.belgium.be/certificates.php?cert=$type&lang=en" "$page"
  rg -o "http://certs\\.eid\\.belgium\\.be/${type,,}[0-9]+\\.crt" "$page"
done | sed 's#^http:#https:#' | sort -ru > "$work_dir/legacy-urls.txt"

while IFS= read -r url; do
  filename="$(basename -- "$url")"
  certificate="$work_dir/$filename"
  download "$url" "$certificate"
  import_certificate "${filename%.crt}" "$certificate"
done < "$work_dir/legacy-urls.txt"

keytool -list -storetype JKS -keystore "$output_file" -storepass "$store_password" >/dev/null
printf 'Created %s with %s CA certificates.\n' "$output_file" \
  "$(keytool -list -storetype JKS -keystore "$output_file" -storepass "$store_password" | rg -c 'trustedCertEntry')"
