import '../src/app/lets-peppol.css';
import '../src/components/new-feature/rainbow-border.css';
import './preview.css';
import { AppTask, IContainer } from 'aurelia';
import { render, renderToCanvas } from '@aurelia/storybook/preview';
import { I18N, I18nConfiguration } from '@aurelia/i18n';
import {
  FeatureAckRegistration,
  NewFeatureCustomAttribute,
  NewFeatureSectionCustomAttribute,
} from '../src/components/new-feature';
import en from '../src/app/locale/translation_en.json';
import fr from '../src/app/locale/translation_fr.json';
import nl from '../src/app/locale/translation_nl.json';
import de from '../src/app/locale/translation_de.json';
import { DesignSystemElements } from '../stories/support/elements';

type Theme = 'light' | 'dark';
type Locale = 'en' | 'nl' | 'fr' | 'de';
type Globals = { theme?: Theme; locale?: Locale; viewport?: { value?: string } };

const THEME_KEY = 'peppol.theme.v1';
const LOCALE_KEY = 'locale';
const embeddedInDocs = !!window.frameElement && window.frameElement.id !== 'storybook-preview-iframe';

const mountedTranslators = new Set<I18N>();

document.addEventListener('submit', (event) => {
  const form = event.target as HTMLFormElement;
  if (!form.getAttribute('action')) {
    event.preventDefault();
  }
});

function applyTheme(theme: Theme) {
  localStorage.setItem(THEME_KEY, theme);
  document.documentElement.setAttribute('data-theme', theme);
}

function applyLocale(locale: Locale) {
  localStorage.setItem(LOCALE_KEY, locale);
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

function storedTheme(): Theme {
  return (localStorage.getItem(THEME_KEY) as Theme | null) ?? 'light';
}

function storedLocale(): Locale {
  return (localStorage.getItem(LOCALE_KEY) as Locale | null) ?? 'en';
}

function currentTheme(globals: Globals): Theme {
  return embeddedInDocs ? storedTheme() : (globals.theme ?? 'light');
}

function currentLocale(globals: Globals): Locale {
  return embeddedInDocs ? storedLocale() : (globals.locale ?? 'en');
}

function applyGlobals(globals: Globals) {
  applyTheme(currentTheme(globals));
  applyLocale(currentLocale(globals));
}

function fitDocsFrameToViewport(viewport?: { value?: string }) {
  const width = viewports[viewport?.value as keyof typeof viewports]?.styles.width;
  if (!embeddedInDocs || !width) {
    return;
  }
  const frame = window.frameElement as HTMLElement;
  frame.style.width = width;
  frame.style.maxWidth = '100%';
}

window.addEventListener('storage', () => {
  applyTheme(storedTheme());
  applyLocale(storedLocale());
});

let interactionFinished = false;
let lastOwnInput = 0;
let releaseFocusTurn: (() => void) | undefined;

function waitForFocusTurn(): Promise<() => void> {
  return new Promise((granted) => {
    void navigator.locks.request(
      'letspeppol-docs-focus',
      () =>
        new Promise<void>((release) => {
          granted(release);
          setTimeout(release, 8000);
        }),
    );
  });
}

if (embeddedInDocs) {
  const noteOwnInput = (event: Event) => {
    if (event.isTrusted) {
      lastOwnInput = performance.now();
    }
  };
  const keepStateWhenAnotherFrameTakesFocus = (event: Event) => {
    if (interactionFinished && performance.now() - lastOwnInput > 1000) {
      event.stopImmediatePropagation();
    }
  };
  window.addEventListener('pointerdown', noteOwnInput, true);
  window.addEventListener('keydown', noteOwnInput, true);
  window.addEventListener('blur', keepStateWhenAnotherFrameTakesFocus, true);
  window.addEventListener('focusout', keepStateWhenAnotherFrameTakesFocus, true);
}

const preview = {
  render,
  renderToCanvas,
  tags: ['autodocs', '!dev'],
  beforeEach: async () => {
    interactionFinished = false;
    if (embeddedInDocs && navigator.locks) {
      releaseFocusTurn = await waitForFocusTurn();
    }
  },
  afterEach: () => {
    interactionFinished = true;
    releaseFocusTurn?.();
  },
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
    (storyFn: () => unknown, context: { globals: Globals }) => {
      applyGlobals(context.globals);
      fitDocsFrameToViewport(context.globals.viewport);
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
        ],
      },
    },
    aurelia: {
      register: [
        FeatureAckRegistration,
        NewFeatureCustomAttribute,
        NewFeatureSectionCustomAttribute,
        ...DesignSystemElements,
        AppTask.activated(I18N, (i18n) => {
          mountedTranslators.add(i18n);
        }),
        AppTask.deactivated(I18N, (i18n) => {
          mountedTranslators.delete(i18n);
        }),
      ],
      configureContainer: (container: IContainer, context: { globals: Globals }) => {
        container.register(
          I18nConfiguration.customize((options) => {
            options.initOptions = {
              lng: currentLocale(context.globals),
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
