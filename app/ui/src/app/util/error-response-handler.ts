export interface ErrorResponse {
    errorCode?: string;
    message?: string;
}

export async function toErrorResponse(error: unknown): Promise<ErrorResponse | undefined> {
    // Handle HTTP Response errors
    if (error instanceof Response) {
        if (error.status === 400) {
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

