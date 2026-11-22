{ config, lib, pkgs, ... }:
let
  backends = [ "kyc" "app" "proxy" ];
in
{
  packages = [ pkgs.openssl ];

  services.postgres = {
    enable = true;
    package = pkgs.postgresql_16;
    listen_addresses = "127.0.0.1";
    port = 55432;
    initialDatabases = map (name: {
      inherit name;
      user = "letspeppol_" + name;
      pass = "local_dev_only";
    }) backends;
    hbaConf = ''
      local all all trust
      host all all 127.0.0.1/32 scram-sha-256
    '';
  };
  # The init/readiness connection uses the private Unix socket, not JDBC's TCP endpoint.
  services.mailpit.enable = true;
  services.mailpit.additionalArgs = [ "--disable-version-check" ];

  env = {
    PGHOST = config.services.postgres.settings.unix_socket_directories;
    DEV_SMTP_PORT = toString config.processes.mailpit.ports.smtp.value;
    DEV_KYC_PORT = "18084";
    DEV_APP_PORT = "18085";
    DEV_PROXY_PORT = "18086";
  };

  scripts.dev-backend.exec = ''
    exec bash "$DEVENV_ROOT/dev/run-backend.sh" "$@"
  '';

  processes = lib.genAttrs backends (name: {
    exec = "exec dev-backend ${name}";
    after = [ "devenv:processes:postgres" ];
  });
}
