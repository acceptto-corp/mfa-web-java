package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.LocalUser;
import models.utils.AppException;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints;
import play.i18n.Messages;
import play.libs.F.*;
import play.libs.ws.*;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

import static play.data.Form.form;

/**
 * Login and Logout.
 * User: yesnault
 */
public class Application extends Controller {

    public static Result GO_HOME = redirect(
            routes.Application.index()
    );

    public static Result GO_DASHBOARD = redirect(
            routes.Dashboard.index()
    );

    /**
     * Display the login page or dashboard if connected
     *
     * @return login page or dashboard
     */
    public static Result index() {
        // Check that the email matches a confirmed user before we redirect
        String email = ctx().session().get("email");
        if (email != null) {
            LocalUser user = LocalUser.findByEmail(email);
            if (user != null && user.validated && (user.mfa_email == null || (user.mfa_authenticated != null && user.mfa_authenticated))) {
                return GO_DASHBOARD;
            } else {
                if (user.mfa_email != null && (user.mfa_authenticated == null || !user.mfa_authenticated)) {
                    Logger.debug("User mfa access enabled but is not authenticated");
                }

                Logger.debug("Clearing invalid session credentials");
                session().clear();
            }
        }

        return ok(index.render(form(Register.class), form(Login.class)));
    }

    /**
     * Login class used by Login Form.
     */
    public static class Login {

        @Constraints.Required
        public String email;
        @Constraints.Required
        public String password;

        /**
         * Validate the authentication.
         *
         * @return null if validation ok, string with details otherwise
         */
        public String validate() {

            LocalUser user = null;
            try {
                user = LocalUser.authenticate(email, password);
            } catch (AppException e) {
                return Messages.get("error.technical");
            }
            if (user == null) {
                return Messages.get("invalid.user.or.password");
            } else if (!user.validated) {
                return Messages.get("account.not.validated.check.mail");
            }
            return null;
        }

    }

    public static class Register {

        @Constraints.Required
        public String email;

        @Constraints.Required
        public String fullname;

        @Constraints.Required
        public String inputPassword;

        /**
         * Validate the authentication.
         *
         * @return null if validation ok, string with details otherwise
         */
        public String validate() {
            if (isBlank(email)) {
                return "Email is required";
            }

            if (isBlank(fullname)) {
                return "Full name is required";
            }

            if (isBlank(inputPassword)) {
                return "Password is required";
            }

            return null;
        }

        private boolean isBlank(String input) {
            return input == null || input.isEmpty() || input.trim().isEmpty();
        }
    }

    /**
     * Handle login form submission.
     *
     * @return Dashboard if auth OK or login form if auth KO
     */
    public static Promise<Result> authenticate() {
        Form<Login> loginForm = form(Login.class).bindFromRequest();

        Form<Register> registerForm = form(Register.class);

        if (loginForm.hasErrors()) {
            return Promise.pure((Result) badRequest(index.render(registerForm, loginForm)));
        } else {

            session("email", loginForm.get().email);

            final LocalUser user = LocalUser.findByEmail(loginForm.get().email);
            if (user.mfa_email != null) {
                user.mfa_authenticated = false;
                user.save();

                final String mfaSite = Play.application().configuration().getString("mfa.site");

                Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v9/authenticate_with_options")
                        .setQueryParameter("email", user.mfa_email)
                        .setQueryParameter("uid", Play.application().configuration().getString("mfa.app.uid"))
                        .setQueryParameter("secret", Play.application().configuration().getString("mfa.app.secret"))
                        .setQueryParameter("message", "Acceptto is wishing to authorize")
                        .setQueryParameter("type", "Login")
                        .setContentType("application/x-www-form-urlencoded")
                        .post("");

                Promise<Result> resultPromise = responsePromise.map(new Function<WSResponse, Result>() {
                    @Override
                    public Result apply(WSResponse wsResponse) throws Throwable {
                        JsonNode json = wsResponse.asJson();

                        if (json.has("success") && !json.get("success").asBoolean()) {
                            flash("error", json.get("message").asText());
                            return GO_HOME;
                        }

                        String channel = json.get("channel").asText();
                        ctx().session().put("channel", channel);

                        String callbackUrl = routes.Mfa.check().absoluteURL(ctx().request());
                        String redirectUrl = mfaSite + "/mfa/index?channel=" + channel + "&callback_url=" + callbackUrl;

                        return redirect(redirectUrl);
                    }
                });

                return resultPromise;
            }

            return Promise.pure(GO_DASHBOARD);
        }
    }

    /**
     * Logout and clean the session.
     *
     * @return Index page
     */
    public static Result logout() {
        session().clear();
        flash("success", Messages.get("youve.been.logged.out"));
        return GO_HOME;
    }
}