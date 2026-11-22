#!/usr/bin/env bash
set -euo pipefail

service=${1:-}
case "$service" in
  app) project=app/backend; port=$DEV_APP_PORT ;;
  kyc) project=kyc; port=$DEV_KYC_PORT ;;
  proxy) project=proxy; port=$DEV_PROXY_PORT ;;
  *) echo 'Usage: dev-backend {app|kyc|proxy} [bootRun options]' >&2; exit 2 ;;
esac
shift

# These are trusted Bash assignment files. Only the backend processes load them.
set -a
source "$DEVENV_ROOT/.env.defaults"
if [[ -f "$DEVENV_ROOT/.env" ]]; then
  source "$DEVENV_ROOT/.env"
fi
set +a

export SERVER_ADDRESS=127.0.0.1 SERVER_PORT="$port"
export DB_PORT="${DB_PORT:-$PGPORT}" DB_USER="letspeppol_$service"
export MAIL_PORT="${MAIL_PORT:-$DEV_SMTP_PORT}"
export KYC_API_URL="${KYC_API_URL:-http://127.0.0.1:$DEV_KYC_PORT}"
export KYC_JWKS_URI="${KYC_JWKS_URI:-$KYC_API_URL/auth/oauth2/jwks}"
export KYC_TOKEN_URI="${KYC_TOKEN_URI:-$KYC_API_URL/auth/oauth2/token}"
export PROXY_API_URL="${PROXY_API_URL:-http://127.0.0.1:$DEV_PROXY_PORT}"

case "$service" in
  app)
    export DOWNLOAD_ARCHIVE_DIR="${DOWNLOAD_ARCHIVE_DIR:-$DEVENV_STATE/downloads}"
    ;;
  kyc)
    export CONTRACT_DATA_DIR="${CONTRACT_DATA_DIR:-$DEVENV_STATE/contracts}"
    export WEBEID_TRUSTED_CA_TRUSTSTORE="${WEBEID_TRUSTED_CA_TRUSTSTORE:-$DEVENV_ROOT/kyc/truststore/belgian-eid.jks}"
    export WEBEID_TRUSTED_CA_TRUSTSTORE_PASSWORD="${WEBEID_TRUSTED_CA_TRUSTSTORE_PASSWORD:-changeit}"
    if [[ -z ${JWT_PRIVATE_KEY:-} && -z ${JWT_PUBLIC_KEY:-} ]]; then
      keydir="$DEVENV_STATE/kyc-jwt"
      (
        umask 077
        mkdir -p "$keydir"
        if [[ ! -s "$keydir/private.pem" ]]; then
          tmp=$(mktemp "$keydir/private.XXXXXX")
          trap 'rm -f "$tmp"' EXIT
          openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp" 2>/dev/null
          mv "$tmp" "$keydir/private.pem"
        fi
        openssl pkey -in "$keydir/private.pem" -pubout -out "$keydir/public.pem"
      )
      export JWT_PRIVATE_KEY="file:$keydir/private.pem" JWT_PUBLIC_KEY="file:$keydir/public.pem"
    elif [[ -z ${JWT_PRIVATE_KEY:-} || -z ${JWT_PUBLIC_KEY:-} ]]; then
      echo 'Set both JWT_PRIVATE_KEY and JWT_PUBLIC_KEY, or leave both unset to generate a local pair.' >&2
      exit 1
    fi
    ;;
  proxy)
    export BACKUP_DATA_DIR="${BACKUP_DATA_DIR:-$DEVENV_STATE/proxy-backup}"
    ;;
esac
if [[ $service != proxy ]]; then
  unset RECOMMAND_API_KEY RECOMMAND_API_SECRET
fi

cd "$DEVENV_ROOT/$project"
exec ./gradlew --no-daemon bootRun "$@"
