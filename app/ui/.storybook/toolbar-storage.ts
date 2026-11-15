import { addons } from 'storybook/manager-api';
import { GLOBALS_UPDATED, SET_GLOBALS } from 'storybook/internal/core-events';

function store({ globals }: { globals: { theme?: string; locale?: string } }) {
  localStorage.setItem('peppol.theme.v1', globals.theme ?? 'light');
  localStorage.setItem('locale', globals.locale ?? 'en');
}

addons.register('letspeppol/toolbar-storage', (api) => {
  api.on(SET_GLOBALS, store);
  api.on(GLOBALS_UPDATED, store);
});
