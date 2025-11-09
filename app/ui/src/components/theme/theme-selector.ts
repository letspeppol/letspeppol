import {resolve} from '@aurelia/kernel';
import {ThemeService} from '../../services/app/theme-service';

export class ThemeSelector {
    private theme = resolve(ThemeService);
}
