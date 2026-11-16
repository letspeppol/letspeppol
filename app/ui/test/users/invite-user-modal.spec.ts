import {describe, expect, it, vi} from 'vitest';
import {InviteUserModal} from '../../src/users/invite-user-modal';

function errorResponse(errorCode: string): Response {
    return new Response(JSON.stringify({errorCode}), {status: 400});
}

function createModal() {
    const companyUserService = {inviteUser: vi.fn()};
    const invited = vi.fn();
    const savedWithoutEmail = vi.fn();
    const modal = Object.create(InviteUserModal.prototype) as InviteUserModal;
    Object.assign(modal, {
        companyUserService,
        i18n: {tr: (key: string) => key},
    });
    modal.showModal(invited, savedWithoutEmail);
    modal.name = 'An Peeters';
    modal.email = 'an@example.be';
    return {modal, companyUserService, invited, savedWithoutEmail};
}

describe('InviteUserModal', () => {
    it('hands the invited user back and closes', async () => {
        const {modal, companyUserService, invited, savedWithoutEmail} = createModal();
        const user = {id: 7, email: 'an@example.be'};
        companyUserService.inviteUser.mockResolvedValue(user);

        await modal.invite();

        expect(companyUserService.inviteUser).toHaveBeenCalledWith({email: 'an@example.be', name: 'An Peeters', permissionMask: 1});
        expect(invited).toHaveBeenCalledWith(user);
        expect(savedWithoutEmail).not.toHaveBeenCalled();
        expect(modal.open).toBe(false);
    });

    it('closes and reports a saved invitation whose email could not be sent', async () => {
        const {modal, companyUserService, invited, savedWithoutEmail} = createModal();
        companyUserService.inviteUser.mockRejectedValue(errorResponse('invitation_not_sent'));

        await modal.invite();

        expect(savedWithoutEmail).toHaveBeenCalledOnce();
        expect(invited).not.toHaveBeenCalled();
        expect(modal.open).toBe(false);
        expect(modal.errorText).toBe('');
        expect(modal.submitting).toBe(false);
    });

    it('stays open and shows why another failure happened', async () => {
        const {modal, companyUserService, savedWithoutEmail} = createModal();
        companyUserService.inviteUser.mockRejectedValue(errorResponse('user_already_member'));

        await modal.invite();

        expect(savedWithoutEmail).not.toHaveBeenCalled();
        expect(modal.open).toBe(true);
        expect(modal.errorText).toBe('alert.users.invite-failed');
    });
});
