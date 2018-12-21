package org.jasig.cas.ticket.registry;

import org.jasig.cas.ticket.registry.encrypt.AbstractCrypticTicketRegistry;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.Ticket;
import org.jasig.cas.ticket.TicketGrantingTicket;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.lang.IgniteBiPredicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * <a href="https://ignite.apache.org">Ignite</a> based distributed ticket registry.
 * </p>
 *
 * <p>
 * Use distinct caches for ticket granting tickets (TGT) and service tickets (ST) for:
 * <ul>
 * <li>Tuning : use cache level time to live with different values for TGT an ST.</li>
 * <li>Monitoring : follow separately the number of TGT and ST.</li>
 * </ul>
 *
 * @author tduehr
 * @since 4.3.0`
 */
public final class IgniteTicketRegistry extends AbstractCrypticTicketRegistry implements ApplicationListener<ContextRefreshedEvent> {

    @NotNull
    @Value("${ignite.ticketsCache.name:serviceTicketsCache}")
    private String servicesCacheName;

    @NotNull
    @Value("${ignite.ticketsCache.name:ticketGrantingTicketsCache}")
    private String ticketsCacheName;


    private IgniteCache<String, ServiceTicket> serviceTicketsCache;
    private IgniteCache<String, TicketGrantingTicket> ticketGrantingTicketsCache;

    @Value("${tgt.maxTimeToLiveInSeconds:28800}")
    private long ticketGrantingTicketTimeoutInSeconds;

    @Value("${st.timeToKillInSeconds:10}")
    private long serviceTicketTimeoutInSeconds;
    @Autowired
    private Ignite ignite;

    /**
     * @see #setSupportRegistryState(boolean)
     **/
    private boolean supportRegistryState = true;

    /**
     * Instantiates a new Ignite ticket registry.
     */
    public IgniteTicketRegistry() {
        super();
    }

    @Override
    public void addTicket(final Ticket ticketToAdd) {
        final Ticket ticket = encodeTicket(ticketToAdd);
        if (ticket instanceof ServiceTicket) {
            logger.debug("Adding service ticket {} to the cache {}", ticket.getId(), this.serviceTicketsCache.getName());
            this.serviceTicketsCache.put(ticket.getId(), (ServiceTicket) ticket);
        } else if (ticket instanceof TicketGrantingTicket) {
            logger.debug("Adding ticket granting ticket {} to the cache {}", ticket.getId(), this.ticketGrantingTicketsCache.getName());
            this.ticketGrantingTicketsCache.put(ticket.getId(), (TicketGrantingTicket) ticket);
        } else {
            throw new IllegalArgumentException("Invalid ticket type " + ticket);
        }
    }

    @Override
    public boolean deleteSingleTicket(final String ticketId) {
        final Ticket ticket = getTicket(ticketId);

        if (ticket == null) {
            logger.debug("Ticket {} cannot be retrieved from the cache", ticketId);
            return true;
        }

        if (this.ticketGrantingTicketsCache.remove(ticket.getId())) {
            logger.debug("Ticket {} is removed", ticket.getId());
        }
        if (this.serviceTicketsCache.remove(ticket.getId())) {
            logger.debug("Ticket {} is removed", ticket.getId());
        }

        return true;
    }

    @Override
    public Ticket getTicket(final String ticketIdToGet) {
        final String ticketId = encodeTicketId(ticketIdToGet);
        if (ticketId == null) {
            return null;
        }

        Ticket ticket = this.serviceTicketsCache.get(ticketId);
        if (ticket == null) {
            ticket = this.ticketGrantingTicketsCache.get(ticketId);
        }
        if (ticket == null) {
            logger.debug("No ticket by id [{}] is found in the registry", ticketId);
            return null;
        }

        final Ticket proxiedTicket = decodeTicket(ticket);
        ticket = getProxiedTicketInstance(proxiedTicket);
        return ticket;
    }

    @Override
    public Collection<Ticket> getTickets() {
        final Collection<Cache.Entry<String, ServiceTicket>> serviceTickets;
        final Collection<Cache.Entry<String, TicketGrantingTicket>> tgtTicketsTickets;


        final IgniteBiPredicate<String, TicketGrantingTicket> filterTgt = new IgniteBiPredicate<String, TicketGrantingTicket>() {
            @Override
            public boolean apply(final String s, final TicketGrantingTicket ticketGrantingTicket) {
                return true;
            }
        };
        final QueryCursor<Cache.Entry<String, TicketGrantingTicket>> cursorTgt =
                ticketGrantingTicketsCache.query(new ScanQuery<>(filterTgt));
        tgtTicketsTickets = cursorTgt.getAll();

        IgniteBiPredicate<String, ServiceTicket> filterSt = new IgniteBiPredicate<String, ServiceTicket>() {
            @Override
            public boolean apply(final String key, final ServiceTicket t) {
                return !t.isExpired();
            }
        };
        final QueryCursor<Cache.Entry<String, ServiceTicket>> cursorSt = serviceTicketsCache.query(new ScanQuery<>(filterSt));
        serviceTickets = cursorSt.getAll();

        final Collection<Ticket> allTickets = new HashSet<>(serviceTickets.size() + tgtTicketsTickets.size());

        for (final Cache.Entry<String, ServiceTicket> entry : serviceTickets) {
            final Ticket proxiedTicket = getProxiedTicketInstance(entry.getValue());
            allTickets.add(proxiedTicket);
        }

        for (final Cache.Entry<String, TicketGrantingTicket> entry : tgtTicketsTickets) {
            final Ticket proxiedTicket = getProxiedTicketInstance(entry.getValue());
            allTickets.add(proxiedTicket);
        }

        return decodeTickets(allTickets);
    }

    public void setServiceTicketsCache(final IgniteCache<String, ServiceTicket> serviceTicketsCache) {
        this.serviceTicketsCache = serviceTicketsCache;
    }

    public void setTicketGrantingTicketsCache(final IgniteCache<String, TicketGrantingTicket> ticketGrantingTicketsCache) {
        this.ticketGrantingTicketsCache = ticketGrantingTicketsCache;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this).append("ticketGrantingTicketsCache", this.ticketGrantingTicketsCache)
                .append("serviceTicketsCache", this.serviceTicketsCache).toString();
    }

    @Override
    protected void updateTicket(final Ticket ticket) {
        addTicket(ticket);
    }

    @Override
    protected boolean needsCallback() {
        return false;
    }

    /**
     * Flag to indicate whether this registry instance should participate in reporting its state with
     * default value set to {@code true}.
     *
     * <p>Therefore, the flag provides a level of flexibility such that depending on the cache and environment
     * settings, reporting statistics
     * can be set to false and disabled.</p>
     *
     * @param supportRegistryState true, if the registry is to support registry state
     * @see #sessionCount()
     * @see #serviceTicketCount()
     */
    public void setSupportRegistryState(final boolean supportRegistryState) {
        this.supportRegistryState = supportRegistryState;
    }


    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        logger.info("Setting up Ignite Ticket Registry...");
        if (logger.isDebugEnabled()) {
            logger.debug("Ticket-granting ticket timeout: [{}s]", this.ticketGrantingTicketTimeoutInSeconds);
            logger.debug("Service ticket timeout: [{}s]", this.serviceTicketTimeoutInSeconds);
        }
        serviceTicketsCache = ignite.cache(servicesCacheName);
        ticketGrantingTicketsCache = ignite.cache(ticketsCacheName);
        //TODO (by Artyom R. Romanenko) kill?
        //ticketGrantingTicketsCache.getConfiguration(CacheConfiguration.class).setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf
        // (new Duration(TimeUnit.SECONDS, ticketGrantingTicketTimeoutInSeconds)));
    }

    @Override
    public int sessionCount() {
        return BooleanUtils.toInteger(this.supportRegistryState, this.ticketGrantingTicketsCache
                .size(CachePeekMode.ALL), super.sessionCount());
    }

    @Override
    public int serviceTicketCount() {
        return BooleanUtils.toInteger(this.supportRegistryState, this.serviceTicketsCache
                .size(CachePeekMode.ALL), super.serviceTicketCount());
    }

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        ignite.active();
    }

    private static class ExternalKeyPredicate implements IgniteBiPredicate<String, TicketGrantingTicket>, Serializable {
        private String externalId;

        private ExternalKeyPredicate(String externalId) {
            this.externalId = externalId;
        }

        @Override
        public boolean apply(String s, TicketGrantingTicket ticketGrantingTicket) {
            return externalId.equals(ticketGrantingTicket.getExternalId());
        }

    }

    @Override
    public TicketGrantingTicket getTgtByExternalId(final String externalId) {
        if (externalId == null) {
            throw new NullPointerException("externalId must be not null");
        }
        try (QueryCursor<Cache.Entry<String, TicketGrantingTicket>> queryCursor = ticketGrantingTicketsCache.query(new ScanQuery<>(new ExternalKeyPredicate(externalId)))) {
            List<Cache.Entry<String, TicketGrantingTicket>> all = queryCursor.getAll();
            switch (all.size()) {
                case 0:
                    return null;
                case 1:
                    return all.get(0).getValue();
                default:
                    throw new IllegalStateException("Multiple tgt for externalId '" + externalId + "'");
            }
        }


    }
}
