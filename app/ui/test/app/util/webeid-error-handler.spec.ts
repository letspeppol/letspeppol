import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import ExtensionUnavailableError from "@web-eid/web-eid-library/errors/ExtensionUnavailableError";
import NativeUnavailableError from "@web-eid/web-eid-library/errors/NativeUnavailableError";
import VersionMismatchError from "@web-eid/web-eid-library/errors/VersionMismatchError";
import {toLocalizedWebEidErrorMessage, isChromiumBrowser} from "../../../src/app/util/webeid-error-handler";

const i18nMock = {tr: (key: string) => `i18n:${key}`} as any;

const CHROMIUM_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
const FIREFOX_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";

describe("isChromiumBrowser", () => {
    it("detects Chromium browsers", () => {
        expect(isChromiumBrowser(CHROMIUM_UA)).toBe(true);
        expect(isChromiumBrowser(FIREFOX_UA)).toBe(false);
    });
});

describe("toLocalizedWebEidErrorMessage", () => {
    let uaSpy;

    function setUA(ua: string) {
        uaSpy = vi.spyOn(navigator, "userAgent", "get").mockReturnValue(ua);
    }

    afterEach(() => uaSpy?.mockRestore());

    it("maps NativeUnavailableError to the native-app message", () => {
        setUA(CHROMIUM_UA);
        const text = toLocalizedWebEidErrorMessage(new NativeUnavailableError(), i18nMock);
        expect(text).toBe("i18n:webeid.native-unavailable");
    });

    it("maps ExtensionUnavailableError on Chromium to the native-app message", () => {
        // Regression for #199: in Chromium a missing native app surfaces as
        // an extension error; the user should be told to install/start the app.
        setUA(CHROMIUM_UA);
        const text = toLocalizedWebEidErrorMessage(new ExtensionUnavailableError(), i18nMock);
        expect(text).toBe("i18n:webeid.native-unavailable");
    });

    it("maps ExtensionUnavailableError on Firefox to the extension message", () => {
        setUA(FIREFOX_UA);
        const text = toLocalizedWebEidErrorMessage(new ExtensionUnavailableError(), i18nMock);
        expect(text).toBe("i18n:webeid.extension-unavailable");
    });

    it("maps VersionMismatchError to the update message", () => {
        setUA(CHROMIUM_UA);
        const text = toLocalizedWebEidErrorMessage(new VersionMismatchError(undefined, {nativeApp: "2.8.0", extension: "2.8.0", library: "2.8.0"}, {extension: true, nativeApp: true}), i18nMock);
        expect(text).toBe("i18n:webeid.version-mismatch");
    });

    it("returns undefined for unrelated errors so callers keep their fallback", () => {
        const text = toLocalizedWebEidErrorMessage(new Error("something else"), i18nMock);
        expect(text).toBeUndefined();
    });
});
