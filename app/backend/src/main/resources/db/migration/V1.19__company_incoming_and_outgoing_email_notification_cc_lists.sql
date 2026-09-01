ALTER TABLE company
    RENAME COLUMN email_notification_cc_list TO email_notification_cc_list_incoming;

ALTER TABLE company
    ADD COLUMN email_notification_cc_list_outgoing varchar(255);
