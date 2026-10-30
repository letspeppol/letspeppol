import {bindable} from "aurelia";

export class Stepper {
    @bindable steps: string[] = [];
    @bindable completedSteps = 0;
}
