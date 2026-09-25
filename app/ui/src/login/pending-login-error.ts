const PENDING_ERROR_KEY = 'peppol.login.error.v1';
const ERROR_PARAMETER = 'error';
const AUTHORIZATION_PATHS = ['/login', '/callback'];

export const GENERIC_LOGIN_ERROR_KEY = 'login.authorization-error';

const ERROR_KEYS: Record<string, string> = {
    ownership_unavailable: 'login.authorization-no-ownership',
    access_denied: 'login.authorization-denied',
    login_required: 'login.authorization-expired',
    server_error: 'login.authorization-unavailable',
    temporarily_unavailable: 'login.authorization-unavailable',
};

export function loginErrorKeyFor(errorCode: string | null | undefined): string {
    return (errorCode && ERROR_KEYS[errorCode]) || GENERIC_LOGIN_ERROR_KEY;
}

export function rememberLoginError(errorKey: string): void {
    try {
        sessionStorage.setItem(PENDING_ERROR_KEY, errorKey);
    } catch {
        return;
    }
}

export function captureLoginError(): void {
    if (!AUTHORIZATION_PATHS.includes(window.location.pathname)) return;

    const params = new URLSearchParams(window.location.search);
    const errorCode = params.get(ERROR_PARAMETER);
    if (errorCode === null) return;

    rememberLoginError(loginErrorKeyFor(errorCode));
    params.delete(ERROR_PARAMETER);
    const query = params.toString();
    window.history.replaceState({}, '', window.location.pathname + (query ? `?${query}` : ''));
}

export function consumeLoginError(): string | undefined {
    try {
        const errorKey = sessionStorage.getItem(PENDING_ERROR_KEY);
        sessionStorage.removeItem(PENDING_ERROR_KEY);
        return errorKey ?? undefined;
    } catch {
        return undefined;
    }
}
