package pack.service.google.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pack.ApplicationConfiguration;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Component
public class GmailApiService {

    private static final Logger log = LoggerFactory.getLogger((new Object() {}).getClass().getEnclosingClass());
    public static final String MESSAGE_FORMAT__METADATA = "metadata"; // Not requesting full message data e.g. body  // https://developers.google.com/gmail/api/v1/reference/users/messages/get

    @Autowired ApplicationConfiguration applicationConfiguration;

    private static final String APPLICATION_NAME = "Gmail API Java Quickstart";

    // Google Authentication flow configuration
    // Google API library should auto-refresh the token if 'offline' access is requested... but 'force' option may be necessary(?)
    // Credential is considered to be expired if it has this much time left
    public static final String APPROVAL_PROMPT_FORCE = "force";
    public static final String TOKEN_ACCESS_TYPE_OFFLINE = "offline";

    // * Directory to store user credentials for this application.
    public static String PATH_CREDENTIALS = "./credentials";
    private static final java.io.File DATA_STORE_DIR = new java.io.File(PATH_CREDENTIALS);


    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static FileDataStoreFactory DATA_STORE_FACTORY;

    private static HttpTransport HTTP_TRANSPORT;

    // Permissions requested by app - please refer to https://developers.google.com/gmail/api/auth/scopes
    // Notes on other permission scopes not needed:
    // GmailScopes.GMAIL_LABELS // Label ("folder") metadata such as number of messages // This is granted implicitly by requesting GmailScopes.GMAIL_METADATA
    // GmailScopes.GMAIL_READONLY // Full read-only access
    // GmailScopes.MAIL_GOOGLE_COM // Full read/write access
    private static final List<String> SCOPES =
            Arrays.asList(
                    GmailScopes.GMAIL_METADATA // Message metadata such as time received and 'from' header
            );


    private static GoogleClientSecrets clientSecrets;


    private static GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow; // Appears to contain nothing user-specific, made static


    @PostConstruct
    private void init() {

        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);

            // Load client secrets.
            InputStream in = GmailApiService.class.getResourceAsStream(applicationConfiguration.googleClientSecretPath);
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            googleAuthorizationCodeFlow = buildGoogleAuthorizationCodeFlow();

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private Gmail buildGmailService(Credential credentialForUser) {
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentialForUser)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private GoogleTokenResponse exchangeCodeForToken(String authCode) throws IOException {
        // Google's well-hidden notes on Redirect Url:
        // Specify the same redirect URI that you use with your web app. If you don't have a web version of your app, you can specify an empty string.

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                clientSecrets.getDetails().getClientId(),
                clientSecrets.getDetails().getClientSecret(),
                authCode,
                applicationConfiguration.googleOauthRedirectUrl)
                .execute();
        return tokenResponse;
    }

    private static GoogleAuthorizationCodeFlow buildGoogleAuthorizationCodeFlow() throws IOException {
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType(TOKEN_ACCESS_TYPE_OFFLINE)
                .setApprovalPrompt(APPROVAL_PROMPT_FORCE) // Might be important for token (auto?) refresh
                .build();
        return flow;
    }


    public Credential authorizeConsoleOob(String googleUserId) throws IOException {
        if (isCredentialValid(googleUserId)) {
            return googleAuthorizationCodeFlow.loadCredential(googleUserId);
        }

        log.info("Obtaining new credentials (OOB)");

        LocalServerReceiver localServerReceiver = new LocalServerReceiver();
        AuthorizationCodeInstalledApp authorizationCodeInstalledApp = new AuthorizationCodeInstalledApp(googleAuthorizationCodeFlow, localServerReceiver);
        Credential credential = authorizationCodeInstalledApp.authorize(googleUserId);

        log.info("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }


    public String getGoogleAuthorizationUrl(String nonce) throws IOException {
        // Build flow and use Google libraries to construct the authorization URL

        String authorizationUrl = googleAuthorizationCodeFlow.newAuthorizationUrl()
                .setRedirectUri(applicationConfiguration.googleOauthRedirectUrl)
                .setState(nonce) // unique identifier known only to the requestor, guards against impersonation and/or hijacking
                .build();

        log.info("Built google authorization URL: " + authorizationUrl);
        log.info("Should add a session-based nonce for security");
        return authorizationUrl;
    }

    public boolean isCredentialValid(String googleUserId) throws IOException {
        Credential credentialForUser = googleAuthorizationCodeFlow.loadCredential(googleUserId);
        if (credentialForUser == null) {
            return false;
        }

        // Google API libraries 'should' automatically refresh the token as needed
        // if (credentialForUser.getExpiresInSeconds() <= TIME_SECONDS__CONSIDER_CREDENTIAL_EXPIRED) {
        //     log.info("Credential considered invalid: is past or nearing expiration for user: " + googleUserId);
        //     return false;
        // }

        return true;
    }


    //TODO which of these comments apply?
    // * Build and return an authorized Gmail client pack.service.
    // Build flow and trigger user authorization request.
    public Gmail getGmailService(String googleUserId) throws IOException {

        if (isCredentialValid(googleUserId) == false) {
            throw new RuntimeException("Called without valid credential");
        }

        Credential credentialForUser = googleAuthorizationCodeFlow.loadCredential(googleUserId);
        String refreshToken = credentialForUser.getRefreshToken();
        if (refreshToken == null) {
            log.info("Warning - credential lacks refresh token - should examine token when it is first obtained");
        }
        Gmail service = buildGmailService(credentialForUser);
        return service;
    }

    public Message getMessageDetailsById(String googleUserId, String messageId) throws IOException {
        // Build a new authorized API client pack.service.
        Gmail service = getGmailService(googleUserId);

        final Message message = service.users().messages().get("me", messageId)
                .setFormat(MESSAGE_FORMAT__METADATA)
                .execute();
        final String snippet = message.getSnippet();
        log.debug("Got details for messageId:" + messageId + " snippet: " + snippet);
        return message;
    }

    public List<History> getHistoryFrom(String googleUserId, String startingHistoryId) throws IOException {
        Gmail service = getGmailService(googleUserId);
        ListHistoryResponse response = service.users().history().list("me").setStartHistoryId(new BigInteger(startingHistoryId)).execute();
        List<History> history = response.getHistory();
        return history;
    }

    public Label getLabelInfo(String googleUserId, String labelId) throws IOException {
        Gmail service = getGmailService(googleUserId);

        Label label = service.users().labels().get("me", labelId).execute();
        log.info("Got label:" + label.toString());
        return label;
    }

    public List<Message> getAllMessagesForLabel(String googleUserId, String labelId) throws IOException, InterruptedException {
        StatefulMessagePageFetcher pageFetcher = getMessagePageFetcherForLabel(googleUserId, labelId, null);

        List<Message> nextPageMessages;
        List<Message> messagesFromAllPages = new ArrayList<>();
        HashMap<String, Message> messageIdToMessageMap = new HashMap<>(); // For accounting purposes only, at the moment
        while (true) {
            nextPageMessages = pageFetcher.getNextPage();
            if (nextPageMessages == null) {
                break;
            }

            for (Message nextMessage : nextPageMessages) {
                if (nextMessage.getHistoryId() != null) {
                    log.info("Found history ID: " + nextMessage.getHistoryId());
                }
                if (messageIdToMessageMap.containsKey(nextMessage.getId())) {
                    log.info("Duplicate message id within page: " + nextMessage.getId());
                } else {
                    messageIdToMessageMap.put(nextMessage.getId(), nextMessage);
                }
            }

            messagesFromAllPages.addAll(nextPageMessages);
        }

        int totalMessages = messagesFromAllPages.size();
        int uniqueMessages = messageIdToMessageMap.size();

        String statsOutput = "Messages found: " + totalMessages + ", Unique messages: " + uniqueMessages;
        if (totalMessages != uniqueMessages) {
            statsOutput += " - COUNTS NOT EQUAL";
        }
        log.info(statsOutput);
        return messagesFromAllPages;
    }

    public StatefulMessagePageFetcher getMessagePageFetcherForLabel(String googleUserId, String labelId, Integer softMaximum) throws IOException {
        Gmail gmailService = getGmailService(googleUserId);
        StatefulMessagePageFetcher pageFetcher = new StatefulMessagePageFetcher(gmailService, labelId, softMaximum);
        return pageFetcher;
    }

    // Get history events since provided History Id
    public List<History> getMessageHistoryFrom(String googleUserId, Long startHistoryId) throws IOException {
        // Build a new authorized API client pack.service.
        Gmail service = getGmailService(googleUserId);


        BigInteger startHistoryIdBigInteger = BigInteger.valueOf(Long.valueOf(startHistoryId));
        final Gmail.Users.History.List request = service.users().history().list("me")
                .setStartHistoryId(startHistoryIdBigInteger);

        List<History> histories = new ArrayList<History>();
        try {
            ListHistoryResponse response = request.execute();

            while (response.getHistory() != null) {
                histories.addAll(response.getHistory());
                if (response.getNextPageToken() != null) {
                    String pageToken = response.getNextPageToken();
                    log.info("Getting next page, token: " + pageToken);
                    response = service.users().history().list("me").setPageToken(pageToken)
                            .setStartHistoryId(startHistoryIdBigInteger).execute();
                } else {
                    log.info("Response has no next page");
                    break;
                }
            }

        } catch (GoogleJsonResponseException e) {
            log.info("---");
            log.info(e.getClass().getCanonicalName() + " occurred when accessing history info for history id: " + startHistoryIdBigInteger + ", details: " + e.getDetails().toString());
        }

        log.info("Done collecting histories - got " + histories.size() + " events following history id: " + startHistoryId);
        return histories;
    }

    public boolean isHistoryIdValid(String googleUserId, String historyId) throws IOException {
        // Build a new authorized API client pack.service.
        Gmail service = getGmailService(googleUserId);

        BigInteger startHistoryIdBigInteger = BigInteger.valueOf(Long.valueOf(historyId));
        final Gmail.Users.History.List request = service.users().history().list("me")
                .setStartHistoryId(startHistoryIdBigInteger);
        try {
            ListHistoryResponse response = request.execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 404) {
                return false;
            }

            throw e; // Not sure what is wrong, so rethrow
        }
        return true;
    }

    public List<Label> demoGetAllLabels(String googleUserId) throws IOException {
        // Build a new authorized API client pack.service.
        Gmail service = getGmailService(googleUserId);

        // Print the labels in the user's account.
        String userId = "me";
        ListLabelsResponse listResponse =
                service.users().labels().list(userId).execute();
        List<Label> labels = listResponse.getLabels();

        return labels;
    }

    // Use auth code obtained from OAuth to create and store a new credential for this user
    // Return the google user id of the user just authorized
    public String receiveNewAccessCode(String authCode) throws IOException {
        GoogleTokenResponse tokenResponse = exchangeCodeForToken(authCode); // Exchange auth code (From Oauth) for access token
        GoogleCredential credentialForNewUser = new GoogleCredential().setAccessToken(tokenResponse.getAccessToken());

        String refreshToken = credentialForNewUser.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            log.info(" ----- Warning: did not receive a valid refresh token!");
        }

        // Use access token to determine the google user id (email address) of the user that was just authorized
        Gmail service = buildGmailService(credentialForNewUser);
        Profile newUserProfile = service.users().getProfile("me").execute();
        String googleUserId = newUserProfile.getEmailAddress();
        log.info("Got profile for user: " + googleUserId);

        if (googleUserId == null || googleUserId.isEmpty()) {
            throw new RuntimeException("Could not obtain a meaningful google User Id, cannot save credential");
        }
        googleAuthorizationCodeFlow.createAndStoreCredential(tokenResponse, googleUserId);
        return googleUserId;
    }
}
