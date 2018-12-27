package org.jasig.cas.ticket.registry;

/**
 * @author Artem R. Romanenko
 * @version 26/12/2018
 */
public interface IgniteTicketRegistryCleaner {
    void start();

    void stop();
}
