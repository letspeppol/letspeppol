import {route} from "@aurelia/router";
import {Login} from "../login/login";
import {Registration} from "../registration/registration";
import {Invoices} from "../invoice/invoices";
import {Partners} from "../partner/partners";
import {Onboarding} from "../registration/onboarding";
import {EmailConfirmation} from "../registration/email-confirmation";
import {resolve} from "@aurelia/kernel";
import {Alert} from "../components/alert/alert";
import {Account} from "../account/account";
import {Products} from "../product/products";
import {ResetPassword} from "../login/reset-password";
import {ForgotPassword} from "../login/forgot-password";
import {Dashboard} from "../dashboard/dashboard";

@route({
    routes: [
        { path: ['/login'],            component: Login,                title: 'Login',                 data: { allowEveryone: true }},
        { path: '/forgot-password',    component: ForgotPassword,       title: 'Forgot Password',       data: { allowEveryone: true }},
        { path: '/reset-password',     component: ResetPassword,        title: 'Reset Password',        data: { allowEveryone: true }},
        { path: '/onboarding',         component: Onboarding,           title: 'Onboarding',            data: { allowEveryone: true }},
        { path: '/registration',       component: Registration,         title: 'Registration',          data: { allowEveryone: true }},
        { path: '/email-confirmation', component: EmailConfirmation,    title: 'Email Confirmation',    data: { allowEveryone: true }},
        { path: '/invoices',           component: Invoices,             title: 'Invoice',               },
        { path: '/partners',           component: Partners,             title: 'Partners',              },
        { path: '/products',           component: Products,             title: 'Products',              },
        { path: '/account',            component: Account,              title: 'Account',               },
        { path: ['', '/dashboard'],    component: Dashboard,            title: 'Dashboard',             },
    ],
})
export class LetsPeppol {
    private alert = resolve(Alert);
}
