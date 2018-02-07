package pack.frontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pack.persist.data.User;
import pack.service.task.TaskService;
import pack.service.UserService;
import pack.service.google.gmail.GmailApiService;
import pack.service.google.pubsub.PubSubService;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());


    public static final String PATH__OAUTH_START = "authStart";
    public static final String PATH__OAUTH_CALLBACK = "oauthCallback";
    public static final String PATH__OAUTH_CALLBACK_DONE = "oauthCallbackDone";

    public static final String PATH__LOGIN_STATUS = "loginStatus";
    public static final String PATH__LOGOUT = "logout";

    public static final String TEMPLATE__AUTH_START = "loginStart";
    public static final String TEMPLATE__LOGIN_ERROR = "loginError";
    public static final String TEMPLATE__LOGIN_SUCCESS = "loginSuccess";
    public static final String TEMPLATE__LOGIN_STATUS = "loginStatus";

    public static final String MODEL__URL_GOOGLE_AUTH = "urlGoogleAuthorizationStart";
    public static final String MODEL__URL_LOGOUT = "urlLogout";
    public static final String MODEL__URL_LOGIN_STATUS = "urlLoginStatus";
    public static final String MODEL__LOGIN_ERROR_MSG = "loginErrorMessage";
    public static final String MODEL__GOOGLE_USER_ID_LOGGED_IN = "googleUserId";

    public static final String SESSION__USER_ID_LOGGED_IN = "USER_LOGGED_IN";

    @Autowired private GmailApiService gmailApiService;
    @Autowired private PubSubService pubSubService;
    @Autowired private UserService userService;
    @Autowired private TaskService taskService;


    @RequestMapping(PATH__OAUTH_START)
    public String authStart(Model model) throws IOException {
        addGoogleAuthStartUrl(model);
        return TEMPLATE__AUTH_START;
    }

    @RequestMapping(PATH__OAUTH_CALLBACK)
    public String OAuth2Callback(HttpSession session,
                                 @RequestParam(name = "state") String nonce, Model model,
                                 @RequestParam(name = "code", required = false) String responseCode,
                                 @RequestParam(name = "error", required = false) String errorMessage
    ) throws IOException, SQLException {
        log.info("Redirected with nonce: " + nonce
                + " errorMessage: " + errorMessage
                + " authCode: " + responseCode
        );

        // User refused authorization
        if ("access_denied".equals(errorMessage)) {
            addGoogleAuthStartUrl(model);
            model.addAttribute(MODEL__LOGIN_ERROR_MSG, "Google has reported that the authorization attempt was unsuccessful because you did not authorize this application to gather statistics on your email account.  If you have privacy concerns, by all means use a throw-away account.  If you believe this is in error, please contact the author.");
            model.addAttribute(MODEL__URL_LOGIN_STATUS, PATH__LOGIN_STATUS);
            return TEMPLATE__LOGIN_ERROR;
        }

        // Something else went wrong, explain to the user
        if (errorMessage != null || responseCode == null) {
            addGoogleAuthStartUrl(model);
            model.addAttribute(MODEL__LOGIN_ERROR_MSG, "Google has reported that the authorization attempt was unsuccessful, in some scenario that was not encountered during testing.  Please contact the author to report this.");
            model.addAttribute(MODEL__URL_LOGIN_STATUS, PATH__LOGIN_STATUS);
            return TEMPLATE__LOGIN_ERROR;
        }

        // URL parameters are security-sensitive // Google advises consuming the parameters here and taking them off the URL
        String authorizedUserGoogleId = gmailApiService.receiveNewAccessCode(responseCode);
        userService.addUserWithGoogleId(authorizedUserGoogleId);  // Create user for this ID if one doesn't exist
        pubSubService.watchMailbox(authorizedUserGoogleId);  // Subscribe to mailbox updates for this user
        loginUserWithGoogleId(authorizedUserGoogleId, session); // Store logged-in status to session

        // Updates message counts (but history deltas cannot be updated until the mailbox actually changes)
        User newUser = userService.getUserWithGoogleUserId(authorizedUserGoogleId);
        taskService.setUpdateForUser(newUser.getId());
        taskService.taskStartSoon();

        return "redirect:" + PATH__OAUTH_CALLBACK_DONE; // Redirect user to a message saying the login was successful
    }

    public void addGoogleAuthStartUrl(Model model) throws IOException {
        // for improved security, generate and remember a real nonce
        String googleAuthorizationUrl = gmailApiService.getGoogleAuthorizationUrl("testNonce");
        model.addAttribute(MODEL__URL_GOOGLE_AUTH, googleAuthorizationUrl);
    }

    @RequestMapping(PATH__OAUTH_CALLBACK_DONE)
    public String OAuth2CallbackDone(HttpSession session, Model model) throws SQLException {
        Integer userIdLoggedIn = (Integer) session.getAttribute(AuthController.SESSION__USER_ID_LOGGED_IN); //Integer expected
        if (userIdLoggedIn == null) {
            log.info("Unexpected:  No user is logged in.  User-directed URL navigation?");
            return "redirect:" + PATH__OAUTH_START;
        }

        User userWithId = userService.getUserWithId(userIdLoggedIn);
        model.addAttribute(MODEL__GOOGLE_USER_ID_LOGGED_IN, userWithId.getGoogleUserId());
        model.addAttribute(MODEL__URL_LOGIN_STATUS, PATH__LOGIN_STATUS);
        model.addAttribute(ChartController.ATTRIBUTE__URL_CHART, ChartController.PATH__CHART);

        return TEMPLATE__LOGIN_SUCCESS;
    }

    @RequestMapping(PATH__LOGIN_STATUS)
    public String loginStatus(HttpSession session, Model model) throws SQLException, IOException {
        Integer userIdLoggedIn = (Integer) session.getAttribute(SESSION__USER_ID_LOGGED_IN); //Integer expected

        if (userIdLoggedIn == null) {
            addGoogleAuthStartUrl(model);

        } else {
            User userWithId = userService.getUserWithId(userIdLoggedIn);
            if (userWithId == null) {
                log.warn("Could not find logged-in user in database, userId: " + userIdLoggedIn);
                userService.logAllUsers();
            }

            model.addAttribute(MODEL__GOOGLE_USER_ID_LOGGED_IN, userWithId.getGoogleUserId());
            model.addAttribute(MODEL__URL_LOGOUT, PATH__LOGOUT);
        }

        return TEMPLATE__LOGIN_STATUS;
    }

    @RequestMapping(PATH__LOGOUT)
    public String logout(HttpSession session, Model model) throws SQLException, IOException {
        session.removeAttribute(SESSION__USER_ID_LOGGED_IN);

        addGoogleAuthStartUrl(model);

        return "redirect:" + PATH__LOGIN_STATUS;
    }


    public void loginUserWithGoogleId(String googleUserId, HttpSession session) throws SQLException {
        User userLoggedIn = userService.getUserWithGoogleUserId(googleUserId);
        session.setAttribute(SESSION__USER_ID_LOGGED_IN, userLoggedIn.getId());
        log.info("Logged in user, userId: " + userLoggedIn.getId() + " google account: " + userLoggedIn.getGoogleUserId());
    }
}
