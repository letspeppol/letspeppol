{
  pkgs,
  lib,
  ...
}: {
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_22;
    npm.enable = true;
  };
}
