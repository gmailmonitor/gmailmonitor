package pack;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class LoggingInitializer implements InitializingBean {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger((new Object() {
    }).getClass().getEnclosingClass());


    public static final String LOGGER_ROOT = "ROOT";
    public static final String APPENDER_CONSOLE = "CONSOLE";
    public static final String FILE_NAME = "logfile.log";

    @Autowired
    private ApplicationConfiguration applicationConfiguration;
    private String logDirectory;

    List<Filter> filters = new ArrayList<>();
    List<Encoder> encoders = new ArrayList<>();
    List<RollingPolicy> rollingPolicies = new ArrayList<>();
    List<Appender> appenders = new ArrayList<>();

    // @PostConstruct
    // public void postConstruct() {
    //     log.info("LoggingInitializer PostConstructed");
    // }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("After Properties Set");
        initializeLogging();
    }


    public void initializeLogging() {
        configureLogDirectory();

        //Assuming use of default logback (ch.qos.logback.classic.LoggerContext)
        LoggerContext logCtx = (LoggerContext) LoggerFactory.getILoggerFactory();
        StatusPrinter.print(logCtx); // Good for debugging
        Logger rootLogger = logCtx.getLogger(LOGGER_ROOT);
        rootLogger.setLevel(Level.DEBUG);

        // Logger customLogger = logCtx.getLogger("com.google.api.client.http.HttpTransport");
        // customLogger.setLevel(Level.WARN);


        Appender<ILoggingEvent> consoleAppender = rootLogger.getAppender(APPENDER_CONSOLE); // Gets started automatically...
        configureConsoleFilters(consoleAppender);

        PatternLayoutEncoder logEncoder = configureEncoder(logCtx);
        RollingFileAppender logFileAppender = configureAppender(logCtx, logEncoder);
        rootLogger.addAppender(logFileAppender);
        configureFileFilters(logFileAppender);

        TimeBasedRollingPolicy logFilePolicy = configurePolicy(logCtx, logFileAppender);
        logFileAppender.setRollingPolicy(logFilePolicy); // Reverse-direction dependency!!!

        startLogComponents();

        log.info("Logger directory: " + logDirectory + "configuration info follows:");
        StatusPrinter.print(logCtx); // Good for debugging
    }

    private boolean isInitialized() {
        if (applicationConfiguration.logDirectory == null) {
            return true;
        } else {
            return false;
        }
    }

    private void configureLogDirectory() {
        logDirectory = applicationConfiguration.logDirectory;
        if (logDirectory.endsWith(File.separator) == false) {
            logDirectory = logDirectory + File.separator;
        }
    }

    private RollingFileAppender configureAppender(LoggerContext logCtx, PatternLayoutEncoder logEncoder) {
        RollingFileAppender logFileAppender = new RollingFileAppender();
        logFileAppender.setContext(logCtx);
        logFileAppender.setName("LogToFile");
        logFileAppender.setFile(logDirectory + FILE_NAME);
        logFileAppender.setEncoder(logEncoder);

        appenders.add(logFileAppender);
        return logFileAppender;
    }


    private void configureConsoleFilters(Appender<ILoggingEvent> consoleAppender) {
        ThresholdFilter filterInfo = new ThresholdFilter();
        filterInfo.setLevel(Level.INFO.toString());
        consoleAppender.addFilter(filterInfo);
        filters.add(filterInfo);

        ClassAndLevelFilter filterHttpTransport = new ClassAndLevelFilter(Level.WARN, "com.google.api.client.http.HttpTransport");
        consoleAppender.addFilter(filterHttpTransport);
        filters.add(filterHttpTransport);
    }

    private void configureFileFilters(RollingFileAppender logFileAppender) {
        ClassAndLevelFilter filterHttpTransport = new ClassAndLevelFilter(Level.DEBUG, "com.google.api.client.http.HttpTransport");
        logFileAppender.addFilter(filterHttpTransport);
        filters.add(filterHttpTransport);
    }


    private PatternLayoutEncoder configureEncoder(LoggerContext logCtx) {
        PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext(logCtx);
        logEncoder.setPattern("%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level - %msg%n");

        encoders.add(logEncoder);
        return logEncoder;
    }

    private TimeBasedRollingPolicy configurePolicy(LoggerContext logCtx, RollingFileAppender logFileAppender) {
        TimeBasedRollingPolicy logFilePolicy = new TimeBasedRollingPolicy();
        logFilePolicy.setContext(logCtx);
        logFilePolicy.setParent(logFileAppender);

        logFilePolicy.setFileNamePattern(logDirectory + "logfile-%d{yyyy-MM-dd_HH}.log");
        logFilePolicy.setMaxHistory(7);

        rollingPolicies.add(logFilePolicy);
        return logFilePolicy;
    }

    private void startLogComponents() {
        for (Filter nextFilter : filters) {
            nextFilter.start();
        }
        for (Encoder nextEncoder : encoders) {
            nextEncoder.start();
        }
        for (RollingPolicy nextPolicy : rollingPolicies) {
            nextPolicy.start();
        }
        for (Appender nextAppender : appenders) {
            nextAppender.start();
        }
    }

    public class ClassAndLevelFilter extends Filter<ILoggingEvent> {

        private Level levelFilter;
        private String classNameFilter; // fully qualified class name (with package name)

        private ClassAndLevelFilter() {}

        public ClassAndLevelFilter(Level level, String className) {
            this.levelFilter = level;
            this.classNameFilter = className;
        }

        @Override
        public FilterReply decide(ILoggingEvent event) {

            Level levelEvent = event.getLevel();
            String classNameEvent = event.getLoggerName();// ex: pack.LoggingInitializer

            if (classNameFilter.equals(classNameEvent) == false) {
                return FilterReply.NEUTRAL;
            }

            if (levelEvent.levelInt < levelFilter.levelInt) {
                return FilterReply.DENY;
            } else {
                return FilterReply.ACCEPT;
            }
        }
    }


}
