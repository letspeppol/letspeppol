import { valueConverter } from 'aurelia';
import moment from 'moment';

@valueConverter('dateFormat')
export class DateFormat {
  toView(value) {
    if (value) {
      return moment(value).format('D/M/YYYY');
    }
    return undefined;
  }
}
