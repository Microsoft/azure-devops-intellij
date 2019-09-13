// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.mocks;

import com.microsoft.alm.plugin.services.CertificateService;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

public class MockCertificateService implements CertificateService {
    @Override
    public SSLContext getSSLContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
