import { bindable } from 'aurelia';

type SepaQrInstance = {
  valid(): boolean;
  makeCodeInto(elementId: string, options?: { width: number; height: number; dpi: -1; mmPerDot: -1 }): unknown;
};

type SepaQrOptions = {
  benefName: string;
  benefBIC: string;
  benefAccNr: string;
  amountEuro: number | '';
  purpose: string;
  creditorRef: string;
  remittanceInf: string;
  information: string;
};

type SepaQrConstructor = new (options: SepaQrOptions) => SepaQrInstance;

declare global {
  interface Window {
    sepaQR?: SepaQrConstructor;
    __sepaQrScriptLoad?: Promise<void>;
  }
}

export class SepaQr {
  @bindable public beneficiaryName = '';
  @bindable public beneficiaryBic = '';
  @bindable public beneficiaryAccountNumber = '';
  @bindable public amountEuro: string | number = '';
  @bindable public purpose = '';
  @bindable public creditorReference = '';
  @bindable public remittanceInformation = '';
  @bindable public information = '';
  @bindable public size: string | number = 256;

  public errorMessage = '';

  private qrHost?: HTMLElement;
  private renderToken = 0;

  public attached() {
    void this.render();
  }

  public beneficiaryNameChanged() { void this.render(); }
  public beneficiaryBicChanged() { void this.render(); }
  public beneficiaryAccountNumberChanged() { void this.render(); }
  public amountEuroChanged() { void this.render(); }
  public purposeChanged() { void this.render(); }
  public creditorReferenceChanged() { void this.render(); }
  public remittanceInformationChanged() { void this.render(); }
  public informationChanged() { void this.render(); }
  public sizeChanged() { void this.render(); }

  private async render() {
    const token = ++this.renderToken;

    if (!this.qrHost) {
      return;
    }

    this.qrHost.innerHTML = '';
    this.errorMessage = '';

    if (!this.beneficiaryName || !this.beneficiaryAccountNumber) {
      return;
    }

    try {
      await loadSepaQrScript();

      if (token !== this.renderToken || !this.qrHost || !window.sepaQR) {
        return;
      }

      const id = `sepa-qr-${crypto.randomUUID()}`;
      this.qrHost.id = id;

      const qr = new window.sepaQR({
        benefName: this.beneficiaryName,
        benefBIC: this.beneficiaryBic,
        benefAccNr: this.beneficiaryAccountNumber,
        amountEuro: normalizeAmount(this.amountEuro),
        purpose: this.purpose,
        creditorRef: this.creditorReference,
        remittanceInf: this.remittanceInformation,
        information: this.information
      });

      if (qr.valid()) {
        const size = normalizeSize(this.size);
        qr.makeCodeInto(id, {
          width: size,
          height: size,
          dpi: -1,
          mmPerDot: -1
        });
      }
    } catch (error) {
      this.qrHost.innerHTML = '';
      this.errorMessage = error instanceof Error ? error.message : 'Unable to render SEPA QR code.';
    }
  }

}

function normalizeSize(value: string | number): number {
  if (typeof value === 'number') {
    return value;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 256;
}

function loadSepaQrScript() {
  if (window.sepaQR) {
    return Promise.resolve();
  }

  if (!window.__sepaQrScriptLoad) {
    window.__sepaQrScriptLoad = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = '/vendor/sepaqr/sepaqr.min.js';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Unable to load /vendor/sepaqr/sepaqr.min.js.'));
      document.head.appendChild(script);
    });
  }

  return window.__sepaQrScriptLoad;
}

function normalizeAmount(value: string | number): number | '' {
  if (typeof value === 'number') {
    return value;
  }

  const trimmed = value.trim();

  if (!trimmed) {
    return '';
  }

  return Number(trimmed.replace(',', '.'));
}
