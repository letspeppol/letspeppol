{ ... }:
{
  scripts.dev-ui.exec = ''
    cd "$DEVENV_ROOT/app/ui"
    exec npm run start -- --host localhost --strictPort "$@"
  '';

  processes.ui.exec = "exec dev-ui";
}
