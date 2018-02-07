package pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Created by User on 12/12/2015.
 */

@SpringBootApplication
public class SpringApplicationLauncher {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    public static void main(String[] args) {
        printCurrentDirectory();
        checkEnvironment();

        // Must use this object's .class or this class's annotations may have no effect including @ComponentScan!
        SpringApplication app = new SpringApplication(getThisClass());
        ConfigurableApplicationContext ctx = app.run(args);
    }

    private static void testEnvFilePath() {
        String credentialsPath = System.getenv(Environment.GOOGLE_APPLICATION_CREDENTIALS);
        File credentialsFile = new File(credentialsPath);
        boolean isFile = credentialsFile.isFile();
        log.info("Credentials path: " + credentialsPath + " is file?: " + isFile);
        if (isFile == false) {
            throw new RuntimeException("Cannot locate credentials file from environment variable");
        }
    }

    private static void printCurrentDirectory() {

        File currentDirectory = new File(".");
        String absolutePath = currentDirectory.getAbsolutePath();
        log.info("Application started in path: " + absolutePath);
        printContentsList(currentDirectory);

        File parentDirectory = new File(".");
        String parentPath = parentDirectory.getAbsolutePath();
        log.info("Parent directory has path: " + parentPath);
        printContentsList(parentDirectory);

        File rootDirectory = new File("/");
        printContentsList(rootDirectory);
    }

    private static void printContentsList(File directoryPath) {
        log.info("Path: " + directoryPath.getAbsolutePath() + " Directory exists?: " + directoryPath.exists());
        String[] contents = directoryPath.list();
        if (contents != null) {
            List<String> contentsList = Arrays.asList(contents);
            log.info("Directory contents: " + contentsList);
        }
    }

    private static void checkEnvironment() {
        String credentialEnvName = Environment.GOOGLE_APPLICATION_CREDENTIALS;
        String credentialEnvValue = System.getenv(credentialEnvName);

        if (credentialEnvValue == null || credentialEnvValue.isEmpty()) {
            throw new RuntimeException("Environment variable is not defined:" + credentialEnvName);
        }

        testEnvFilePath(); // Highly specific test for debugging attempt
    }

    public static Class<?> getThisClass() {
        Object newObject = new Object() {}; // Curly braces required for this magic trick to work
        Class<?> enclosingClass = newObject.getClass().getEnclosingClass();
        return enclosingClass;
    }
}
