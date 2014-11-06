package controllers;

import models.LocalUser;
import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.dashboard.index;

/**
 * User: yesnault
 * Date: 22/01/12
 */
@Security.Authenticated(Secured.class)
public class Dashboard extends Controller {

    public static Result index() {
        String mfaSite = Play.application().configuration().getString("mfa.site");
        String mfaUid = Play.application().configuration().getString("mfa.app.uid");
        String enableMfaUrl = mfaSite + "/mfa/email?uid=" + mfaUid;

        return ok(index.render(LocalUser.findByEmail(request().username()), enableMfaUrl));
    }
}
