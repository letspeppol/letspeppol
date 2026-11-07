import '../src/app/lets-peppol.css';
import '../src/components/new-feature/rainbow-border.css';
import './preview.css';
import { AppTask, IContainer } from 'aurelia';
import { render, renderToCanvas } from '@aurelia/storybook/preview';
import { I18N, I18nConfiguration } from '@aurelia/i18n';
import { DateFormatConverter } from '../src/app/value-converters/date-format';
import { DateTimeFormatConverter } from '../src/app/value-converters/date-time-format';
import {
  FeatureAckRegistration,
  NewFeatureCustomAttribute,
  NewFeatureSectionCustomAttribute,
} from '../src/components/new-feature';
import { VatDisplayRegistration } from '../src/services/app/vat-display-service';
import { PaymentTermsField } from '../src/components/payment-terms/payment-terms-field';
import en from '../src/app/locale/translation_en.json';
import fr from '../src/app/locale/translation_fr.json';
import nl from '../src/app/locale/translation_nl.json';
import de from '../src/app/locale/translation_de.json';
import { DesignSystemElements } from '../stories/support/elements';

type Theme = 'light' | 'dark';
type Locale = 'en' | 'nl' | 'fr' | 'de';

const mountedTranslators = new Set<I18N>();

document.addEventListener('submit', (event) => {
  const form = event.target as HTMLFormElement;
  if (!form.getAttribute('action')) {
    event.preventDefault();
  }
});

function applyTheme(theme: Theme) {
  localStorage.setItem('peppol.theme.v1', theme);
  document.documentElement.setAttribute('data-theme', theme);
}

function applyLocale(locale: Locale) {
  localStorage.setItem('locale', locale);
  document.documentElement.lang = locale;
  for (const i18n of mountedTranslators) {
    if (i18n.getLocale() !== locale) {
      void i18n.setLocale(locale);
    }
  }
}

const viewports = {
  smallPhone: { name: 'Small phone 360', styles: { width: '360px', height: '740px' }, type: 'mobile' },
  phone: { name: 'Phone 390', styles: { width: '390px', height: '844px' }, type: 'mobile' },
  phoneLandscape: { name: 'Phone landscape 844', styles: { width: '844px', height: '390px' }, type: 'mobile' },
  tabletPortrait: { name: 'Tablet portrait 820', styles: { width: '820px', height: '1180px' }, type: 'tablet' },
  tabletLandscape: { name: 'Tablet landscape 1180', styles: { width: '1180px', height: '820px' }, type: 'tablet' },
  desktop: { name: 'Desktop 1440', styles: { width: '1440px', height: '900px' }, type: 'desktop' },
};

const preview = {
  render,
  renderToCanvas,
  tags: ['autodocs'],
  globalTypes: {
    theme: {
      description: 'Colour theme',
      toolbar: {
        title: 'Theme',
        icon: 'contrast',
        items: [
          { value: 'light', title: 'Light', icon: 'sun' },
          { value: 'dark', title: 'Dark', icon: 'moon' },
        ],
        dynamicTitle: true,
      },
    },
    locale: {
      description: 'Interface language',
      toolbar: {
        title: 'Language',
        icon: 'globe',
        items: [
          { value: 'en', title: 'English' },
          { value: 'nl', title: 'Nederlands' },
          { value: 'fr', title: 'Français' },
          { value: 'de', title: 'Deutsch' },
        ],
        dynamicTitle: true,
      },
    },
  },
  initialGlobals: {
    theme: 'light',
    locale: 'en',
  },
  decorators: [
    (storyFn: () => unknown, context: { globals: { theme?: Theme; locale?: Locale } }) => {
      applyTheme(context.globals.theme ?? 'light');
      applyLocale(context.globals.locale ?? 'en');
      return storyFn();
    },
  ],
  parameters: {
    layout: 'padded',
    docs: {
      story: { inline: true },
    },
    controls: {
      expanded: true,
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    viewport: {
      options: viewports,
    },
    options: {
      storySort: {
        order: [
          'Introduction',
          'Open questions',
          'Foundations',
          ['Principles', 'Colour', 'Typography', 'Spacing and shape', 'Layout and breakpoints', 'Motion and icons', 'Writing', 'Accessibility'],
          'Primitives',
          ['Buttons', 'Fields', 'Status and feedback', 'Page structure', 'Data table', 'Modals and action bar'],
          'Components',
          'Invoice editor',
          'Screens',
        ],
      },
    },
    a11y: {
      test: 'todo',
    },
    aurelia: {
      register: [
        DateFormatConverter,
        DateTimeFormatConverter,
        FeatureAckRegistration,
        VatDisplayRegistration,
        NewFeatureCustomAttribute,
        NewFeatureSectionCustomAttribute,
        PaymentTermsField,
        ...DesignSystemElements,
        AppTask.activated(I18N, (i18n) => {
          mountedTranslators.add(i18n);
        }),
        AppTask.deactivated(I18N, (i18n) => {
          mountedTranslators.delete(i18n);
        }),
      ],
      configureContainer: (container: IContainer, context: { globals: { locale?: Locale } }) => {
        container.register(
          I18nConfiguration.customize((options) => {
            options.initOptions = {
              lng: context.globals.locale ?? 'en',
              fallbackLng: 'en',
              resources: {
                nl: { translation: nl },
                fr: { translation: fr },
                en: { translation: en },
                de: { translation: de },
              },
            };
          }),
        );
      },
    },
  },
};

export default preview;
