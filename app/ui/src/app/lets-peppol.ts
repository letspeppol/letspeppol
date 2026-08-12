import {NavigationStrategy, route} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import {Alert} from "../components/alert/alert";

// NavigationStrategy invokes the dynamic import only after its route matches. Previously every
// screen was imported here, so even /login and /callback loaded the complete application graph.
const lazy = (load: () => Promise<Record<string, unknown>>) => new NavigationStrategy(() => load());

@route({
    routes: [
        { path: ['/login'],                component: lazy(() => import('../login/login')),                       title: 'Login',                  data: { allowEveryone: true }},
        { path: '/callback',               component: lazy(() => import('../login/callback')),                    title: 'Callback',               data: { allowEveryone: true }},
        { path: '/forgot-password',        component: lazy(() => import('../login/forgot-password')),             title: 'Forgot Password',        data: { allowEveryone: true }},
        { path: '/reset-password',         component: lazy(() => import('../login/reset-password')),              title: 'Reset Password',         data: { allowEveryone: true }},
        { path: '/onboarding',             component: lazy(() => import('../registration/onboarding')),           title: 'Onboarding',             data: { allowEveryone: true }},
        { path: '/registration',           component: lazy(() => import('../registration/registration')),         title: 'Registration',           data: { allowEveryone: true, registrationType: 'ADMIN' }},
        { path: '/affiliate/registration', component: lazy(() => import('../registration/registration')),         title: 'Affiliate Registration', data: { allowEveryone: true, registrationType: 'AFFILIATE' }},
        { path: '/email-confirmation',     component: lazy(() => import('../registration/email-confirmation')),   title: 'Email Confirmation',     data: { allowEveryone: true }},
        { path: '/add-ownership',          component: lazy(() => import('../registration/add-ownership')),        title: 'Add Account' },
        { path: ['/invoices', '/invoices/:id'], component: lazy(() => import('../invoice/invoices')),             title: 'Invoice' },
        { path: '/partners',               component: lazy(() => import('../partner/partners')),                  title: 'Partners' },
        { path: '/products',               component: lazy(() => import('../product/products')),                  title: 'Products' },
        { path: '/sponsors',               component: lazy(() => import('../sponsor/sponsors')),                  title: 'Sponsors' },
        { path: '/account',                component: lazy(() => import('../account/account')),                   title: 'Account' },
        { path: ['', '/dashboard'],        component: lazy(() => import('../dashboard/dashboard')),               title: 'Dashboard' },
    ],
})
export class LetsPeppol {
    private alert = resolve(Alert);
}
