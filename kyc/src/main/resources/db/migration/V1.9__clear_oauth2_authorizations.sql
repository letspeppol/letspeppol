-- Rows written by the Jackson 2 build serialise the AccountUserDetails principal through its
-- getters, so they carry `accountNonLocked`/`enabled` instead of the `locked`/`verified`
-- constructor fields and deserialise with both flags wrong. Nothing here outlives an hour
-- (no refresh token grant, 1h access tokens, 5m authorization codes) and /sapi/** validates
-- JWTs by signature, so dropping them costs at most a retried login.
DELETE FROM oauth2_authorization;
