# Company user invitation

Executable proof: [`CompanyUserTest`](../kyc/src/test/java/org/letspeppol/kyc/controller/CompanyUserTest.java), methods `adminInvitesNewUser`, `invitedUserAcceptsAndSignsInWithGrantedPermissions` and `existingAccountAcceptsWithoutChoosingAPassword`.

```mermaid
sequenceDiagram
    actor ADMIN as ADMIN
    actor USER as USER
    participant Frontend as Frontend
    participant KYC as KYC

    Note over ADMIN, KYC: Executable proof: CompanyUserTest.adminInvitesNewUser

Note over ADMIN, KYC: Inviting a user
    Note left of ADMIN: Already logged in as ADMIN <br> Visit /{peppolId}/users
    ADMIN ->> Frontend: Invite( email, name, permissions )
    Frontend ->> KYC: POST /kyc/sapi/users <br> Authorization: Bearer ADMIN_JWT <br> ( email, name, permissionMask )
    Note right of KYC: JWT == ADMIN <br> ADMIN ownership is ACTIVE in the database <br> Email is not yet a member <br> Find or create pending Account <br> Link as INVITED USER with normalised mask <br> Generate invitation token
    KYC ->> USER: Mail "You have been invited" /invitation?token={token}
    KYC ->> Frontend: CompanyUserDto ( status = INVITED )
    Frontend ->> ADMIN: Show invited user in the matrix

Note over ADMIN, KYC: Executable proof: CompanyUserTest.invitedUserAcceptsAndSignsInWithGrantedPermissions

Note over ADMIN, KYC: Accepting the invitation
    Note left of USER: Receives email
    USER ->> Frontend: Open link in email
    Frontend ->> KYC: POST /kyc/api/invitation/verify?token={token}
    Note right of KYC: Validate token <br> Token not expired
    KYC ->> Frontend: InvitationInfo <br> ( email, name, companyName, peppolId, passwordRequired )
    Frontend ->> USER: Show company & inviter context

    alt Account is new
        USER ->> Frontend: Input( password, repeat password )
        Frontend ->> KYC: POST /kyc/api/invitation/accept <br> ( token, newPassword )
        Note right of KYC: Store password <br> Mark account verified
    else Account already exists
        USER ->> Frontend: Confirm()
        Frontend ->> KYC: POST /kyc/api/invitation/accept <br> ( token )
        Note right of KYC: Keep existing credentials
    end
    Note right of KYC: Ownership becomes ACTIVE <br> Delete invitation token
    KYC ->> Frontend: 204 No Content
    Frontend ->> USER: Show success & link to login

Note over ADMIN, KYC: Signing in as the invited user
    USER ->> Frontend: Login( email, password )
    Frontend ->> KYC: OAuth2 Authorization Code + PKCE (see login.md) <br> acting ownership = ( AccountType.USER, peppolId )
    Note right of KYC: Validate credentials <br> Validate ACTIVE ownership USER for peppolId
    KYC ->> Frontend: JWT ( AccountType.USER, peppolId, peppolActive, uid, permissionMask )
```

The ADMIN can afterwards change the mask, suspend, reactivate or remove the user and resend a pending
invitation; see [Company users, invitations, and permissions](./api-company-users.md) for those routes,
the permission bits and the error codes.
