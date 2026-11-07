import type { Meta, Story } from '../support/story';
import { CardReader } from '../../src/components/eid/card-reader';

const meta: Meta = {
  title: 'Components/eID card reader',
  render: () => ({
    template: `
      <div class="ds-frame sheet" style="max-width: 440px">
        <card-reader caption-key.bind="captionKey" reader-label-key.bind="readerLabelKey" signed.bind="signed"></card-reader>
      </div>
    `,
    components: [CardReader],
  }),
  args: {
    captionKey: 'confirmation.anim-caption-validate',
    readerLabelKey: 'confirmation.anim-reader-label',
    signed: false,
  },
  argTypes: {
    captionKey: {
      control: 'select',
      options: ['confirmation.anim-caption-validate', 'confirmation.anim-caption-sign', 'onboarding.anim-caption-sign'],
    },
    readerLabelKey: { control: 'select', options: ['confirmation.anim-reader-label', 'onboarding.anim-reader-label'] },
    signed: { control: 'boolean' },
  },
  parameters: {
    docs: {
      description: {
        component:
          'A small illustration of a Belgian eID card next to a card reader, shown wherever the person has to use their eID: validating identity, signing the contract during registration or when adding a company, and the reader check during onboarding. ' +
          'While waiting, a signature keeps drawing itself on the card and the reader LED pulses; once signed, the signature completes and a green check appears. ' +
          'The caption is the instruction, so it names the next physical step. Motion stops under reduced-motion preferences.',
      },
    },
  },
};

export default meta;

export const Validate: Story = {
  name: 'Waiting to validate identity',
};

export const Sign: Story = {
  name: 'Waiting to sign the contract',
  args: { captionKey: 'confirmation.anim-caption-sign' },
};

export const Signed: Story = {
  name: 'Signed',
  args: { captionKey: 'confirmation.anim-caption-sign', signed: true },
};

export const OnboardingCheck: Story = {
  name: 'Onboarding reader check',
  args: { captionKey: 'onboarding.anim-caption-sign', readerLabelKey: 'onboarding.anim-reader-label' },
};
