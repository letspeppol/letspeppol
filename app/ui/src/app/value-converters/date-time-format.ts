import { valueConverter } from 'aurelia';
import moment from 'moment';

@valueConverter('dateTimeFormat')
export class DateTimeFormatConverter {
  toView(value) {
    if (value) {
      return moment(value).format('D/M/YYYY HH:mm');
    }
    return undefined;
  }
}
