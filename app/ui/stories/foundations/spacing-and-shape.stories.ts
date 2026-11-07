import type { Meta, Story } from '../support/story';
import { template } from '../support/story';

const meta: Meta = {
  title: 'Foundations/Spacing and shape',
  parameters: {
    docs: {
      description: {
        component:
          'A 4px-based spacing scale, one radius for sheets and controls, and flat sheets. Only floating things get a shadow. ' +
          'Old names such as `--space-05` stay as aliases for existing CSS; new CSS uses the numbered scale below.',
      },
    },
  },
};

export default meta;

const spaces = ['1', '2', '3', '4', '5', '6', '8', '12'];

export const SpacingScale: Story = {
  name: 'Spacing scale',
  render: () => ({
    template: `
      <div class="ds-page">
        <ds-section heading="Spacing scale" lead="Gaps, padding and margins come from this scale.">
          <div class="card ds-stack">
            <div repeat.for="step of spaces" class="ds-space-row">
              <ds-token token="--space-\${step}"></ds-token>
              <span></span>
              <span class="ds-bar" style="width: var(--space-\${step})"></span>
            </div>
          </div>
        </ds-section>
        <ds-section heading="Page gutters and width" lead="16px under 768px, 24px to 1023px, 32px from 1024px. Content stops at 1460px.">
          <div class="card ds-stack">
            <div class="ds-space-row"><ds-token token="--gutter"></ds-token><span></span><span class="ds-bar" style="width: var(--gutter)"></span></div>
            <div class="ds-space-row"><ds-token token="--container-max"></ds-token><span></span><span></span></div>
            <div class="ds-space-row"><ds-token token="--control-h"></ds-token><span></span><span class="ds-bar" style="width: var(--control-h)"></span></div>
            <div class="ds-space-row"><ds-token token="--touch-target"></ds-token><span></span><span class="ds-bar" style="width: var(--touch-target)"></span></div>
            <div class="ds-space-row"><ds-token token="--header-h"></ds-token><span></span><span class="ds-bar" style="width: var(--header-h)"></span></div>
          </div>
        </ds-section>
      </div>
    `,
    props: { spaces },
  }),
};

export const RadiusAndElevation: Story = {
  name: 'Radius and elevation',
  render: template(`
    <div class="ds-page">
      <ds-section heading="Radius" lead="One radius for sheets and controls. Pills and avatars are fully round. Bottom sheets on phones round their top corners only.">
        <div class="ds-grid">
          <div class="ds-stack">
            <div class="ds-shape" style="border-radius: var(--radius)">Sheet, control</div>
            <ds-token token="--radius"></ds-token>
          </div>
          <div class="ds-stack">
            <div class="ds-shape" style="border-radius: var(--radius-pill); height: 2.5rem">Pill, avatar</div>
            <ds-token token="--radius-pill"></ds-token>
          </div>
          <div class="ds-stack">
            <div class="ds-shape" style="border-radius: var(--radius-sheet) var(--radius-sheet) 0 0">Bottom sheet</div>
            <ds-token token="--radius-sheet"></ds-token>
          </div>
        </div>
      </ds-section>
      <ds-section heading="Elevation" lead="Sheets are flat: a 1px rule, no shadow. Menus, drawers, modals, toasts and the sticky action bar float.">
        <div class="ds-grid">
          <div class="ds-stack">
            <div class="ds-shape" style="border-radius: var(--radius)">Flat sheet</div>
            <span class="ds-label">1px --rule, no shadow</span>
          </div>
          <div class="ds-stack">
            <div class="ds-shape" style="border-radius: var(--radius); box-shadow: var(--shadow-float)">Floating</div>
            <ds-token token="--shadow-float"></ds-token>
          </div>
        </div>
      </ds-section>
      <ds-section heading="Rules">
        <div class="ds-stack">
          <ds-rule kind="dont">Nest cards inside cards. Group with spacing and a soft rule instead.</ds-rule>
          <ds-rule kind="dont">Give a sheet a shadow to make it stand out. If it needs to stand out, it probably needs the orange marker or a better position.</ds-rule>
        </div>
      </ds-section>
    </div>
  `),
};
