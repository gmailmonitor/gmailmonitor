package pack.frontend;

import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.HistoryEvent;
import pack.persist.data.User;
import pack.service.UserService;
import pack.service.google.gmail.GmailDataService;
import pack.service.task.TaskService;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger((new Object() {
    }).getClass().getEnclosingClass());

    public static final String PATH__STATS = "mailStats";
    public static final String TEMPLATE__STATS = "mailStats";

    public static final String ATTRIBUTE__SENDER_STATS = "senderStats";
    public static final String ATTRIBUTE__CHART_TIME_WINDOW = "statsTime";
    public static final String ATTRIBUTE__LAST_HISTORY_UPDATE = "historyUpdate";
    public static final String ATTRIBUTE__LAST_LABEL_UPDATE = "labelUpdate";
    public static final String ATTRIBUTE__TASK_DELAY = "taskDelay";

    @Autowired
    private GmailDataService gmailDataService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private UserService userService;

    // Chart main page
    @RequestMapping(PATH__STATS)
    public String stats(@RequestParam(name = "statsTime", required = false, defaultValue = "") String statsTimeUrlParameter,
                        HttpSession session,
                        Model model) throws SQLException {

        Integer userIdLoggedIn = getUserIdLoggedIn(session); //Integer expected
        if (userIdLoggedIn == null) {
            log.info("User is not logged in, redirecting user to login page");
            return "redirect:" + AuthController.PATH__OAUTH_START;
        }
        User userLoggedIn = userService.getUserWithId(userIdLoggedIn);

        Long intervalStart = null;
        if (statsTimeUrlParameter != null && !statsTimeUrlParameter.isEmpty()) {
            long statsTime = Long.parseLong(statsTimeUrlParameter);
            intervalStart = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(statsTime);
        }

        List<Pair> senderStats = gmailDataService.getSenderStats(userLoggedIn, intervalStart);
        model.addAttribute(ATTRIBUTE__SENDER_STATS, senderStats);


        log.info("Request for stats with time window: " + statsTimeUrlParameter);

        // Code could be cleaned up
        model.addAttribute(ATTRIBUTE__CHART_TIME_WINDOW, statsTimeUrlParameter); // Used by template to set javascript variable for AJAX request
        model.addAttribute(AuthController.MODEL__GOOGLE_USER_ID_LOGGED_IN, userLoggedIn.getGoogleUserId());

        HistoryEvent lastHistoryUpdate = gmailDataService.getLastHistoryUpdate(userIdLoggedIn);
        if (lastHistoryUpdate == null) {
            model.addAttribute(ATTRIBUTE__LAST_HISTORY_UPDATE, "none yet - please refresh in 10-20s");
        } else {
            long dateDifference = System.currentTimeMillis() - lastHistoryUpdate.getDateOccurred();
            String historyInterval = formatInterval(dateDifference) + " ago";
            model.addAttribute(ATTRIBUTE__LAST_HISTORY_UPDATE, historyInterval);
        }

        GmailLabelUpdate lastLabelUpdate = gmailDataService.getLastLabelUpdate(userIdLoggedIn);
        if (lastLabelUpdate == null) {
            model.addAttribute(ATTRIBUTE__LAST_LABEL_UPDATE, "none yet - please refresh in 10-20s");
        } else {
            long dateDifference = System.currentTimeMillis() - lastLabelUpdate.getUpdateTimeMillis();
            String labelInterval = formatInterval(dateDifference) + " ago";
            model.addAttribute(ATTRIBUTE__LAST_LABEL_UPDATE, labelInterval);
        }

        ScheduledFuture scheduledFuture = taskService.getScheduledFuture();
        if (scheduledFuture == null) {
            log.info("Scheduled Task:  None scheduled");
        } else {

            log.info("Scheduled Task:  cancelled?: " + scheduledFuture.isCancelled() + " done?: " + scheduledFuture.isDone());
            long delaySeconds = scheduledFuture.getDelay(TimeUnit.SECONDS);
            String delayMinutes = String.format("%.2f", (double) delaySeconds / 60);
            model.addAttribute(ATTRIBUTE__TASK_DELAY, delayMinutes + " min");

        }

        return TEMPLATE__STATS;
    }

    private Integer getUserIdLoggedIn(HttpSession session) {
        log.info("Session ID: " + session.getId());
        return (Integer) session.getAttribute(AuthController.SESSION__USER_ID_LOGGED_IN);
    }

    private static String formatInterval(final long input) {
        final long day = TimeUnit.MILLISECONDS.toDays(input);
        final long hr = TimeUnit.MILLISECONDS.toHours(input - TimeUnit.DAYS.toMillis(day));
        final long min = TimeUnit.MILLISECONDS.toMinutes(input - TimeUnit.DAYS.toMillis(day) - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(input - TimeUnit.DAYS.toMillis(day) - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
        // final long ms = TimeUnit.MILLISECONDS.toMillis(input - TimeUnit.DAYS.toMillis(day) - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
        String formatted = String.format("%02dd %02dhr %02dm %02ds", day, hr, min, sec);
        return formatted;
    }

}