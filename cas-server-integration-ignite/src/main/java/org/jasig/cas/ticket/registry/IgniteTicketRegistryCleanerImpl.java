package org.jasig.cas.ticket.registry;

import org.apache.ignite.Ignite;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.resources.SpringResource;
import org.jasig.cas.CentralAuthenticationService;
import org.jasig.cas.ticket.ExpirationPolicy;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.TicketGrantingTicket;

import org.jasig.cas.ticket.support.NeverExpiresExpirationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Artem R. Romanenko
 * @version 26/12/2018
 */
class IgniteTicketRegistryCleanerImpl implements IgniteTicketRegistryCleaner, InitializingBean {
    protected static final Logger logger = LoggerFactory.getLogger(IgniteTicketRegistryCleanerImpl.class);
    @Autowired
    private Ignite ignite;

    @Autowired
    private CentralAuthenticationService centralAuthenticationService;

    @Autowired
    @Qualifier("grantingTicketExpirationPolicy")
    private ExpirationPolicy ticketExpirationPolicy;

    @Value("${ticket.registry.cleaner.startdelay:20}")
    private int startDelay;


    private TicketExpireEventListener serviceTicketEvictEventListener;

    @Override
    public void afterPropertiesSet() throws Exception {
        serviceTicketEvictEventListener = new TicketExpireEventListener();
        serviceTicketEvictEventListener.setCentralAuthenticationService(centralAuthenticationService);
        if (!(ticketExpirationPolicy instanceof NeverExpiresExpirationPolicy)) {
            throw new IllegalStateException("Tgt expiration policy must be NeverExpiresExpirationPolicy");
        }
        if (startDelay > 0) {
            throw new IllegalStateException("Property 'ticket.registry.cleaner.startdelay' must be less 0 in order disable default ticket cleaner");
        }
    }

    static class TicketExpireEventListener implements IgnitePredicate<CacheEvent> {
        private final static Logger LOG = LoggerFactory.getLogger(TicketExpireEventListener.class);
        @SpringResource(resourceClass = CentralAuthenticationService.class)
        private transient CentralAuthenticationService centralAuthenticationService;

        @Override
        public boolean apply(CacheEvent event) {
            Object oldValueWrapper = event.oldValue();
            if (oldValueWrapper instanceof BinaryObject) {
                Object oldValue = ((BinaryObject) oldValueWrapper).deserialize();
                if (oldValue instanceof ServiceTicket) {
                    ServiceTicket st = (ServiceTicket) oldValue;
                    logger.debug("Cleaning up expired service ticket [{}]", st.getId());
                } else if (oldValue instanceof TicketGrantingTicket) {
                    TicketGrantingTicket tgt = (TicketGrantingTicket) oldValue;
                    centralAuthenticationService.handlePostDestroy(tgt);
                    logger.debug("Cleaning up expired ticket granting ticket [{}]", tgt.getId());
                }
            } else {
                LOG.error("Unknown type " + oldValueWrapper);
            }
            return true;
        }

        public void setCentralAuthenticationService(CentralAuthenticationService centralAuthenticationService) {
            this.centralAuthenticationService = centralAuthenticationService;
        }
    }


    @Override
    public void start() {
        ignite.events().enableLocal(EventType.EVT_CACHE_OBJECT_EXPIRED);
        ignite.events().localListen(serviceTicketEvictEventListener, EventType.EVT_CACHE_OBJECT_EXPIRED);
    }

    @Override
    public void stop() {
        ignite.events().stopLocalListen(serviceTicketEvictEventListener);
        ignite.events().disableLocal(EventType.EVT_CACHE_OBJECT_EXPIRED);

    }
}
