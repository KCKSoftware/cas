package org.jasig.cas.ticket.registry;

import org.mockito.Mockito;

/**
 * @author Artem R. Romanenko
 * @version 27/12/2018
 */
public class MockFactory {
    public <T> T mock(Class<T> clazz) {
        return Mockito.mock(clazz);
    }
}
