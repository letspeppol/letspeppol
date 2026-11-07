const config = {
  stories: ['../stories/**/*.mdx', '../stories/**/*.stories.ts'],
  addons: ['@storybook/addon-docs', '@storybook/addon-a11y'],
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
  },
  docs: {
    defaultName: 'Overview',
  },
};

export default config;
