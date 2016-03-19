package org.ecocean.rest;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.ecocean.ContextConfiguration;
import org.ecocean.Global;
import org.ecocean.export.Export;
import org.ecocean.export.ExportFactory;
import org.ecocean.security.SecurityInfo;
import org.ecocean.security.User;
import org.ecocean.security.UserService;
import org.ecocean.security.UserToken;
import org.ecocean.servlet.ServletUtils;
import org.ecocean.util.LogBuilder;
import org.ecocean.util.NotificationException;
import org.ecocean.util.WildbookUtils;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.samsix.database.Database;
import com.samsix.database.DatabaseException;

@RestController
@RequestMapping(value = "/api/user")
public class UserController {
    private static Logger logger = UserService.logger;

    public static boolean notAcceptedTerms(final User user) {
        return (Global.INST.getAppResources().getBoolean("user.agreement.show", false)
                && (Global.INST.getAppResources().getString("user.agreement.url", null) != null)
                && ! user.getAcceptedUserAgreement());
    }


//    public static UserToken getUserToken(final HttpServletRequest request,
//                                         final String username,
//                                         final String password) throws DatabaseException {
//        try (Database db = ServletUtils.getDb(request)) {
//            User user = UserFactory.getUserByNameOrEmail(db, username);
//
//            Account acc = Stormpath.getAccount(username);
//
//            if (acc == null) {
//                if (user == null) {
//                    return null;
//                }
//
//                String hashedPass = ServletUtilities.hashAndSaltPassword(password, user.getSalt());
//                return new UserToken(user, new UsernamePasswordToken(user.getUserId().toString(), hashedPass));
//            }
//
//            String hashedPass;
//            try {
//                Stormpath.loginAccount(username, password);
//                //
//                // If they got their user through stormpath then we trust their
//                // password to be correct so we use the value from the db. Should be the
//                // same as that on stormpath?
//                //
//                if (user == null) {
//                    if (logger.isDebugEnabled()) {
//                        logger.debug("successful authentication via Stormpath, but no Wildbook user for email "
//                                + acc.getEmail()
//                                + ". creating one!");
//                    }
//                    user = Stormpath.createUserFromStormpath(acc, password);
//                    UserFactory.saveUser(db, user);
//                }
//
//                hashedPass = user.getHashedPass();
//            } catch (ResourceException ex) {
//                logger.warn("failed to authenticate user '"
//                        + username
//                        + "' via Stormpath; falling back to Wildbook User: "
//                        + ex.toString());
//                hashedPass = ServletUtilities.hashAndSaltPassword(password, user.getSalt());
//            }
//
//            return new UserToken(user, new UsernamePasswordToken(user.getUserId().toString(), hashedPass));
//        }
//    }


    public static UserToken getUserToken(final HttpServletRequest request,
                                         final String username,
                                         final String password) throws DatabaseException {
        User user = Global.INST.getUserService().getUserByNameOrEmail(username);

        if (user == null) {
            return null;
        }

        String hashedPass = WildbookUtils.hashAndSaltPassword(password, user.getSalt());
        UsernamePasswordToken token = new UsernamePasswordToken(user.getId().toString(), hashedPass);
        //
        // TODO: Built in support for remember me. Should we have the user pass this in so as
        // to not assume true?
        //
        token.setRememberMe(true);

        return new UserToken(user, token);
    }


    public static String getAllRolesForUserAsString(final HttpServletRequest request,
                                                    final Integer userid) throws DatabaseException {
        if (userid == null) {
            return "";
        }

        SecurityInfo info = Global.INST.getUserService().getSecurityInfo(userid.toString());
        if (info == null) {
            return "";
        }

        StringBuilder rolesFound = new StringBuilder();
        for (String context : info.getContextRoleKeys()) {
            String contextLabel = ContextConfiguration.getNameForContext(context);

            for(String role : info.getContextRoles(context)) {
                rolesFound.append(contextLabel + ":" + role + "\r");
            }
        }
        return rolesFound.toString();
    }

    @RequestMapping(value = "login", method = RequestMethod.POST)
    public SimpleUser loginCall(final HttpServletRequest request,
                                @RequestBody
                                final LoginAttempt loginAttempt) throws DatabaseException
    {
        if (logger.isDebugEnabled()) {
            LogBuilder.get("login attempt").appendVar("username", loginAttempt.username).debug(logger);
        };

        UserToken userToken = getUserToken(request, loginAttempt.username, loginAttempt.password);

        if (userToken == null) {
            throw new NotificationException("No user with username [" + loginAttempt.username + "] is found.");
        }

        try {
            Subject subject = SecurityUtils.getSubject();
            subject.login(userToken.getToken());

            Global.INST.getUserService().setLastLogin(userToken.getUser());

            return userToken.getUser().toSimple();
        } finally {
            userToken.clear();
        }
    }


    public static void logoutUser(final HttpServletRequest request) throws ServletException, IOException {
        Subject subject = SecurityUtils.getSubject();
        if (subject != null) {
            subject.logout();
        }

        HttpSession session = request.getSession(false);
        if( session != null ) {
            session.invalidate();
        }
    }


    /*
     * This doesn't work in the sense that from a different origin (a node server calling in restfully)
     * does not know that we are now logged out. We are logged out because going to wildbook verifies
     * that, but if we go to our node server and check isLoggedIn it thinks we are. Thus, the UserPrincipal
     * for that session is not getting nulled out. However, if we use this exact same line of code in
     * the servlet LogoutUser from this same node server, the UserPrincipal is nulled out. Something
     * bizarre going on there that I don't understand. Just means I'm hanging on to that LogoutUser
     * one-line servlet. :)
     */
//    @RequestMapping(value = "logout", method = RequestMethod.GET)
//    public void logoutCall(final HttpServletRequest request) throws ServletException, IOException
//    {
//        logoutUser(request);
//    }


    @RequestMapping(value = "resetpass", method = RequestMethod.POST)
    public void resetPassword(final HttpServletRequest request,
                              @RequestBody final ResetPass reset) throws DatabaseException {
        if (logger.isDebugEnabled()) {
            logger.debug(LogBuilder.quickLog("token", reset.token));
        }

        if (reset.password.length() < 8) {
            throw new NotificationException("This password is too short!");
        }

        UserService userService = Global.INST.getUserService();
        userService.resetPassWithToken(reset.token, reset.password);
    }

    @RequestMapping(value = "exports", method = RequestMethod.GET)
    public List<Export> export(final HttpServletRequest request) throws DatabaseException {
        try (Database db = ServletUtils.getDb(request)) {
           return ExportFactory.getUserUndeliveredExports(db, ServletUtils.getUser(request).getId());
        }
    }

    @RequestMapping(value = "searchusers", method = RequestMethod.POST)
    public List<SimpleUser> getUsers(final HttpServletRequest request,
                               @RequestBody final String q) throws DatabaseException {
        UserService userService = Global.INST.getUserService();
        return userService.searchUsers(q);
    }

    static class LoginAttempt {
        public String username;
        public String password;
    }

    static class ResetPass {
        public String token;
        public String password;
    }
}