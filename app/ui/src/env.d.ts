interface ImportMetaEnv {
    readonly VITE_PROXY_BASE_URL?: string;
    readonly VITE_APP_BASE_URL?: string;
    readonly VITE_KYC_BASE_URL?: string;
    readonly VITE_PEPPOL_DIR_URL?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}

declare module 'swagger-ui-dist' {
    interface SwaggerUiInstance {
        initOAuth(options: {
            clientId: string;
            appName: string;
            usePkceWithAuthorizationCodeGrant: boolean;
        }): void;
    }

    interface SwaggerUiBundle {
        (options: Record<string, unknown>): SwaggerUiInstance;
        plugins: { DownloadUrl: unknown };
        presets: { apis: unknown };
    }

    export const SwaggerUIBundle: SwaggerUiBundle;
    export const SwaggerUIStandalonePreset: unknown;
}

declare module 'swagger-ui-dist/oauth2-redirect.js';
