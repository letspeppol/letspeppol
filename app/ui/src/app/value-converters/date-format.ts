import { valueConverter } from 'aurelia';
import moment from 'moment';

@valueConverter('dateFormat')
export class DateFormatConverter {
  toView(value) {
    if (value) {
      return moment(value).format('D/M/YYYY');
    }
    return undefined;
  }
}
