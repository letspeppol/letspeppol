interface ImportMetaEnv {
    readonly VITE_PROXY_BASE_URL?: string;
    readonly VITE_APP_BASE_URL?: string;
    readonly VITE_KYC_BASE_URL?: string;
    readonly VITE_PEPPOL_DIR_URL?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
