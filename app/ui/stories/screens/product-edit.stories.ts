import type { Meta, Story } from '../support/story';
import { Products } from '../../src/product/products';
import { ProductContext } from '../../src/product/product-context';
import type { ProductDto } from '../../src/services/app/product-service';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { productServices, shellServices, shopCategories, shopProducts } from '../support/mocks/business';
import { atelier } from '../support/mocks/fixtures';
import { provide } from '../support/mocks/stub';

function editing(product: ProductDto | 'new', session: SessionOptions = {}) {
  return () => {
    const context = new ProductContext();
    shopCategories.forEach((category) => context.addProductCategory(structuredClone(category)));
    if (product === 'new') {
      context.newProduct();
    } else {
      context.selectedProduct = structuredClone(product);
    }
    return { items: [...sessionMocks(session), ...shellServices(), ...productServices(), provide(ProductContext, context)] };
  };
}

const cake = shopProducts.find((product) => product.id === 3);
const longName = shopProducts.find((product) => product.id === 8);

const meta: Meta = {
  title: 'Screens/Products/Edit',
  component: Products,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 860 },
      description: {
        component:
          'The product form has two sheets: what it is (name, description, reference, barcode, category) and what it costs (sale price, VAT rate, cost price, stock). ' +
          'Only the name is required; Save product stays disabled until it is filled. ' +
          'The VAT rate is a choice between the four Belgian rates, never free input. ' +
          'Under 1024px cancel and save move into the sticky action bar, and without the product permission the form is read-only with a single Back to products button.',
      },
    },
  },
};

export default meta;

export const NewProduct: Story = {
  name: 'New product',
  parameters: { docs: { description: { story: 'An empty form; Save product is disabled until the product has a name.' } } },
  render: editing('new'),
};

export const ExistingProduct: Story = {
  name: 'Existing product',
  render: editing(cake),
};

export const LongName: Story = {
  name: 'Long name and description',
  render: editing(longName),
};

export const ReadOnly: Story = {
  name: 'Read-only user',
  parameters: { docs: { description: { story: 'Without the product permission the fields are disabled and the only action is going back.' } } },
  render: editing(cake, {
    company: atelier,
    admin: false,
    permissionMask: CompanyPermission.INVOICE_READ | CompanyPermission.INVOICE_DRAFT,
  }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: editing(cake),
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: editing(cake),
};
