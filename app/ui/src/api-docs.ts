import { SwaggerUIBundle, SwaggerUIStandalonePreset } from 'swagger-ui-dist';
import 'swagger-ui-dist/swagger-ui.css';
import './api-docs.css';

window.addEventListener('load', () => {
  const ui = SwaggerUIBundle({
    dom_id: '#swagger-ui',
    urls: [
      { url: '/app/v3/api-docs', name: 'App API' },
      { url: '/kyc/v3/api-docs', name: 'KYC API' },
      { url: '/proxy/v3/api-docs', name: 'Proxy API' },
    ],
    'urls.primaryName': 'KYC API',
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset,
    ],
    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
    layout: 'StandaloneLayout',
    deepLinking: true,
    displayRequestDuration: true,
    persistAuthorization: true,
    tryItOutEnabled: true,
    validatorUrl: null,
    oauth2RedirectUrl: `${window.location.origin}/oauth2-redirect.html`,
  });

  ui.initOAuth({
    clientId: 'letspeppol-ui',
    appName: "Let’s Peppol API Docs",
    usePkceWithAuthorizationCodeGrant: true,
  });
});
