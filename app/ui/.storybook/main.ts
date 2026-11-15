const config = {
  stories: ['../stories/**/*.mdx', '../stories/**/*.stories.ts'],
  addons: ['@storybook/addon-docs'],
  framework: {
    name: '@aurelia/storybook',
    options: {},
  },
  core: {
    builder: {
      name: '@storybook/builder-vite',
      options: {
        viteConfigPath: '.storybook/vite.config.ts',
      },
    },
    disableTelemetry: true,
    disableWhatsNewNotifications: true,
  },
  staticDirs: ['../public'],
  features: {
    sidebarOnboardingChecklist: false,
    actions: false,
    backgrounds: false,
    changeDetection: false,
    controls: false,
    interactions: false,
    measure: false,
    outline: false,
  },
  docs: {
    defaultName: 'Overview',
  },
};

export default config;
