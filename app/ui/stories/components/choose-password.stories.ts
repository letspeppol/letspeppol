import type { Meta, Story } from '../support/story';
import { ChoosePassword } from '../../src/components/choose-password/choose-password';

const meta: Meta = {
  title: 'Components/Choose password',
  render: () => ({
    template: `
      <div class="ds-frame sheet" style="max-width: 440px">
        <choose-password password.bind="password" confirm-password.bind="confirmPassword" show-heading.bind="showHeading"></choose-password>
      </div>
    `,
    components: [ChoosePassword],
  }),
  args: { password: '', confirmPassword: '', showHeading: false },
  parameters: {
    docs: {
      description: {
        component:
          'The password pair used when registering, accepting an invitation, resetting a password and changing it from Account. ' +
          'Nothing but the two fields shows until the person starts typing; then a strength meter and the six rules appear, each rule turning green as it is met. ' +
          'Rules are written as plain requirements, not errors, so the list never scolds. The heading is optional because most hosts already have their own.',
      },
    },
  },
};

export default meta;

export const Empty: Story = {};

export const WithHeading: Story = {
  name: 'With heading',
  args: { showHeading: true },
};

export const Weak: Story = {
  name: 'Weak password',
  args: { password: 'peppol' },
};

export const AlmostThere: Story = {
  name: 'Almost there, no match yet',
  args: { password: 'Bakkerij2026', confirmPassword: 'Bakkerij' },
};

export const Strong: Story = {
  name: 'Strong and matching',
  args: { password: 'Kerkstraat-12-Gent', confirmPassword: 'Kerkstraat-12-Gent' },
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 420 } } },
  args: { password: 'Bakkerij2026', confirmPassword: '' },
};
