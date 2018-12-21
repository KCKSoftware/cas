package org.jasig.cas.ticket.registry;

import com.google.common.io.Files;
import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteSpringBean;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.CommunicationSpi;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;


/**
 * @author Artem R. Romanenko
 * @version 20/12/2018
 * @since 4.28.KCK-SNAPSHOT
 */
@Configuration
public class CasIgniteConfiguration {

    /**
     * The Slf4j logger instance.
     */
    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private DiscoverySpi ingiteDiscoverySpi;
    @Autowired
    private CommunicationSpi igniteCommunicatorSpi;
    @Value("#{@igniteCacheConfiguration}")
    private List<CacheConfiguration> igniteCacheConfiguration;


    @Value("${ignite.storagePath:}")
    private String igniteWorkDirectory;


    @Value("${ignite.keyStoreType:}")
    private String keyStoreType;

    @Value("${ignite.keyStoreFilePath:}")
    private String keyStoreFilePath;

    @Value("${ignite.keyStorePassword:}")
    private String keyStorePassword;

    @Value("${ignite.trustStoreType:}")
    private String trustStoreType;

    @Value("${ignite.protocol:}")
    private String protocol;

    @Value("${ignite.keyAlgorithm:}")
    private String keyAlgorithm;

    @Value("${ignite.trustStoreFilePath:}")
    private String trustStoreFilePath;

    @Value("${ignite.trustStorePassword:}")
    private String trustStorePassword;

    @Bean
    Ignite ignite() throws Exception {
        final IgniteSpringBean igniteSpringBean = new IgniteSpringBean();
        final IgniteConfiguration igniteConfiguration = igniteConfiguration();
        igniteSpringBean.setConfiguration(igniteConfiguration);
        if (logger.isDebugEnabled()) {
            logger.debug("igniteConfiguration.cacheConfiguration={}", igniteConfiguration.getCacheConfiguration());
            logger.debug("igniteConfiguration.getDiscoverySpi={}", igniteConfiguration.getDiscoverySpi());
            logger.debug("igniteConfiguration.getSslContextFactory={}", igniteConfiguration.getSslContextFactory());
        }
        return igniteSpringBean;
    }

    private IgniteConfiguration igniteConfiguration() throws Exception {
        final IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
        igniteConfiguration.setDiscoverySpi(ingiteDiscoverySpi);
        igniteConfiguration.setCommunicationSpi(igniteCommunicatorSpi);
        final CacheConfiguration[] tmp
                = igniteCacheConfiguration.toArray(new CacheConfiguration[igniteCacheConfiguration.size()]);
        igniteConfiguration.setCacheConfiguration(tmp);

        final String realWorkDirectory;
        if (StringUtils.isNotBlank(igniteWorkDirectory)) {
            realWorkDirectory = igniteWorkDirectory;
            final DataStorageConfiguration igniteDataStorageConfiguration = new DataStorageConfiguration();
            final DataRegionConfiguration defaultDataRegionConfiguration = new DataRegionConfiguration();
            defaultDataRegionConfiguration.setPersistenceEnabled(true);
            igniteDataStorageConfiguration.setDefaultDataRegionConfiguration(defaultDataRegionConfiguration);
            igniteConfiguration.setDataStorageConfiguration(igniteDataStorageConfiguration);
        } else {
            realWorkDirectory = Files.createTempDir().getCanonicalPath();
        }

        igniteConfiguration.setWorkDirectory(realWorkDirectory);

        configureSecureTransport(igniteConfiguration);
        return igniteConfiguration;
    }


    private void configureSecureTransport(final IgniteConfiguration igniteConfiguration) {
        if (StringUtils.isNotBlank(this.keyStoreFilePath) && StringUtils.isNotBlank(this.keyStorePassword)
                && StringUtils.isNotBlank(this.trustStoreFilePath) && StringUtils.isNotBlank(this.trustStorePassword)) {
            final SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStoreFilePath(this.keyStoreFilePath);
            sslContextFactory.setKeyStorePassword(this.keyStorePassword.toCharArray());
            if ("NULL".equals(this.trustStoreFilePath) && "NULL".equals(this.trustStorePassword)) {
                sslContextFactory.setTrustManagers(SslContextFactory.getDisabledTrustManager());
            } else {
                sslContextFactory.setTrustStoreFilePath(this.trustStoreFilePath);
                sslContextFactory.setTrustStorePassword(this.trustStorePassword.toCharArray());
            }

            if (StringUtils.isNotBlank(this.keyAlgorithm)) {
                sslContextFactory.setKeyAlgorithm(this.keyAlgorithm);
            }
            if (StringUtils.isNotBlank(this.protocol)) {
                sslContextFactory.setProtocol(this.protocol);
            }
            if (StringUtils.isNotBlank(this.trustStoreType)) {
                sslContextFactory.setTrustStoreType(this.trustStoreType);
            }
            if (StringUtils.isNotBlank(this.keyStoreType)) {
                sslContextFactory.setKeyStoreType(this.keyStoreType);
            }
            igniteConfiguration.setSslContextFactory(sslContextFactory);
        }
    }

}
