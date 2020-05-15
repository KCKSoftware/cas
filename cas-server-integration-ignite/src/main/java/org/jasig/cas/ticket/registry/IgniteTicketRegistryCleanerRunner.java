package org.jasig.cas.ticket.registry;

import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;

import java.io.Serializable;

/**
 * @author Artem R. Romanenko
 * @version 26/12/2018
 */
class IgniteTicketRegistryCleanerRunner implements Serializable, Service {
    public static final String SERVICE_NAME="igniteTicketRegistryCleanerRunner";

    private static final long serialVersionUID = 4857077869686233334L;

    @SpringResource(resourceClass = IgniteTicketRegistryCleaner.class)
    private transient IgniteTicketRegistryCleaner job;

    @Override
    public void cancel(ServiceContext ctx) {
        job.stop();
    }

    @Override
    public void init(ServiceContext ctx) throws Exception {

    }

    @Override
    public void execute(ServiceContext ctx) throws Exception {
        job.start();
    }

}
