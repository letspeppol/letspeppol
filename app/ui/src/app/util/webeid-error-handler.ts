// Maps web-eID library errors to localized, actionable messages.
// The raw library strings (e.g. "Web-eID extension is not available") are
// misleading in Chromium-based browsers, where a missing native app surfaces
// as an extension error. See issue #199.
import ExtensionUnavailableError from "@web-eid/web-eid-library/errors/ExtensionUnavailableError";
import NativeUnavailableError from "@web-eid/web-eid-library/errors/NativeUnavailableError";
import VersionMismatchError from "@web-eid/web-eid-library/errors/VersionMismatchError";
import {I18N} from "@aurelia/i18n";

export function isChromiumBrowser(ua: string = navigator.userAgent): boolean {
  return /Chrome|Chromium|Edg|Opera/.test(ua) && !/Firefox/.test(ua);
}

export function toLocalizedWebEidErrorMessage(error: unknown, i18n: I18N, ua?: string): string {
  if (error instanceof NativeUnavailableError) {
    return i18n.tr("webeid.native-unavailable");
  }
  if (error instanceof ExtensionUnavailableError) {
    // Firefox needs the user-installed Web eID extension; Chromium-based
    // browsers connect to the native app directly, so the fix there is
    // installing/starting the native application.
    if (isChromiumBrowser(ua)) {
      return i18n.tr("webeid.native-unavailable");
    }
    return i18n.tr("webeid.extension-unavailable");
  }
  if (error instanceof VersionMismatchError) {
    return i18n.tr("webeid.version-mismatch");
  }
  return undefined;
}
