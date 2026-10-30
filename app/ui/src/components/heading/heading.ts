import {resolve} from "@aurelia/kernel";
import {Router} from "@aurelia/router";
import {IDisposable, IEventAggregator, watch} from "aurelia";
import {I18N, ILocalChangeSubscriber} from "@aurelia/i18n";
import {ThemeService} from '../../services/app/theme-service';
import {LoginService} from "../../services/app/login-service";
import {InvoiceContext} from "../../invoice/invoice-context";
import {OwnershipService, OwnershipSummary} from "../../services/app/ownership-service";
import {AccountCacheService} from "../../services/app/account-cache-service";
import {AlertType} from "../alert/alert";
import {focusFirst, keepFocusInside} from "../util/focus-trap";

type NavId = 'dashboard' | 'invoices' | 'partners' | 'products' | 'downloads' | 'settings' | 'account' | 'sponsors';
type BandLayout = 'full' | 'compact' | 'collapsed' | 'phone';
type MenuKind = 'popover' | 'sheet' | 'drawer';

export interface NavItem {
    id: NavId;
    labelKey: string;
    path: string;
    feature?: string;
    inBar: boolean;
}

const NAV_IDS: readonly NavId[] = ['dashboard', 'invoices', 'partners', 'products', 'downloads', 'settings', 'account', 'sponsors'];
const PRIMARY_IDS: readonly NavId[] = ['dashboard', 'invoices', 'partners', 'products'];
const BAR_SLOTS = 4;
const FEATURE_SECTIONS: Partial<Record<NavId, string>> = {
    invoices: 'invoices',
    downloads: 'downloads',
    settings: 'settings',
    account: 'account',
};

const PHONE_QUERY = '(width <= 767px), (height <= 500px) and (orientation: landscape)';
const BOTTOM_BAR_QUERY = '(width <= 767px) and (orientation: portrait)';
const DESKTOP_QUERY = '(width >= 1024px)';
const DESKTOP_LAYOUTS: readonly BandLayout[] = ['full', 'compact'];

export class Heading implements ILocalChangeSubscriber {
    private readonly loginService = resolve(LoginService);
    private readonly invoiceContext = resolve(InvoiceContext);
    private readonly router = resolve(Router);
    private readonly theme = resolve(ThemeService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly accountCache = resolve(AccountCacheService);
    private readonly ea = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    private readonly phoneQuery = window.matchMedia(PHONE_QUERY);
    private readonly bottomBarQuery = window.matchMedia(BOTTOM_BAR_QUERY);
    private readonly desktopQuery = window.matchMedia(DESKTOP_QUERY);
    private readonly subscriptions: IDisposable[] = [];
    private resizeObserver?: ResizeObserver;
    private fitFrame = 0;
    private menuTrigger: HTMLElement | null = null;

    headerEl?: HTMLElement;
    bandEl?: HTMLElement;
    menuCard?: HTMLElement;

    ownerships: OwnershipSummary[] = [];
    selectedOwnershipKey = '';
    swapping = false;
    layout: BandLayout = 'full';
    bottomBarActive = false;
    menuOpen = false;
    activeSection: NavId | null = null;

    attached() {
        this.updateActiveSection();
        this.onViewportChange();
        for (const query of [this.phoneQuery, this.bottomBarQuery, this.desktopQuery]) {
            query.addEventListener('change', this.onViewportChange);
        }
        if (this.headerEl) {
            this.resizeObserver = new ResizeObserver(() => this.fitBand());
            this.resizeObserver.observe(this.headerEl);
        }
        this.i18n.subscribeLocaleChange(this);
        this.subscriptions.push(this.ea.subscribe('au:router:navigation-end', () => {
            this.updateActiveSection();
            this.closeMenu(false);
        }));
        void document.fonts?.ready.then(() => this.scheduleFit());
        void this.loadOwnerships();
    }

    detaching() {
        for (const query of [this.phoneQuery, this.bottomBarQuery, this.desktopQuery]) {
            query.removeEventListener('change', this.onViewportChange);
        }
        this.resizeObserver?.disconnect();
        this.i18n.unsubscribeLocaleChange(this);
        for (const subscription of this.subscriptions.splice(0)) {
            subscription.dispose();
        }
        cancelAnimationFrame(this.fitFrame);
        document.body.classList.remove('has-bottombar');
    }

    handleLocaleChange() {
        this.scheduleFit();
    }

    get navItems(): NavItem[] {
        const ids = this.availableNavIds();
        const barIds = new Set([
            ...ids.filter(id => PRIMARY_IDS.includes(id)),
            ...ids.filter(id => !PRIMARY_IDS.includes(id)),
        ].slice(0, BAR_SLOTS));
        return ids.map(id => this.navItem(id, barIds.has(id)));
    }

    get barItems(): NavItem[] {
        return this.navItems.filter(item => item.inBar);
    }

    get menuItems(): NavItem[] {
        const items = this.menuKind === 'drawer' ? this.navItems : this.navItems.filter(item => !item.inBar);
        return this.ownershipService.hasCompany ? [...items, this.navItem('sponsors', false)] : items;
    }

    get menuKind(): MenuKind {
        if (this.layout !== 'phone') {
            return 'popover';
        }
        return this.bottomBarActive ? 'sheet' : 'drawer';
    }

    get homePath() {
        return this.loginService.getCurrentOwnershipRoute(this.ownershipService.hasCompany ? '/dashboard' : '/account');
    }

    get currentCompanyName() {
        const current = this.ownerships.find(ownership => this.getOwnershipKey(ownership) === this.selectedOwnershipKey);
        return (current ?? this.ownerships[0])?.companyName ?? '';
    }

    @watch((vm: Heading) => `${vm.navItems.map(item => item.id).join()}|${vm.currentCompanyName}|${vm.ownerships.length}`)
    bandContentChanged() {
        this.scheduleFit();
    }

    toggleMenu(event: Event) {
        if (this.menuOpen) {
            this.closeMenu();
            return;
        }
        this.menuTrigger = event.currentTarget as HTMLElement;
        this.menuOpen = true;
        requestAnimationFrame(() => focusFirst(this.menuCard));
    }

    closeMenu(restoreFocus = true) {
        if (!this.menuOpen) {
            return;
        }
        this.menuOpen = false;
        if (restoreFocus) {
            this.menuTrigger?.focus();
        }
        this.menuTrigger = null;
    }

    onMenuKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            event.preventDefault();
            this.closeMenu();
            return;
        }
        keepFocusInside(event, this.menuCard);
    }

    onNavigate(item: NavItem) {
        if (item.id === 'invoices') {
            this.clearInvoice();
        }
        this.closeMenu(false);
    }

    logout() {
        this.closeMenu(false);
        this.ownerships = [];
        this.selectedOwnershipKey = '';
        this.loginService.logout();
    }

    clearInvoice() {
        this.invoiceContext.clearSelectedInvoice();
    }

    async refreshOwnerships() {
        this.ownerships = this.ownershipService.getCachedOwnerships();
        const currentOwnershipKey = this.ownershipService.getCurrentOwnershipKey() ?? '';
        this.selectedOwnershipKey = currentOwnershipKey;
        if (currentOwnershipKey && this.ownerships.some(ownership => this.getOwnershipKey(ownership) === currentOwnershipKey)) {
            queueMicrotask(() => {
                this.selectedOwnershipKey = currentOwnershipKey;
            });
            setTimeout(() => {
                this.selectedOwnershipKey = currentOwnershipKey;
            }, 0);
        }
    }

    getOwnershipLabel(ownership: OwnershipSummary) {
        const role = this.i18n.tr(`ownership.role.${ownership.type}`);
        return `${ownership.companyName} - ${role.startsWith('ownership.role.') ? ownership.type : role}`;
    }

    getOwnershipKey(ownership: OwnershipSummary) {
        return this.ownershipService.getOwnershipKey(ownership.peppolId, ownership.type);
    }

    async changeOwnership() {
        if (this.swapping) {
            return;
        }
        const nextOwnership = this.ownerships.find(ownership => this.getOwnershipKey(ownership) === this.selectedOwnershipKey);
        if (!nextOwnership) {
            return;
        }
        const currentOwnershipKey = this.ownershipService.getCurrentOwnershipKey();
        if (currentOwnershipKey === this.selectedOwnershipKey) {
            return;
        }
        this.swapping = true;
        try {
            await this.loginService.swapOwnership(nextOwnership);
        } catch (error) {
            console.error(error);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to switch ownership"});
        }
        try {
            await this.refreshOwnerships();
            await this.followActingOwnership(currentOwnershipKey);
        } finally {
            this.swapping = false;
        }
    }

    async goToAddOwnership() {
        this.closeMenu(false);
        this.clearInvoice();
        await this.router.load(this.loginService.getCurrentOwnershipRoute('/add-ownership'));
    }

    private async loadOwnerships() {
        await this.loginService.ensureAuthenticated();
        await this.ownershipService.loadOwnerships();
        await this.refreshOwnerships();
    }

    private async followActingOwnership(previousOwnershipKey: string | null) {
        const actingOwnershipKey = this.ownershipService.getCurrentOwnershipKey();
        this.selectedOwnershipKey = actingOwnershipKey ?? '';
        if (!actingOwnershipKey || actingOwnershipKey === previousOwnershipKey) {
            return;
        }
        this.accountCache.clearAll();
        this.ea.publish('account:switched');
        await this.router.load(this.loginService.getCurrentOwnershipRoute('/dashboard'));
    }

    private availableNavIds(): NavId[] {
        const {hasCompany, granted} = this.ownershipService;
        const ids: NavId[] = [];
        if (hasCompany) {
            ids.push('dashboard');
            if (granted.INVOICE_READ) ids.push('invoices');
            if (granted.INVOICE_READ || granted.PARTNER_MANAGE) ids.push('partners');
            if (granted.INVOICE_READ || granted.PRODUCT_MANAGE) ids.push('products');
            if (granted.INVOICE_EXPORT) ids.push('downloads');
            ids.push('settings');
        }
        ids.push('account');
        return ids;
    }

    private navItem(id: NavId, inBar: boolean): NavItem {
        return {
            id,
            labelKey: `navigation.${id}`,
            path: this.loginService.getCurrentOwnershipRoute(`/${id}`),
            feature: FEATURE_SECTIONS[id],
            inBar,
        };
    }

    private updateActiveSection() {
        const segments = window.location.pathname.split('/').filter(Boolean);
        const section = segments.find((segment): segment is NavId => NAV_IDS.includes(segment as NavId));
        this.activeSection = section ?? (segments.length === 0 ? 'dashboard' : null);
    }

    private readonly onViewportChange = () => {
        this.bottomBarActive = this.bottomBarQuery.matches;
        document.body.classList.toggle('has-bottombar', this.bottomBarActive);
        this.fitBand();
    };

    private scheduleFit() {
        cancelAnimationFrame(this.fitFrame);
        this.fitFrame = requestAnimationFrame(() => this.fitBand());
    }

    private fitBand() {
        const header = this.headerEl;
        const band = this.bandEl;
        if (!header || !band) {
            return;
        }
        let layout: BandLayout = this.phoneQuery.matches ? 'phone' : 'collapsed';
        if (layout === 'collapsed' && this.desktopQuery.matches) {
            for (const candidate of DESKTOP_LAYOUTS) {
                header.dataset.layout = candidate;
                if (band.scrollWidth <= band.clientWidth) {
                    layout = candidate;
                    break;
                }
            }
        }
        header.dataset.layout = layout;
        if (layout !== this.layout) {
            this.layout = layout;
            this.closeMenu(false);
        }
    }
}
