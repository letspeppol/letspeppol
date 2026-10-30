import {bindable, containerless} from "aurelia";

export type NavIconName =
    'dashboard' | 'invoices' | 'partners' | 'products' | 'downloads' | 'settings' | 'account' | 'sponsors'
    | 'more' | 'menu' | 'close' | 'switch-company' | 'chevron' | 'add-company' | 'log-out'
    | 'moon' | 'sun' | 'language' | 'user';

@containerless
export class NavIcon {
    @bindable name: NavIconName;
}
