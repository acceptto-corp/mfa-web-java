package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.LocalUser;
import org.springframework.cglib.core.Local;
import play.Logger;
import play.Play;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.F;
import play.mvc.*;
import play.libs.ws.*;
import play.libs.F.Function;
import play.libs.F.Promise;
import views.html.account.signup.created;
import views.html.mfa.enablemfa;

public class Mfa extends Controller {

    @Security.Authenticated(Secured.class)
    public static Result enableMfa() {
        return ok(enablemfa.render(LocalUser.findByEmail(request().username())));
    }

    @Security.Authenticated(Secured.class)
    public static Promise<Result> enableMfaPost() {
        final String email = request().username();
        DynamicForm form = Form.form().bindFromRequest();
        String mfa_email = form.get("mfa_email");

        final String mfaSite = Play.application().configuration().getString("mfa.site");

        F.Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v9/is_user_valid")
                .setQueryParameter("email", mfa_email)
                .setQueryParameter("uid", Play.application().configuration().getString("mfa.app.uid"))
                .setQueryParameter("secret", Play.application().configuration().getString("mfa.app.secret"))
                .setContentType("application/x-www-form-urlencoded")
                .post("");

        return responsePromise.map(new F.Function<WSResponse, Result>() {
            @Override
            public Result apply(WSResponse wsResponse) throws Throwable {
                JsonNode json = wsResponse.asJson();

                if (json.has("valid") && json.get("valid").asBoolean()) {
                    if (json.has("registration_state") && json.get("registration_state").asText().equals("finished")) {
                        // Using the same email as Acceptto one.
                        LocalUser user = LocalUser.findByEmail(email);
                        if (user == null) {
                            Logger.error("User " + email + " not found");
                            ctx().flash().put("notice", "User not found.");
                            return redirect(routes.Dashboard.index());
                        }

                        user.mfa_email = mfa_email;
                        user.mfa_authenticated = true;
                        user.save();
                        Logger.debug("MFA email has set to " + mfa_email);
                        ctx().flash().put("notice", "Enabling Multi Factor Authentication was successful.");
                        return redirect(routes.Dashboard.index());
                    } else {
                        ctx().flash().put("notice", "Entered email hasn't finished the registration process yet.");
                        return redirect(routes.Mfa.enableMfa());
                    }
                }

                ctx().flash().put("notice", "Entered email is not a valid Acceptto user.");
                return redirect(routes.Mfa.enableMfa());
            }
        });
    }

    public static Promise<Result> check() {
        final String email = session("email");
        final LocalUser user = LocalUser.findByEmail(email);
        if (user == null) {
            ctx().flash().put("notice", "MFA Two Factor Authentication request timed out with no response.");
            return Promise.pure(redirect(routes.Application.index()));
        }

        String mfaSite = Play.application().configuration().getString("mfa.site");
        String channel = ctx().session().get("channel");

        Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v9/check")
                .setQueryParameter("uid", Play.application().configuration().getString("mfa.app.uid"))
                .setQueryParameter("secret", Play.application().configuration().getString("mfa.app.secret"))
                .setQueryParameter("channel", channel)
                .setQueryParameter("email", user.mfa_email)
                .post("");

        Promise<Result> resultPromise = responsePromise.map(new Function<WSResponse, Result>() {
            @Override
            public Result apply(WSResponse wsResponse) throws Throwable {
                JsonNode json = wsResponse.asJson();

                String status = json.get("status").asText();

                Logger.debug("Check result status: " + status);

                if (status.equals("approved")) {
                    user.mfa_authenticated = true;
                    user.save();
                    ctx().flash().put("notice", "MFA Two Factor Authentication request was accepted.");
                    return redirect(routes.Dashboard.index());
                } else if (status.equals("rejected")) {
                    user.mfa_authenticated = false;
                    user.save();
                    ctx().flash().put("notice", "MFA Two Factor Authentication request was declined.");
                    return redirect(routes.Dashboard.index());
                } else {
                    ctx().flash().put("notice", "MFA Two Factor Authentication request was unknown!");
                    return redirect(routes.Dashboard.index());
                }
            }
        });

        return resultPromise;
    }

    public static Promise<Result> accepttoAuthenticate(LocalUser user, Integer authType){
        final String mfaSite = Play.application().configuration().getString("mfa.site");

        WSRequestHolder request = WS.url(mfaSite + "/api/v9/authenticate_with_options")
                .setQueryParameter("email", user.mfa_email)
                .setQueryParameter("uid", Play.application().configuration().getString("mfa.app.uid"))
                .setQueryParameter("secret", Play.application().configuration().getString("mfa.app.secret"))
                .setQueryParameter("message", "Acceptto is wishing to authorize")
                .setQueryParameter("type", "Login");

        if (authType != null) {
            request = request.setQueryParameter("auth_type", authType.toString());
        }

        Promise<WSResponse> responsePromise = request.post("");

        Promise<Result> resultPromise = responsePromise.map(new Function<WSResponse, Result>() {
            @Override
            public Result apply(WSResponse wsResponse) throws Throwable {
                JsonNode json = wsResponse.asJson();

                if (json.has("success") && !json.get("success").asBoolean()) {
                    flash("error", json.get("message").asText());
                    return Application.GO_HOME;
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
}
