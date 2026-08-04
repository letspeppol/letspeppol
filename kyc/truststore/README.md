# Belgian eID trust store

`belgian-eid.jks` is generated from the official Belgian eID repositories. It contains the
Belgium Root CA 1–4 and CA6 trust roots, plus the published Citizen CA and Foreigner CA
intermediates. Those intermediates are required here because Web-eID sends only the signer
certificate and the JVM does not automatically download the missing issuer certificate.

Rebuild it whenever the official CA inventory changes:

```bash
cd kyc/truststore
WEBEID_TRUSTED_CA_TRUSTSTORE_PASSWORD='choose-a-password' ./build-belgian-eid-truststore.sh
```

Use the resulting path and password in the KYC service:

```properties
webeid.trusted-ca-truststore=/absolute/path/to/belgian-eid.jks
webeid.trusted-ca-truststore-password=${WEBEID_TRUSTED_CA_TRUSTSTORE_PASSWORD}
webeid.check-revocation=true
```

The checked-in store uses the conventional `changeit` password. A trust-store password does
not protect the public certificates; set a deployment-specific value when rebuilding if desired.

The CA sources are the official [legacy eID repository](https://repository.eid.belgium.be/) and
[current eID PKI repository](https://repository.eidpki.belgium.be/#/download). The latter
publishes the current EC hierarchy below Belgium Root CA6; the former retains the RSA-card
hierarchy. This store is intentionally scoped to physical Belgian eID Citizen/Foreigner signing
chains, rather than trusting every Belgian QTSP.
