import { fireEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Products } from '../../src/product/products';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { findElement, productServices, shellServices, type ProductServiceOptions } from '../support/mocks/business';
import { atelier } from '../support/mocks/fixtures';
import { pending, resolved } from '../support/mocks/stub';

function screen(options: ProductServiceOptions = {}, session: SessionOptions = {}) {
  return () => ({ items: [...sessionMocks(session), ...shellServices(), ...productServices(options)] });
}

const readOnly: SessionOptions = {
  company: atelier,
  admin: false,
  permissionMask: CompanyPermission.INVOICE_READ | CompanyPermission.INVOICE_DRAFT,
};

async function editCategory(canvasElement: HTMLElement, segment: number) {
  fireEvent.click(await findElement(canvasElement, '.product-toolbar .segment', segment));
  fireEvent.click(await findElement(canvasElement, '.product-toolbar-end .btn.ghost'));
}

const meta: Meta = {
  title: 'Screens/Products/Overview',
  component: Products,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 820 },
      description: {
        component:
          'The product list holds what a company sells, so invoice lines can fill in name, price and VAT rate. ' +
          'Categories are the segmented filter above the table; each carries its colour dot and a count, and the selected one can be edited in place. ' +
          'Prices, VAT and stock are right-aligned tabular figures. On phones each product becomes a card and the category dialog becomes a bottom sheet. ' +
          'Without the product permission the list is read-only: the New category and New product buttons and the delete column are simply absent.',
      },
    },
  },
};

export default meta;

export const Default: Story = {
  name: 'Products with categories',
  render: screen(),
};

export const CategoryFilter: Story = {
  name: 'Filtered on a category',
  parameters: { docs: { description: { story: 'Selecting a category filters the table and reveals Edit category next to the search.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    await findElement(canvasElement, '.product-row');
    fireEvent.click(await findElement(canvasElement, '.product-toolbar .segment', 2));
  },
};

export const Empty: Story = {
  name: 'No products yet',
  parameters: { docs: { description: { story: 'A company without products or categories gets one line and one button.' } } },
  render: screen({ products: resolved([]), categories: resolved([]) }),
};

export const Loading: Story = {
  name: 'Loading',
  parameters: { docs: { description: { story: 'Until the products arrive the table shows only its head and the All segment has no count.' } } },
  render: screen({ products: pending() }),
};

export const ReadOnly: Story = {
  name: 'Read-only user',
  render: screen({}, readOnly),
};

export const NewCategory: Story = {
  name: 'New category dialog',
  parameters: { docs: { description: { story: 'A name and a colour are all a category has; Save category stays disabled until there is a name.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    await findElement(canvasElement, '.product-row');
    fireEvent.click(await findElement(canvasElement, '.section-header .actions .btn:not(.primary)'));
  },
};

export const EditCategory: Story = {
  name: 'Edit category dialog',
  parameters: { docs: { description: { story: 'Editing adds the destructive Delete category button on the left of the footer.' } } },
  render: screen(),
  play: async ({ canvasElement }) => editCategory(canvasElement, 2),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: screen(),
};

export const Phone: Story = {
  name: 'Phone, stacked cards',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen(),
};

export const PhoneCategorySheet: Story = {
  name: 'Phone, category bottom sheet',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen(),
  play: async ({ canvasElement }) => editCategory(canvasElement, 3),
};
