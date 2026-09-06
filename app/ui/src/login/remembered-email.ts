const REMEMBERED_EMAIL_KEY = 'peppol.login.email.v1';

export function getRememberedEmail(): string | null {
    try {
        const email = localStorage.getItem(REMEMBERED_EMAIL_KEY)?.trim();
        return email || null;
    } catch {
        return null;
    }
}

export function updateRememberedEmail(email: string, remember: boolean): void {
    try {
        if (remember) {
            localStorage.setItem(REMEMBERED_EMAIL_KEY, email.trim());
        } else {
            localStorage.removeItem(REMEMBERED_EMAIL_KEY);
        }
    } catch {
        // Login must continue when storage is unavailable (for example in a locked-down browser).
    }
}
