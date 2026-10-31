const PERMISSION_ERROR_CODES = new Set(['MISSING_PERMISSION', 'not_admin']);

export async function isMissingPermission(error: unknown): Promise<boolean> {
    if (!(error instanceof Response) || error.status !== 403) {
        return false;
    }
    try {
        const body = await error.clone().json() as { errorCode?: unknown } | null;
        return typeof body?.errorCode === 'string' && PERMISSION_ERROR_CODES.has(body.errorCode);
    } catch {
        return false;
    }
}
