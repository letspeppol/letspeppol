import {resolve} from "@aurelia/kernel";
import * as webeid from '@web-eid/web-eid-library/web-eid';
import {IEventAggregator} from "aurelia";

export class Onboarding {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
}