package pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import javax.annotation.PostConstruct;
import javax.swing.*;


@Configuration

@PropertySource("classpath:" + Environment.APPLICATION_PROPERTIES)
public class ApplicationConfiguration {

    // Cannot require Spring to initialize loggingInitializer before this bean, as it depends on this one
    // @Autowired LoggingInitializer loggingInitializer; // Placed in multiple @Configuration beans in attempt to obtain an early initialization

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());
    private static final String SYSTEM_PROPERTY_NAME__GOOGLE_CLOUD_PROJECT_ID = "GOOGLE_CLOUD_PROJECT";

    // In a production application, these values would be private and non-modifiable
    @Value("${google.pubsub.topic.id}")
    public String pubsubTopicId;

    @Value("${google.pubsub.subscription.id}")
    public String pubsubSubscriptionId;

    @Value("${database.url}")
    public String databaseUrl;

    // This must be the project identifier, not simple project name
    @Value("${google.cloud.project.name}")
    public String googleCloudProjectId;

    @Value("${google.client.secret.path}")
    public String googleClientSecretPath;

    @Value("${google.oauth.redirect.url}")
    public String googleOauthRedirectUrl;

    @Value("${log.directory}")
    public String logDirectory;



    @PostConstruct
    private void postConstruct() {
        log.info("Setting system property: " + SYSTEM_PROPERTY_NAME__GOOGLE_CLOUD_PROJECT_ID + " value: " + googleCloudProjectId);
        System.setProperty(SYSTEM_PROPERTY_NAME__GOOGLE_CLOUD_PROJECT_ID, googleCloudProjectId);
    }

    @Bean
    static PropertySourcesPlaceholderConfigurer propertyPlaceHolderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

}
