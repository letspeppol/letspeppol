import Aurelia from 'aurelia';
import {RouterConfiguration} from "@aurelia/router";
import {I18nConfiguration} from "@aurelia/i18n";
import { LetsPeppol } from './app/lets-peppol';
import {Alert} from "./alert/alert";
import {AuthenticationHook} from "./app/authentication-hook";
import * as en from "./locale/translation_en.json";
import * as fr from "./locale/translation_fr.json";
import * as nl from "./locale/translation_nl.json";

Aurelia
    .register(RouterConfiguration.customize({
        useUrlFragmentHash: false,
    }))
    .register(
        I18nConfiguration.customize((options) => {
            options.initOptions = {
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
