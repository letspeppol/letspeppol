#!/usr/bin/env bash
# Add a NEW lab receiver only. Refuse to overwrite existing participant metadata.
set -euo pipefail
participant=${1:?Usage: bash dev/phoss/register-receiver.sh 9915:new-local-receiver}
[[ $participant =~ ^9915:[A-Za-z0-9._-]+$ ]] || { echo 'Only local 9915 participants are supported' >&2; exit 1; }
: "${PHOSS_AP_CERT:?Set PHOSS_AP_CERT to the existing lab AP public PEM certificate}"
: "${SMP_CREDENTIALS_FILE:?Set SMP_CREDENTIALS_FILE to a private curl netrc file for machine smp}"
root=$(cd "$(dirname "$0")/../.." && pwd)
[[ $(docker network inspect phoss-local --format '{{.Internal}}') == true ]] || { echo 'Expected internal phoss-local network' >&2; exit 1; }
encoded=$(printf '%s' "iso6523-actorid-upis::$participant" | perl -pe 's/([^A-Za-z0-9_.~-])/sprintf("%%%02X",ord($1))/ge')
base="http://smp:8080/$encoded"
status=$(docker run --rm --network phoss-local curlimages/curl:8.18.0 -sS -o /dev/null -w '%{http_code}' "$base")
[[ $status == 404 ]] || { echo "Refusing to replace participant: GET returned HTTP $status" >&2; exit 1; }
mkdir -p "$root/.devenv/state/phoss-test/registrations"
run=$(mktemp -d "$root/.devenv/state/phoss-test/registrations/${participant//:/_}.XXXXXX")
cert=$(openssl x509 -in "$PHOSS_AP_CERT" -outform der | base64 -w0)
cat > "$run/group.xml" <<EOF
<smp:ServiceGroup xmlns:smp="http://busdox.org/serviceMetadata/publishing/1.0/" xmlns:id="http://busdox.org/transport/identifiers/1.0/">
<id:ParticipantIdentifier scheme="iso6523-actorid-upis">$participant</id:ParticipantIdentifier>
<smp:ServiceMetadataReferenceCollection/>
</smp:ServiceGroup>
EOF
cat > "$run/metadata.xml" <<EOF
<smp:ServiceMetadata xmlns:wsa="http://www.w3.org/2005/08/addressing" xmlns:smp="http://busdox.org/serviceMetadata/publishing/1.0/" xmlns:id="http://busdox.org/transport/identifiers/1.0/">
<smp:ServiceInformation>
<id:ParticipantIdentifier scheme="iso6523-actorid-upis">$participant</id:ParticipantIdentifier>
<id:DocumentIdentifier scheme="busdox-docid-qns">urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1</id:DocumentIdentifier>
<smp:ProcessList><smp:Process>
<id:ProcessIdentifier scheme="cenbii-procid-ubl">urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</id:ProcessIdentifier>
<smp:ServiceEndpointList><smp:Endpoint transportProfile="peppol-transport-as4-v2_0">
<wsa:EndpointReference><wsa:Address>http://ap:8080/as4</wsa:Address></wsa:EndpointReference>
<smp:RequireBusinessLevelSignature>false</smp:RequireBusinessLevelSignature>
<smp:Certificate>$cert</smp:Certificate>
<smp:ServiceDescription>LetsPeppol isolated integration test</smp:ServiceDescription>
<smp:TechnicalContactUrl>https://github.com/letspeppol/letspeppol</smp:TechnicalContactUrl>
</smp:Endpoint></smp:ServiceEndpointList>
</smp:Process></smp:ProcessList>
</smp:ServiceInformation></smp:ServiceMetadata>
EOF
doc='busdox-docid-qns::urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1'
encoded_doc=$(printf '%s' "$doc" | perl -pe 's/([^A-Za-z0-9_.~-])/sprintf("%%%02X",ord($1))/ge')
curl_smp() {
  docker run --rm -i --user "$(id -u):$(id -g)" --network phoss-local --mount "type=bind,src=$SMP_CREDENTIALS_FILE,dst=/run/smp.netrc,readonly" curlimages/curl:8.18.0 --netrc-file /run/smp.netrc "$@"
}
curl_smp -fsS -H 'Content-Type: application/xml' -X PUT --data-binary @- "$base?create-in-sml=false" < "$run/group.xml" > "$run/create-group.txt"
curl_smp -fsS -H 'Content-Type: application/xml' -X PUT --data-binary @- "$base/services/$encoded_doc" < "$run/metadata.xml" > "$run/create-metadata.txt"
curl_smp -fsS "$base/services/$encoded_doc" > "$run/signed-metadata.xml"
printf 'Created %s; saved metadata evidence in %s\n' "$participant" "$run"
