import { addons } from 'storybook/manager-api';
import { create } from 'storybook/theming';

addons.setConfig({
  theme: create({
    base: 'light',
    brandTitle: 'Let’s Peppol design system',
    brandImage: '/logo.png',
    brandTarget: '_self',
    colorPrimary: '#2C3C69',
    colorSecondary: '#2C3C69',
    appBg: '#EEF1F6',
    appContentBg: '#FFFFFF',
    appBorderColor: '#D6DCE8',
    appBorderRadius: 6,
    textColor: '#1B2440',
    textMutedColor: '#66708A',
    barBg: '#FFFFFF',
    barSelectedColor: '#2C3C69',
    fontBase: '"Instrument Sans", system-ui, sans-serif',
  }),
  sidebar: {
    showRoots: true,
  },
});
