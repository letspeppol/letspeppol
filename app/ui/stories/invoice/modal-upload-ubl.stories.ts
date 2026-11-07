import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { UploadUblModal } from '../../src/invoice/overview/components/upload-ubl-modal';
import { uploadValidation } from '../support/mocks/invoices';
import { modalFrame, part, ready } from './helpers';

const markup = '<upload-ubl-modal open-modal></upload-ubl-modal>';

const ublFile = () => new File(
  ['<?xml version="1.0" encoding="UTF-8"?><Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"><cbc:ID xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">F2026-311</cbc:ID></Invoice>'],
  'F2026-311.xml',
  { type: 'text/xml' },
);

const meta: Meta = {
  title: 'Invoice editor/Modals/Upload UBL',
  component: UploadUblModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Opened from the invoice list: bring in a UBL invoice or credit note made elsewhere, for example by accounting software. ' +
          'The drop zone is one large button; the document type is read from the file. ' +
          'A valid file becomes a draft straight away; an invalid one swaps the drop zone for the validator’s problems and a way to try another file.',
      },
    },
  },
};

export default meta;

export const DropZone: Story = {
  name: 'Choose a file',
  render: part(markup),
};

export const Invalid: Story = {
  name: 'File with validation problems',
  render: part(markup, { validation: uploadValidation }),
  play: async ({ canvasElement }) => {
    await ready(canvasElement, '.upload-wrapper');
    await userEvent.upload(await ready<HTMLInputElement>(canvasElement, 'upload-ubl-modal input[type=file]'), ublFile());
    await ready(canvasElement, '.validation-errors li');
  },
};
