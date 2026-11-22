{ pkgs, ... }:
{
  imports = [ ./dev/backend.nix ./dev/ui.nix ];

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk25_headless;
  };

  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_22;
    npm.enable = true;
  };

  # Load private overrides in the backend launcher, not during Nix evaluation.
  dotenv.disableHint = true;

  scripts.open-kyc.exec = ''
    for launcher in idea idea-oss idea-community; do
      if command -v "$launcher" >/dev/null 2>&1; then
        exec "$launcher" "$DEVENV_ROOT/kyc"
      fi
    done
    echo 'Install IntelliJ IDEA and add its launcher to PATH, or open kyc/ from the IDE.' >&2
    exit 1
  '';
}
