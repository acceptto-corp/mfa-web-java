package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.LocalUser;
import play.Logger;
import play.Play;
import play.data.DynamicForm;
import play.data.Form;
import play.mvc.*;
import play.libs.ws.*;
import play.libs.F.Function;
import play.libs.F.Promise;

public class Mfa extends Controller {

    public static Result callBack() {
        DynamicForm form = Form.form().bindFromRequest();
        String error = form.get("error");
        if (error != null && !error.isEmpty()) {
            Logger.debug("MFA Callback - Error returned: " + error);
            ctx().flash().put("notice", error);
            return redirect(routes.Application.index());
        }

        String accessToken = form.get("access_token");
        if (accessToken == null || accessToken.isEmpty()) {
            Logger.debug("MFA Callback - Access Token was null or empty");
            ctx().flash().put("notice", "Invalid parameters!");
            return redirect(routes.Application.index());
        }

        String email = ctx().session().get("email");
        if (email == null || email.isEmpty()) {
            Logger.debug("MFA Callback - User expired");
            ctx().flash().put("notice", "Time out!");
            return redirect(routes.Application.index());
        }

        Logger.debug("MFA Callback - Everything OK");

        LocalUser user = LocalUser.findByEmail(email);
        user.mfa_email = accessToken;
        user.mfa_authenticated = true;
        user.save();

        ctx().flash().put("notice", "Enabling Multi Factor Authentication was successful.");
        return redirect(routes.Dashboard.index());
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
                .setQueryParameter("email", email)
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
}
