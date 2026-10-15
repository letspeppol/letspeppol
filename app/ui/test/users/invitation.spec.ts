import {afterEach, describe, expect, it, vi} from 'vitest';
import type {Params, RouteNode} from '@aurelia/router';
import {InvitationInfo} from '../../src/services/kyc/company-user-service';
import {Invitation} from '../../src/users/invitation';

const info: InvitationInfo = {
    email: 'an@example.be',
    name: 'An Peeters',
    companyName: 'Peeters & Zonen',
    peppolId: '0208:0123456789',
    passwordRequired: true,
};

function createInvitation(token: string | null = 'invite-token') {
    const companyUserService = {
        verifyInvitation: vi.fn(),
        acceptInvitation: vi.fn(),
    };
    const invitation = Object.create(Invitation.prototype) as Invitation;
    Object.assign(invitation, {
        companyUserService,
        i18n: {tr: (key: string) => `tr:${key}`},
        token,
        info: undefined,
        verifying: true,
        submitting: false,
        accepted: false,
        errorText: '',
        password: '',
        confirmPassword: '',
        choosePassword: {rules: {pwStrong: true, matchOk: true}},
    });
    return {invitation, companyUserService};
}

function errorResponse(errorCode: string): Response {
    return new Response(JSON.stringify({errorCode}), {status: 400});
}

afterEach(() => {
    history.replaceState({}, '', '/');
});

describe('Invitation', () => {
    it('reads the token from the link, hides it and verifies it', async () => {
        history.replaceState({}, '', '/invitation?token=invite-token');
        const {invitation, companyUserService} = createInvitation(null);
        companyUserService.verifyInvitation.mockResolvedValue(info);

        invitation.loading({} as Params, {queryParams: new URLSearchParams('token=invite-token')} as unknown as RouteNode);
        await vi.waitFor(() => expect(invitation.verifying).toBe(false));

        expect(companyUserService.verifyInvitation).toHaveBeenCalledWith('invite-token');
        expect(invitation.info).toEqual(info);
        expect(window.location.search).toBe('');
    });

    it('does not call the server without a token', async () => {
        const {invitation, companyUserService} = createInvitation(null);

        await invitation.verify();

        expect(companyUserService.verifyInvitation).not.toHaveBeenCalled();
        expect(invitation.info).toBeUndefined();
        expect(invitation.errorText).toBe('tr:error.invitation_not_found');
        expect(invitation.verifying).toBe(false);
    });

    it('explains why an invitation is refused', async () => {
        const {invitation, companyUserService} = createInvitation();
        companyUserService.verifyInvitation.mockRejectedValue(errorResponse('invitation_expired'));

        await invitation.verify();

        expect(invitation.info).toBeUndefined();
        expect(invitation.errorText).toBe('tr:error.invitation_expired');
    });

    it('sets the chosen password for a new account', async () => {
        const {invitation, companyUserService} = createInvitation();
        Object.assign(invitation, {info, password: 'Correct-Horse-9-Battery'});
        companyUserService.acceptInvitation.mockResolvedValue(undefined);

        await invitation.accept();

        expect(companyUserService.acceptInvitation).toHaveBeenCalledWith({token: 'invite-token', newPassword: 'Correct-Horse-9-Battery'});
        expect(invitation.accepted).toBe(true);
    });

    it('waits for a strong, confirmed password before accepting', async () => {
        const {invitation, companyUserService} = createInvitation();
        Object.assign(invitation, {info, password: 'weak', choosePassword: {rules: {pwStrong: false, matchOk: true}}});

        await invitation.accept();

        expect(companyUserService.acceptInvitation).not.toHaveBeenCalled();
        expect(invitation.accepted).toBe(false);
    });

    it('accepts without a password when the account already exists', async () => {
        const {invitation, companyUserService} = createInvitation();
        Object.assign(invitation, {info: {...info, passwordRequired: false}});
        companyUserService.acceptInvitation.mockResolvedValue(undefined);

        await invitation.accept();

        expect(companyUserService.acceptInvitation).toHaveBeenCalledWith({token: 'invite-token'});
        expect(invitation.accepted).toBe(true);
    });

    it('keeps the form and shows the reason when accepting fails', async () => {
        const {invitation, companyUserService} = createInvitation();
        Object.assign(invitation, {info: {...info, passwordRequired: false}});
        companyUserService.acceptInvitation.mockRejectedValue(errorResponse('invitation_not_found'));

        await invitation.accept();

        expect(invitation.accepted).toBe(false);
        expect(invitation.submitting).toBe(false);
        expect(invitation.errorText).toBe('tr:error.invitation_not_found');
    });
});
