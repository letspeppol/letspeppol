import { customAttribute, bindable, INode } from 'aurelia';
import { resolve } from '@aurelia/kernel';
import { I18N, ILocalChangeSubscriber } from '@aurelia/i18n';
import { IFeatureAck } from './feature-ack-service';

const RAINBOW_CLASS = 'rainbow-border';
const HINT_CLASS = 'new-feature-hint new-feature-hint-corner';

@customAttribute({ name: 'new-feature-section', defaultProperty: 'section', noMultiBindings: true })
export class NewFeatureSectionCustomAttribute implements ILocalChangeSubscriber {
    @bindable section: string;

    private readonly element = resolve(INode) as HTMLElement;
    private readonly ack = resolve(IFeatureAck);
    private readonly i18n = resolve(I18N);

    private unsubscribe: (() => void) | null = null;
    private hintEl: HTMLSpanElement | null = null;
    private localeSubscribed = false;

    attaching() {
        this.refresh();
        this.unsubscribe = this.ack.subscribe(() => this.refresh());
    }

    detaching() {
        this.unsubscribe?.();
        this.unsubscribe = null;
        this.element.classList.remove(RAINBOW_CLASS);
        this.removeHint();
        if (this.localeSubscribed) {
            this.i18n.unsubscribeLocaleChange(this);
            this.localeSubscribed = false;
        }
    }

    sectionChanged() {
        this.refresh();
    }

    handleLocaleChange() {
        if (this.hintEl) this.applyHintLabel(this.hintEl);
    }

    private refresh() {
        const active = !!this.section && this.ack.hasUnacknowledgedInSection(this.section);
        this.element.classList.toggle(RAINBOW_CLASS, active);
        if (active) {
            this.ensureHint();
        } else {
            this.removeHint();
        }
    }

    private ensureHint() {
        if (this.hintEl) return;
        const hint = document.createElement('span');
        hint.className = HINT_CLASS;
        hint.setAttribute('aria-hidden', 'true');
        this.applyHintLabel(hint);
        this.element.appendChild(hint);
        this.hintEl = hint;
        if (!this.localeSubscribed) {
            this.i18n.subscribeLocaleChange(this);
            this.localeSubscribed = true;
        }
    }

    private applyHintLabel(hint: HTMLSpanElement) {
        hint.dataset.label = this.i18n.tr('new-feature.label');
    }

    private removeHint() {
        this.hintEl?.remove();
        this.hintEl = null;
    }
}
