# UI

This project is bootstrapped by [aurelia 2](https://github.com/aurelia/new).  
For more information about `aurelia` visit [aurelia.io](https://aurelia.io) or [docs.aurelia.io](https://docs.aurelia.io).  

Make sure you have the following tools installed:
* Node.js version 22 or higher
* Code editor of your choice
* A https proxy when testing with Web-eID

## Run project
1. Change directory to the project folder using the cli
2. `npm install` to install packages
3. `npm start` to start up a Vite development server running on port `9000`

The dev server proxies `/kyc`, `/app` and `/proxy` to the services on ports `8084`, `8085` and `8086`. See [Local development](../../README.md#local-development) to start them.

### Useful commands
* `npm run build` which produces a build in the `dist/` folder
* `npm run test` to run unit tests
* `npm run test:watch` to run unit tests in watch mode
* `npm run lint` before committing to validate TypeScript, CSS, and translations
* `npm run lint:translations` to check that source translation keys exist in every locale and that locale keys are sorted
* `npm run translations:sort` to sort all locale keys recursively
* `npm run storybook` to browse the design system on port `6006`

## Design system
`npm run storybook` serves the catalogue of every token, shared style and component on port `6006`; the local Docker stack serves the same catalogue. Each sidebar entry is one page.

Reviewers leave feedback with the **Give feedback** button, which opens a GitHub issue titled `Design feedback: <page>: ...`. List what is open with `gh issue list --search 'in:title "Design feedback"'` and close each issue with the change or the decision. Once nothing is open, the catalogue becomes the design document served by the application next to the API docs.

## Web-eID
* Install the web-eid plugin via https://www.id.ee/en/article/install-id-software
* Make sure you install the browser extension, you can find the instructions on the same page
* Restart your browser!
* Verify whether the plugin works at https://www.id.ee/en/article/test-your-id-card
* Web-eID only works on an HTTPS origin. Put an HTTPS proxy in front of port `9000` that sends `X-Forwarded-Host` and `X-Forwarded-Proto`, and start KYC with `UI_URL`, `OAUTH2_REDIRECT_URI`, `WEBAUTHN_RP_ID` and `WEBAUTHN_RP_ORIGINS` set for that origin

## Nix development environment

A `devenv` environment is available in the `dev/ui` directory. Make sure you have [`devenv`](https://devenv.sh/getting-started/) installed, and optionally install [`direnv`](https://devenv.sh/automatic-shell-activation/) for automatic shell activation. If you don’t use `direnv`, you’ll need to run `devenv shell` manually from the `dev/ui` directory. When working in this environment, add the `--prefix ../../ui` option to commands like `npm install` so they target the actual project directory.
