# Local development how-to

## Start the stack

Install [Nix and devenv 2.1 or newer](https://devenv.sh/getting-started/). From the repository root, run:

```sh
devenv shell -- npm --prefix app/ui ci
devenv up
```

The first command installs the frontend dependencies from `app/ui/package-lock.json`. Run it on a fresh checkout or when that lockfile changes. For everyday restarts, just run `devenv up`.

`devenv up` starts the whole stack. No separate backend install is needed: Gradle downloads dependencies and builds KYC, the app backend, and the proxy as part of startup. This can take a few minutes on the first run.

No Docker, root `.env`, or external credentials are required. Leave `devenv up` running while you test.

Open [http://localhost:9000](http://localhost:9000) and log in on a fresh dev database with `alice@letspeppol.org` / `alice`. Use `localhost`, not `127.0.0.1`, for the app: OAuth uses the exact origin. The Mailpit inbox is at [http://127.0.0.1:8025](http://127.0.0.1:8025).

If the UI loads but login fails, check whether KYC is still starting. From a second terminal at the repository root:

```sh
curl -fsS http://localhost:9000/kyc/actuator/health
devenv --no-tui processes logs kyc
```

The health endpoint should report `UP`. Press Ctrl+C in the first terminal when you're done. The development database and Mailpit inbox persist across restarts.

## Test local email with Mailpit

1. In a private browser window, open [http://localhost:9000/forgot-password](http://localhost:9000/forgot-password), or use **Forgot my password** on the login page.
2. Enter `bob@letspeppol.org`, another seeded account. Using Bob leaves Alice's password unchanged.
3. Open [Mailpit](http://127.0.0.1:8025) and look for **Reset your password for Let's Peppol** addressed to Bob. Check the newest message if the inbox already has one.
4. Open the reset link, pick a new local-only password, and then log in as Bob with that password. This changes Bob's password in your local database, so it survives a restart.

KYC uses normal SMTP, but the dev launcher sends it to Mailpit on `127.0.0.1:1025` by default. Mailpit stores the message locally; it does not deliver it to Bob's real inbox. The sender defaults to `letspeppol@example.invalid`. No SMTP login is needed. If nothing arrives, check the KYC logs and make sure your root `.env` doesn't override `MAIL_HOST` or `MAIL_PORT`.

## Optional: test email confirmation without Web eID

In the repository-root `.env`, set `SPRING_PROFILES_ACTIVE=postgres,dev,web-eid-testing` and restart `devenv up`. Use a private dev stack with a disposable local database, Mailpit, and no live access-point credentials. The launcher opts in to the KYC test build (`-PwebEidTesting=true`). The bypass endpoint is absent from the default KYC artifact. It also requires both `dev` and `web-eid-testing` and a server bound to `127.0.0.1`; either `prod` or `production` disables it. The Vite build flag only enables the switch on the dev server; `npm --prefix app/ui run build` strips the bypass markup from the compiled template.

Open a fresh [Mailpit](http://127.0.0.1:8025) confirmation link and enable **Bypass Web eID and signing (testing only)**. Select a director and review the usual contract if one is listed, then complete registration. Log in with the email you registered with, using the password you chose (or your existing password if that email is already verified). The registration uses the selected company in your local database and consumes the link. It does not record an eID verification, sign a contract, or activate Peppol. Remove `web-eid-testing` and restart to disable the bypass.

## Optional: use your existing HTTPS proxy

If you already have a TLS proxy and a trusted certificate for `letspeppol.httpsonlan.com`, you can keep using `https://letspeppol.httpsonlan.com:3001`. The proxy, not devenv, serves the certificate. Configure the proxy to forward requests to `http://localhost:9000`, preserve the public host and port (in `Host` or `X-Forwarded-Host`), and set `X-Forwarded-Proto: https`. Enable WebSocket forwarding if you want Vite hot reload to work through the proxy. Keep this development proxy private; the dev stack has known passwords.

Add these settings to the optional, git-ignored `.env` at the repository root:

```sh
UI_URL=https://letspeppol.httpsonlan.com:3001
ISSUER_URI="$UI_URL/kyc"
KYC_ISSUER_URI="$ISSUER_URI"
OAUTH2_REDIRECT_URI="$UI_URL/callback"
CORS_ALLOWED_ORIGINS="$UI_URL"
WEBAUTHN_RP_ID=letspeppol.httpsonlan.com
WEBAUTHN_RP_ORIGINS="$UI_URL"
```

Stop the stack and start it again, allowing your hostname in Vite:

```sh
__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS=letspeppol.httpsonlan.com devenv up
```

Open the HTTPS URL for login and request a **new** reset email. The inbox is still at `http://127.0.0.1:8025`, but the new link should start with `https://letspeppol.httpsonlan.com:3001/reset-password`. Older messages keep their original URLs. Passkeys registered for `localhost` won't work on the HTTPS hostname, or vice versa.

## Optional: connect Recommand Playground

In [Recommand](https://app.recommand.eu), select your **Playground** team and create a team API key. Put both values in your root `.env`, using [`.env.example`](../.env.example) as a template. If you've already set up HTTPS, add the two settings to the existing file rather than overwriting it:

```sh
RECOMMAND_API_KEY='your-playground-key'
RECOMMAND_API_SECRET='your-playground-secret'
```

Restart the proxy (or stop and restart `devenv up`) before testing Peppol registration or document exchange. Use test data and don't use a production team or share the credentials. Leave both values unset or blank for local login and email tests. The proxy only loads and polls Recommand when credentials are configured; supplying only one value causes a startup error.

Scrada is disabled if `SCRADA_API_KEY` is unset or blank. To enable it, also supply `SCRADA_API_PWD` and `SCRADA_COMPANY_ID`. In devenv, new registrations use Recommand by default; set `PEPPOL_ACCESS_POINT=SCRADA` only if you want to register new companies through Scrada instead.
