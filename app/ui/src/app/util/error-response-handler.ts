import type {I18N} from "@aurelia/i18n";

export interface ErrorResponse {
    errorCode?: string;
    message?: string;
}

export async function toErrorResponse(error: unknown): Promise<ErrorResponse | undefined> {
    // Handle HTTP Response errors
    if (error instanceof Response) {
        if (!error.ok) {
            try {
                const body: unknown = await error.json();
                const errorBody = body as ErrorResponse | null;
                if (!errorBody) {
                    return undefined;
                }
                if (errorBody.errorCode || errorBody.message) {
                    return errorBody;
                }
            } catch {
                return undefined;
            }
        }
    }
    return undefined;
}

export function toLocalizedErrorMessage(
    errorResponse: ErrorResponse | undefined,
    i18n: I18N,
    fallback: string,
    errorCodeTranslationOverrides: Record<string, string> = {}
): string {
    if (!errorResponse) {
        return fallback;
    }
    if (errorResponse.errorCode) {
        const key = errorCodeTranslationOverrides[errorResponse.errorCode] ?? `error.${errorResponse.errorCode}`;
        const translated = i18n.tr(key);
        if (translated && translated !== key) {
            return translated;
        }
    }
    return errorResponse.message || fallback;
}

