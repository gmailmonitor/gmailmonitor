package pack.frontend.databind;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import pack.frontend.AuthController;

import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
public class GreetingController {

    public static final String TEMPLATE__INTRO = "intro";
    public static final String TEMPLATE__LOGIN_PRENOTICE = "loginPreNotice";

    public static final String PATH__LOGIN_PRENOTICE = "loginNotice";

    public static final String MODEL__URL_LOGIN_PRENOTICE = "urlLoginPreNotice";

    @Autowired AuthController authController;

    @RequestMapping("/")
    public String introPage(HttpSession session, Model model) {
        Integer userIdLoggedIn = (Integer) session.getAttribute(AuthController.SESSION__USER_ID_LOGGED_IN); //Integer expected
        if (userIdLoggedIn != null) {
            return "redirect:" + AuthController.PATH__LOGIN_STATUS;
        }

        model.addAttribute(MODEL__URL_LOGIN_PRENOTICE, PATH__LOGIN_PRENOTICE);
        return TEMPLATE__INTRO;
    }

    @RequestMapping(PATH__LOGIN_PRENOTICE)
    public String disclaimerPage(HttpSession session, Model model) throws IOException {
        Integer userIdLoggedIn = (Integer) session.getAttribute(AuthController.SESSION__USER_ID_LOGGED_IN); //Integer expected
        if (userIdLoggedIn != null) {
            return "redirect:" + AuthController.PATH__LOGIN_STATUS;
        }

        authController.addGoogleAuthStartUrl(model);

        return TEMPLATE__LOGIN_PRENOTICE;
    }
}
