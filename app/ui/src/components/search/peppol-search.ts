import {bindable, IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Match, PeppolDirService} from "../../services/peppol/peppol-dir-service";
import {AlertType} from "../alert/alert";

export class PeppolSearch {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly peppolDirService = resolve(PeppolDirService);
    showSuggestions = false;
    peppolMatches: Match[];
    @bindable name;
    @bindable vatNumber;
    @bindable peppolId;
    @bindable selectMatchFunction: (name: string, participantID: string) => void;
    @bindable peppolIdChangedFunction: (peppolId: string) => void;

    async searchPeppolIdByName() {
        try {
            this.ea.publish('showOverlay', "Searching company");
            this.peppolMatches = (await this.peppolDirService.findByName(this.name)).matches;
            this.checkSuggestionsFound();

        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Searching Peppol directory failed"});
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    async searchPeppolIdByVAT() {
        try {
            this.ea.publish('showOverlay', "Searching company");
            this.peppolMatches = (await this.peppolDirService.findByParticipant(this.vatNumber.replace(/\D/g, ''))).matches;
            this.checkSuggestionsFound();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Searching Peppol directory failed"});
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    checkSuggestionsFound() {
        this.showSuggestions = this.peppolMatches.length > 0;
        if (!this.showSuggestions) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "No results found"});
        }
    }

    getName(match: Match) {
        return match.entities?.[0]?.name?.[0].name ?? 'unknown';
    }

    getCountryCode(match: Match) {
        return match.entities?.[0]?.countryCode ?? undefined;
    }

    select(match: Match) {
        this.showSuggestions = false;
        this.peppolId = match.participantID.value;
        if (this.selectMatchFunction) {
            this.selectMatchFunction(this.getName(match), this.peppolId);
        }
    }

    peppolIdInputChanged() {
        if (this.peppolIdChangedFunction) {
            this.peppolIdChangedFunction(this.peppolId);
        }
    }

    onSearchBlur() {
        setTimeout(() => {
            this.showSuggestions = false;
        }, 120);
    }
}