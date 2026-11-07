import type { Meta, Story } from '../support/story';
import { ValidationResultModal } from '../../src/invoice/edit/components/modals/validation-result-modal';
import { failedValidation, uploadValidation } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const markup = '<validation-result-modal open-modal.bind="[result]"></validation-result-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Validation result',
  component: ValidationResultModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Shown when the Peppol validator rejects a document before it is sent. ' +
          'The title says what to do, the count says how much, and the list repeats the validator’s messages verbatim, rule code first, so support can look them up. ' +
          'The only action leads back to the document.',
      },
    },
  },
};

export default meta;

export const ThreeProblems: Story = {
  name: 'Three problems',
  render: part(markup, { props: { result: failedValidation } }),
};

export const TwoProblems: Story = {
  name: 'Two problems',
  render: part(markup, { props: { result: uploadValidation } }),
};
