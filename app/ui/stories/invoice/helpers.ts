import '../../src/invoice/edit/invoice-edit.css';
import { bindable, customAttribute, CustomElement, INode } from 'aurelia';
import { resolve } from '@aurelia/kernel';
import type { AureliaStoryResult } from '@aurelia/storybook';
import { userEvent, waitFor } from 'storybook/test';
import { invoiceMocks, type InvoiceStoryOptions } from '../support/mocks/invoices';

interface ModalViewModel {
  showModal(...args: unknown[]): unknown;
}

@customAttribute('open-modal')
export class OpenModal {
  @bindable value: unknown = undefined;
  private readonly host = resolve(INode) as HTMLElement;

  attached() {
    const modal = CustomElement.for<ModalViewModel>(this.host).viewModel;
    const args = Array.isArray(this.value) ? this.value : [];
    setTimeout(() => void modal.showModal(...args));
  }
}

export interface PartOptions extends InvoiceStoryOptions {
  components?: unknown[];
  props?: Record<string, unknown>;
}

export function part(markup: string, options: PartOptions = {}): () => AureliaStoryResult {
  const { components = [], props, ...mocks } = options;
  return () => ({
    template: markup,
    components: [OpenModal, ...components],
    items: invoiceMocks(mocks),
    props,
  });
}

export function tile(markup: string, width = '20rem'): string {
  return `<div style="max-width: ${width}">${markup}</div>`;
}

export const modalFrame = { inline: false, iframeHeight: 760 };

export async function ready<T extends Element = HTMLElement>(root: Element, selector: string, timeout = 4000): Promise<T> {
  return waitFor(() => {
    const element = root.querySelector<T>(selector);
    if (!element || !(element as unknown as HTMLElement).getClientRects().length) {
      throw new Error(`Waiting for ${selector}`);
    }
    return element;
  }, { timeout });
}

export async function clickWhenReady(root: Element, selector: string) {
  await userEvent.click(await ready(root, selector));
}
