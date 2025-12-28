import { bindable } from 'aurelia';

export class CardReader {
  @bindable captionKey = 'confirmation.anim-caption';
  @bindable readerLabelKey = 'confirmation.anim-reader-label';
  @bindable signed = false;
}
