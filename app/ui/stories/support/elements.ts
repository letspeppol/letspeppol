import { bindable, customElement } from 'aurelia';

function readToken(name: string, element: Element = document.documentElement): string {
  return getComputedStyle(element).getPropertyValue(name).trim();
}

function resolveColor(cssColor: string): [number, number, number, number] | null {
  const canvas = document.createElement('canvas');
  canvas.width = 1;
  canvas.height = 1;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context || !cssColor) {
    return null;
  }
  context.clearRect(0, 0, 1, 1);
  context.fillStyle = cssColor;
  context.fillRect(0, 0, 1, 1);
  const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
  return [r, g, b, a / 255];
}

function relativeLuminance([r, g, b]: [number, number, number, number]): number {
  const channel = (value: number) => {
    const srgb = value / 255;
    return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrastRatio(foreground: string, background: string): number | null {
  const fg = resolveColor(foreground);
  const bg = resolveColor(background);
  if (!fg || !bg) {
    return null;
  }
  const light = Math.max(relativeLuminance(fg), relativeLuminance(bg));
  const dark = Math.min(relativeLuminance(fg), relativeLuminance(bg));
  return (light + 0.05) / (dark + 0.05);
}

function toHex(cssColor: string): string {
  const rgba = resolveColor(cssColor);
  if (!rgba) {
    return cssColor;
  }
  const [r, g, b, a] = rgba;
  const hex = `#${[r, g, b].map((value) => value.toString(16).padStart(2, '0')).join('')}`.toUpperCase();
  return a < 1 ? `${hex} @ ${Math.round(a * 100)}%` : hex;
}

abstract class ThemeAware {
  private observer?: MutationObserver;

  attached() {
    this.refresh();
    this.observer = new MutationObserver(() => this.refresh());
    this.observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  }

  detaching() {
    this.observer?.disconnect();
  }

  abstract refresh(): void;
}

@customElement({
  name: 'ds-swatch',
  template: `
    <div class="ds-swatch">
      <div class="ds-swatch-chip" style="background: var(\${token})">
        <span if.bind="on" class="ds-swatch-sample" style="color: var(\${on})">Aa</span>
      </div>
      <div class="ds-swatch-meta">
        <code>\${token}</code>
        <span class="ds-swatch-value">\${value}</span>
        <span if.bind="ratio" class="ds-swatch-ratio \${ratio >= 4.5 ? 'pass' : ratio >= 3 ? 'large' : 'fail'}">
          \${on} on it: \${ratio.toFixed(2)}:1 \${ratio >= 4.5 ? 'AA' : ratio >= 3 ? 'AA large only' : 'fails'}
        </span>
        <span class="ds-swatch-usage" if.bind="usage">\${usage}</span>
      </div>
    </div>
  `,
})
export class DsSwatch extends ThemeAware {
  @bindable token = '';
  @bindable usage = '';
  @bindable on = '';
  value = '';
  ratio: number | null = null;

  refresh() {
    this.value = toHex(readToken(this.token));
    this.ratio = this.on ? contrastRatio(readToken(this.on), readToken(this.token)) : null;
  }
}

@customElement({
  name: 'ds-contrast',
  template: `
    <div class="ds-contrast" style="background: var(\${background}); color: var(\${foreground})">
      <span class="ds-contrast-sample">\${label || 'Sample text'}</span>
      <span class="ds-contrast-pair"><code>\${foreground}</code> on <code>\${background}</code></span>
      <span class="ds-contrast-ratio \${ratio >= 4.5 ? 'pass' : ratio >= 3 ? 'large' : 'fail'}">
        \${ratio ? ratio.toFixed(2) + ':1' : ''} \${ratio >= 4.5 ? 'AA' : ratio >= 3 ? 'AA large' : 'fails AA'}
      </span>
    </div>
  `,
})
export class DsContrast extends ThemeAware {
  @bindable foreground = '';
  @bindable background = '';
  @bindable label = '';
  ratio: number | null = null;

  refresh() {
    this.ratio = contrastRatio(readToken(this.foreground), readToken(this.background));
  }
}

@customElement({
  name: 'ds-token',
  template: `<code class="ds-token">\${token}</code><span class="ds-token-value">\${value}</span>`,
})
export class DsToken extends ThemeAware {
  @bindable token = '';
  value = '';

  refresh() {
    this.value = readToken(this.token);
  }
}

@customElement({
  name: 'ds-section',
  template: `
    <section class="ds-section">
      <header class="ds-section-header">
        <h2 class="ds-section-title">\${heading}</h2>
        <p class="ds-section-lead" if.bind="lead">\${lead}</p>
      </header>
      <au-slot></au-slot>
    </section>
  `,
})
export class DsSection {
  @bindable heading = '';
  @bindable lead = '';
}

@customElement({
  name: 'ds-rule',
  template: `
    <div class="ds-rule \${kind}">
      <span class="ds-rule-kind">\${kind === 'do' ? 'Do' : kind === 'dont' ? 'Don’t' : 'Note'}</span>
      <div class="ds-rule-body"><au-slot></au-slot></div>
    </div>
  `,
})
export class DsRule {
  @bindable kind: 'do' | 'dont' | 'note' = 'note';
}


const BREAKPOINTS = [
  { name: 'Small phone', query: '(width <= 480px)' },
  { name: 'Phone', query: '(width <= 767px)' },
  { name: 'Phone portrait (bottom bar)', query: '(width <= 767px) and (orientation: portrait)' },
  { name: 'Phone landscape', query: '(height <= 500px) and (orientation: landscape)' },
  { name: 'Tablet', query: '(768px <= width <= 1023px)' },
  { name: 'Tablet and down', query: '(width <= 1023px)' },
  { name: 'Desktop', query: '(width >= 1024px)' },
  { name: 'Touch', query: '(pointer: coarse)' },
  { name: 'Reduced motion', query: '(prefers-reduced-motion: reduce)' },
];

@customElement({
  name: 'ds-breakpoints',
  template: `
    <div class="ds-breakpoints card">
      <p class="ds-breakpoints-size tabular">Viewport \${width} × \${height}px</p>
      <ul>
        <li repeat.for="bp of states" matches.class="bp.matches">
          <span class="ds-breakpoints-dot" aria-hidden="true"></span>
          <strong>\${bp.name}</strong>
          <code>\${bp.query}</code>
          <span class="visually-hidden">\${bp.matches ? 'active' : 'inactive'}</span>
        </li>
      </ul>
    </div>
  `,
})
export class DsBreakpoints {
  width = 0;
  height = 0;
  states = BREAKPOINTS.map((bp) => ({ ...bp, matches: false }));

  attached() {
    this.update();
    window.addEventListener('resize', this.update);
  }

  detaching() {
    window.removeEventListener('resize', this.update);
  }

  private readonly update = () => {
    this.width = window.innerWidth;
    this.height = window.innerHeight;
    this.states = BREAKPOINTS.map((bp) => ({ ...bp, matches: window.matchMedia(bp.query).matches }));
  };
}

export const DesignSystemElements = [DsSwatch, DsContrast, DsToken, DsSection, DsRule, DsBreakpoints];
