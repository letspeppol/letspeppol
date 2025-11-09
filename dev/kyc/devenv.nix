{pkgs, ...}: let
  lombok = pkgs.stdenv.mkDerivation {
    name = "lombok";
    src = ./plugins/lombok-251.26094.98.zip;

    nativeBuildInputs = [pkgs.unzip];
    phases = ["unpackPhase" "installPhase"];

    unpackPhase = ''
      unzip $src -d extracted
    '';

    installPhase = ''
      mkdir -p $out/lib
      cp extracted/lombok/lib/lombok.jar $out/lib/lombok.jar
    '';
  };

  intellijWithPlugins = pkgs.jetbrains.plugins.addPlugins pkgs.jetbrains.idea-community-bin [
    lombok
  ];
in {
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk21_headless;
  };

  packages = [
    intellijWithPlugins
  ];

  enterShell = ''
    echo "Welcome to the Java dev environment!"
    echo "Loading KYC project ..."
    idea-community ../../kyc/ &
  '';
}
