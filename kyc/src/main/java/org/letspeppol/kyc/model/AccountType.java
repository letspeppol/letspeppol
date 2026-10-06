package org.letspeppol.kyc.model;

public enum AccountType {
    ADMIN,          //This account is the first user/director that signed the contract
    USER,           //This account can read and write invoices, but not user management or peppol registration
    APP,            //This account is an app that receives for multiple users
//  APP_USER,       //This account is an app can request USER JWT for operating as USER --> OAuth2 should be used
    AFFILIATE       //This account is an affiliate part of Company and can send registration requests with custom url forward on approval, useful for accountants via App that can download invoices that are flagged for accounting
}
