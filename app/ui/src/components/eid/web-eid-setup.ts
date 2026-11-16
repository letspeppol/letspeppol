import {bindable} from 'aurelia';
import {ErrorCode} from '@web-eid/web-eid-library/web-eid';

export type WebEidSetupIssue = 'extension' | 'native' | null;

export function getWebEidSetupIssue(error: unknown): WebEidSetupIssue {
    if (!error || typeof error !== 'object' || !('code' in error)) return null;
    if (error.code === ErrorCode.ERR_WEBEID_EXTENSION_UNAVAILABLE) return 'extension';
    if (error.code === ErrorCode.ERR_WEBEID_NATIVE_UNAVAILABLE) return 'native';
    return null;
}

export const extensionStores = [
    {name: 'Microsoft Edge', url: 'https://microsoftedge.microsoft.com/addons/detail/gnmckgbandlkacikdndelhfghdejfido'},
    {name: 'Google Chrome', url: 'https://chromewebstore.google.com/detail/web-eid/ncibgoaomkmdpilpocfeponihegamlic'},
    {name: 'Mozilla Firefox', url: 'https://addons.mozilla.org/firefox/addon/web-eid-webextension/'},
];

// This is only a store-link suggestion, never an authentication/security decision.
// Choose the current desktop browser, not the machine's default browser.
export function getExtensionStore(userAgent: string) {
    if (/Android|iPhone|iPad|iPod|OPR\//i.test(userAgent)) return undefined;
    if (/Edg\//.test(userAgent)) return extensionStores[0];
    if (/Firefox\//.test(userAgent)) return extensionStores[2];
    if (/Chrome\//.test(userAgent)) return extensionStores[1];
    return undefined;
}

export class WebEidSetup {
    @bindable issue: WebEidSetupIssue = null;
    @bindable emailConfirmation = false;
    readonly stores = extensionStores;
    readonly recommendedStore = getExtensionStore(navigator.userAgent);
}
