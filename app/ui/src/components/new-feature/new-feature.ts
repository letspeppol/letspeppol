import { customAttribute, bindable, INode } from 'aurelia';
import { resolve } from '@aurelia/kernel';
import { I18N, ILocalChangeSubscriber } from '@aurelia/i18n';
import { IFeatureAck } from './feature-ack-service';
import { isExpired } from './feature-registry';

export type NewFeatureTrigger = 'click' | 'view' | 'hover' | 'none';

const RAINBOW_CLASS = 'rainbow-border';
const DISMISSABLE_CLASS = 'new-feature-dismissable';

@customAttribute({ name: 'new-feature', defaultProperty: 'id', noMultiBindings: true })
export class NewFeatureCustomAttribute implements ILocalChangeSubscriber {
    @bindable id: string;
    @bindable trigger: NewFeatureTrigger = 'click';
    @bindable dwell: number = 3000;

    private readonly element = resolve(INode) as HTMLElement;
    private readonly ack = resolve(IFeatureAck);
    private readonly i18n = resolve(I18N);

    private cleanup: (() => void) | null = null;
    private hintEl: HTMLSpanElement | null = null;
    private localeSubscribed = false;

    attaching() {
        if (!this.id) return;
        if (this.ack.isAcknowledged(this.id) || isExpired(this.id)) return;

        this.element.classList.add(RAINBOW_CLASS);

        switch (this.trigger) {
            case 'click':
                this.element.classList.add(DISMISSABLE_CLASS);
                this.injectHint();
                this.bindOnce('click');
                break;
            case 'hover':
                this.element.classList.add(DISMISSABLE_CLASS);
                this.injectHint();
                this.bindOnce('mouseenter');
                break;
            case 'view':
                this.bindIntersection();
                break;
            case 'none':
                // wayfinder mode on the feature itself: highlight, don't dismiss
                break;
        }
    }

    detaching() {
        this.cleanup?.();
        this.cleanup = null;
        this.removeHint();
        this.element.classList.remove(DISMISSABLE_CLASS);
        if (this.localeSubscribed) {
            this.i18n.unsubscribeLocaleChange(this);
            this.localeSubscribed = false;
        }
    }

    handleLocaleChange() {
        if (this.hintEl) this.applyHintLabels(this.hintEl);
    }

    private bindOnce(eventName: 'click' | 'mouseenter') {
        const handler = () => this.dismiss();
        this.element.addEventListener(eventName, handler, { once: true });
        this.cleanup = () => this.element.removeEventListener(eventName, handler);
    }

    private bindIntersection() {
        if (typeof IntersectionObserver === 'undefined') return;

        let dwellTimer: number | null = null;
        const observer = new IntersectionObserver((entries) => {
            for (const entry of entries) {
                if (entry.isIntersecting) {
                    if (dwellTimer === null) {
                        dwellTimer = window.setTimeout(() => this.dismiss(), this.dwell);
                    }
                } else if (dwellTimer !== null) {
                    clearTimeout(dwellTimer);
                    dwellTimer = null;
                }
            }
        });
        observer.observe(this.element);

        this.cleanup = () => {
            if (dwellTimer !== null) clearTimeout(dwellTimer);
            observer.disconnect();
        };
    }

    private injectHint() {
        const hint = document.createElement('span');
        hint.className = 'new-feature-hint';
        hint.setAttribute('aria-hidden', 'true');
        this.applyHintLabels(hint);
        this.element.appendChild(hint);
        this.hintEl = hint;
        if (!this.localeSubscribed) {
            this.i18n.subscribeLocaleChange(this);
            this.localeSubscribed = true;
        }
    }

    private applyHintLabels(hint: HTMLSpanElement) {
        hint.dataset.label = this.i18n.tr('new-feature.label');
        hint.dataset.hint = this.i18n.tr('new-feature.click-to-dismiss');
    }

    private removeHint() {
        this.hintEl?.remove();
        this.hintEl = null;
    }

    private dismiss() {
        this.cleanup?.();
        this.cleanup = null;
        this.removeHint();
        this.element.classList.remove(RAINBOW_CLASS, DISMISSABLE_CLASS);
        this.ack.acknowledge(this.id);
    }
}
