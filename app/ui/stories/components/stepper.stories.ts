import type { Meta, Story } from '../support/story';
import { Stepper } from '../../src/components/stepper/stepper';

const meta: Meta = {
  title: 'Components/Stepper',
  component: Stepper,
  args: {
    steps: [
      'registration.step.company',
      'registration.step.details',
      'registration.step.email',
      'registration.step.identity',
      'registration.step.contract',
    ],
    completedSteps: 1,
  },
  argTypes: {
    completedSteps: { control: { type: 'range', min: 0, max: 5, step: 1 } },
  },
};

export default meta;

export const Default: Story = {};
