import {DonationService, DonationStatsDto} from "../../services/app/donation-service";
import {resolve} from "@aurelia/kernel";

export class Donations {
    private donationService = resolve(DonationService);
    private accountInfo: DonationStatsDto;
    strokeDashOffset: number = 0;
    attached() {
        this.loadLatestDonationInfo()
    }

    async loadLatestDonationInfo() {
        this.accountInfo = await this.donationService.getDonationStats();

        const maxProcessed = this.accountInfo.maxProcessedLastWeek ?? 100;
        const percentageRemaining = this.accountInfo.invoicesRemaining / maxProcessed;
        this.strokeDashOffset = 565.48 -  565.48 * Math.min(1.0, percentageRemaining);

        const circle = document.getElementById('donationCircle');
        circle.style.strokeDashoffset = `${this.strokeDashOffset}px`
        circle.style.stroke = this.statusColor(percentageRemaining);
    }

    hexToHsl(hex) {
        const r = parseInt(hex.substring(1, 3), 16) / 255;
        const g = parseInt(hex.substring(3, 5), 16) / 255;
        const b = parseInt(hex.substring(5, 7), 16) / 255;

        const max = Math.max(r, g, b), min = Math.min(r, g, b);
        let h, s, l = (max + min) / 2;

        if (max === min) {
            h = s = 0;
        } else {
            const d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            switch (max) {
                case r: h = (g - b) / d + (g < b ? 6 : 0); break;
                case g: h = (b - r) / d + 2; break;
                case b: h = (r - g) / d + 4; break;
            }
            h *= 60;
        }

        return { h, s, l };
    }

    // Helper: interpolate between two HSL colors
    lerp(a, b, t) {
        return {
            h: a.h + (b.h - a.h) * t,
            s: a.s + (b.s - a.s) * t,
            l: a.l + (b.l - a.l) * t,
        };
    }

    statusColor(p) {
        p = Math.max(0, Math.min(1, p));

        const red    = this.hexToHsl("#ef4444");
        const yellow = this.hexToHsl("#f59e0b");
        const green  = this.hexToHsl("#10b981");

        let hsl;
        if (p < 0.5) {
            hsl = this.lerp(red, yellow, p * 2);
        } else {
            hsl = this.lerp(yellow, green, (p - 0.5) * 2);
        }

        return `hsl(${hsl.h}, ${hsl.s * 100}%, ${hsl.l * 100}%)`;
    }

}