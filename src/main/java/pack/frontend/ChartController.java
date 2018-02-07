package pack.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pack.frontend.databind.DataPointMessageCount;
import pack.frontend.databind.DataSeriesLabelChangeSegments;
import pack.frontend.databind.ResultsContainerMessageData;
import pack.persist.data.GmailLabelUpdate;
import pack.persist.data.HistoryEvent;
import pack.persist.data.User;
import pack.persist.data.dummy.GmailLabelUpdateDummy;
import pack.service.task.TaskService;
import pack.service.UserService;
import pack.service.google.gmail.GmailDataService;
import pack.service.google.gmail.GmailService;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class ChartController {

    private static final Logger log = LoggerFactory.getLogger((new Object(){}).getClass().getEnclosingClass());

    public static final String PATH__CHART = "chart";
    public static final String TEMPLATE__CHART = "chart";

    public static final String ATTRIBUTE__URL_CHART = "urlChart";
    public static final String ATTRIBUTE__CHART_TIME_WINDOW = "chartTime";
    public static final String ATTRIBUTE__LAST_HISTORY_UPDATE = "historyUpdate";
    public static final String ATTRIBUTE__LAST_LABEL_UPDATE = "labelUpdate";
    public static final String ATTRIBUTE__TASK_DELAY = "taskDelay";

    @Autowired private GmailService gmailService;
    @Autowired private GmailDataService gmailDataService;
    @Autowired private TaskService taskService;
    @Autowired private UserService userService;

    // Chart main page
    @RequestMapping(PATH__CHART)
    public String chart(@RequestParam(required = false, defaultValue = "") String chartTime,
                        HttpSession session,
                        Model model) throws SQLException {


        // log.error("Calling System.exit()"); // Used in experiments to save sessions through restart
        // System.exit(0); // Unclear why this persists session information to disk

        Integer userIdLoggedIn = getUserIdLoggedIn(session); //Integer expected
        if (userIdLoggedIn == null) {
            log.info("User is not logged in, redirecting user to login page");
            return "redirect:" + AuthController.PATH__OAUTH_START;
        }


        log.info("Request for chart with time window: " + chartTime);
        model.addAttribute(ATTRIBUTE__CHART_TIME_WINDOW, chartTime); // Used by template to set javascript variable for AJAX request

        User userWithId = userService.getUserWithId(userIdLoggedIn);
        model.addAttribute(AuthController.MODEL__GOOGLE_USER_ID_LOGGED_IN, userWithId.getGoogleUserId());

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

        return TEMPLATE__CHART;
    }

    @RequestMapping("/taskUpdateMyStats")
    public String taskStartForMyStats(
            @RequestHeader(value = "referer", required = false) final String refererUrl,
            HttpSession session, Model model) {
        Integer userIdLoggedIn = getUserIdLoggedIn(session); //Integer expected
        if (userIdLoggedIn == null) {
            log.info("User is not logged in, returning no chart data");
            return "redirect:" + AuthController.PATH__OAUTH_START;
        }

        taskService.setUpdateForUser(userIdLoggedIn);
        boolean startNowResult = taskService.taskStartSoon();

        log.info("Reschedule for immediate execution:  success=" + startNowResult + " redirecting to: " + refererUrl);
        return "redirect:" + refererUrl;
    }

    // Chart data provided by AJAX request
    @RequestMapping(value = {"/data/combinedChart", "/data/combinedChart/{chartIntervalHours}"})
    public @ResponseBody ResultsContainerMessageData
    readUnread(@PathVariable(required = false) Integer chartIntervalHours, HttpSession session) throws SQLException, JsonProcessingException {

        Integer userIdLoggedIn = getUserIdLoggedIn(session); //Integer expected
        if (userIdLoggedIn == null) {
            log.info("User is not logged in, returning no chart data");
            return null;
        }

        long dataStartTime;
        if (chartIntervalHours == null) {
            long startTimeForUserData = gmailDataService.getFirstUsableDataForUser(userIdLoggedIn);
            dataStartTime = startTimeForUserData;
        } else {
            dataStartTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(chartIntervalHours);
        }
        String intervalDescription = formatInterval(dataStartTime);
        log.info("determined chart interval of " + intervalDescription);

        // Use raw unread/total counts to build a data series
        List<GmailLabelUpdate> messageTotalsDatabase = gmailDataService.getMessageAndUnreadCountClose(userIdLoggedIn.intValue(), dataStartTime);
        List<DataPointMessageCount> messageTotalsUi = DataPreparationFrontend.buildDataSeriesAllMessagesCount(messageTotalsDatabase);


        // Use mailboxChangesDatabase to build four line-segment sequences
        int numberOfBuckets = messageTotalsUi.size();
        new ArrayList<>();

        if (numberOfBuckets == 0) {
            log.warn("No chart data to return.  New user?");
            return null;
        }


        List<HistoryEvent> mailboxChangesDatabase = gmailDataService.getMailboxMessageChanges(userIdLoggedIn.intValue(), dataStartTime);
        DataSeriesLabelChangeSegments mailboxChangesUi = DataPreparationFrontend.buildDataSeriesForAllCountChanges(messageTotalsDatabase, mailboxChangesDatabase);

        ResultsContainerMessageData resultsContainer = new ResultsContainerMessageData();
        resultsContainer.setDataSeriesMessageCounts(messageTotalsUi);
        resultsContainer.setDataSeriesLabelChangeSegments(mailboxChangesUi);

        log.info("Chart Data: " + resultsContainer.describeCountData());
        // log.info("Chart Data: " + resultsContainer.describeDeltaData());

        return resultsContainer;
    }

    private long getActualDuration(List<GmailLabelUpdateDummy> dataOverTimeMessageAndUnreadCount) {
        if (dataOverTimeMessageAndUnreadCount.size() < 2) {
            return 0L;
        }

        GmailLabelUpdateDummy first = dataOverTimeMessageAndUnreadCount.get(0);
        GmailLabelUpdateDummy last = dataOverTimeMessageAndUnreadCount.get(dataOverTimeMessageAndUnreadCount.size()-1);
        long durationActual = last.getUpdateTimeMillis() - first.getUpdateTimeMillis();
        return durationActual;
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