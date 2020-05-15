package org.jasig.cas.ticket.registry;

import org.springframework.context.ApplicationEvent;

/**
 * @author Artem R. Romanenko
 * @version 13.02.19
 */
public class IgniteReadyEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public IgniteReadyEvent(Object source) {
        super(source);
    }
}
