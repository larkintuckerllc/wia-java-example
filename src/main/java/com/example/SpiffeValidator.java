package com.example;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

class SpiffeValidator {

    static final String SPIFFE_URI = "spiffe://jtucker-wia-d.svc.id.goog/ns/debug/sa/default";

    static void verifySpiffeUri(X509Certificate cert) throws CertificateException {
        var sans = cert.getSubjectAlternativeNames();
        if (sans != null) {
            for (var san : sans) {
                if (Integer.valueOf(6).equals(san.get(0)) && SPIFFE_URI.equals(san.get(1))) {
                    return;
                }
            }
        }
        throw new CertificateException("Certificate does not contain expected SPIFFE URI: " + SPIFFE_URI);
    }
}
