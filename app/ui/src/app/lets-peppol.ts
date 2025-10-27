import {route} from "@aurelia/router";
import {Login} from "../login/login";
import {Registration} from "../registration/registration";
import {Invoices} from "../invoice/invoices";
import {Customers} from "../customer/customers";
import {EmailConfirmation} from "../registration/email-confirmation";
import {resolve} from "@aurelia/kernel";
import {Alert} from "../alert/alert";
import {Account} from "../account/account";

@route({
    routes: [
        { path: ['', '/login'],        component: Login,                title: 'Login',                 data: { allowEveryone: true }},
        { path: '/registration',       component: Registration,         title: 'Registration',          data: { allowEveryone: true }},
        { path: '/email-confirmation', component: EmailConfirmation,    title: 'Email Confirmation',    data: { allowEveryone: true }},
        { path: '/invoices',           component: Invoices,             title: 'Invoice',               },
        { path: '/customers',          component: Customers,            title: 'Customers',             },
        { path: '/account',            component: Account,              title: 'Account',               },
    ],
})
export class LetsPeppol {
    private alert = resolve(Alert);
}
