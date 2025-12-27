import Aurelia from 'aurelia';
import {RouterConfiguration} from "@aurelia/router";
import {I18nConfiguration} from "@aurelia/i18n";
import { LetsPeppol } from './app/lets-peppol';
import {Alert} from "./components/alert/alert";
import {AuthenticationHook} from "./app/authentication-hook";
import * as en from "./app/locale/translation_en.json";
import * as fr from "./app/locale/translation_fr.json";
import * as nl from "./app/locale/translation_nl.json";

Aurelia
    .register(RouterConfiguration.customize({
        useUrlFragmentHash: false,
        activeClass: 'active-route'
    }))
    .register(
        I18nConfiguration.customize((options) => {
            options.initOptions = {
                lng: localStorage.getItem("locale") ?? undefined,
                resources: {
                    nl: { translation: nl },
                    fr: { translation: fr },
                    en: { translation: en },
                }
            };
        })
    )
    .register(Alert, AuthenticationHook)
    .app(LetsPeppol)
    .start();
