package org.jasig.cas.authentication;

import java.io.Serializable;

/**
 * @author Artem R. Romanenko
 * @version 24/12/2018
 */
public class BasicCredentialMetaDataWithExternalId extends BasicCredentialMetaData implements CredentialMetaDataWithExternalId, Serializable {

    private static final long serialVersionUID = 5899219330147849387L;

    private final String externalId;

    /**
     * Creates a new instance from the given credential.
     *
     * @param credential Credential for which metadata should be created.
     * @param externalId External id
     */
    public BasicCredentialMetaDataWithExternalId(Credential credential, String externalId) {
        super(credential);
        this.externalId = externalId;
    }

    @Override
    public String getExternalId() {
        return externalId;
    }
}
