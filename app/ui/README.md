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

### Useful commands
* `npm run build` which produces a build in the `dist/` folder
* `npm run test` to run unit tests
* `npm run test:watch` to run unit tests in watch mode
* `npm run lint` before committing to validate TypeScript, CSS, and translations
* `npm run lint:translations` to check that source translation keys exist in every locale and that locale keys are sorted
* `npm run translations:sort` to sort all locale keys recursively

## Web-eID
* Install the web-eid plugin via https://www.id.ee/en/article/install-id-software
* Make sure you install the browser extension, you can find the instructions on the same page
* Restart your browser!
* Verify whether the plugin works at https://www.id.ee/en/article/test-your-id-card

## Nix development environment

The [repository-root development environment](../../README.md#local-development-with-nix) provides Node 22 and npm. From the root, run `devenv shell -- npm --prefix app/ui ci` to install dependencies, then `devenv up` for the full stack or `devenv shell -- dev-ui` for Vite alone.
