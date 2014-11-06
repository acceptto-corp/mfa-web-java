# About

This is a demo project that demonstrate how to integrate your account system (users) with Acceptto multifactor authentication system.

# How to Run

1. Install [Play Framework 2.3](https://www.playframework.com/download)
2. Clone the project and goto the project directory
3. Configure the database according to the `conf/application.conf`
4. Make sure that mfa site which is set in `conf/application.conf` is available (local or public)
4. Run `activator run` and browse to `localhost:9000`
5. Apply evolution (it'll asked for the first time to setup database)

# Create the project from scratch

You can create another project from scratch.

## Create Project

1. Install [Play Framework 2.3](https://www.playframework.com/download) (Typesafe Activator)
2. Run `activator new`
3. Enter `PlayStartApp` as template name
4. Apply the following changes into the `PlayStartApp` project

## Models

Add the following fields to `User` model.

```java
    public String mfa_access_token;

    public Boolean mfa_authenticated;
```

## Setup email settings

rename `mail.conf.example` to `mail.conf` and update data.

## Add Enable MFA link to view

Change `index.scala.html` signature to get `enableMfaUrl` as parameter and show it when `mfa_access_token` exist:

```
@(user: User, enableMfaUrl: String)

@main(user) {

    @if(user.mfa_access_token == null){
        <p>
            <a href='@enableMfaUrl'>Enable MFA</a>
        </p>
    }

    DASHBOARD Example

}
```

Pass `enableMfaUrl` to the dashboard `index.scala.html` in `Dashboard.java`:

```java
    public static Result index() {
        String mfaSite = Play.application().configuration().getString("mfa.site");
        String mfaUid = Play.application().configuration().getString("mfa.app.uid");
        String enableMfaUrl = mfaSite + "/mfa/email?uid=" + mfaUid;

        return ok(index.render(User.findByEmail(request().username()), enableMfaUrl));
    }
```

## Configuration

Add the following configuration to `application.conf`:

```
mfa.app.uid="application unique id you got from acceptto"
mfa.app.secret="mfa app secret you got from acceptto"
mfa.site="https://mfa.acceptto.com"
```

## Implement MFA controller

Add `Mfa.java` in controllers:
```java
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

/**
 * Created by amir on 11/5/14.
 */
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

        String userName = ctx().request().username();
        if (userName == null || userName.isEmpty()) {
            Logger.debug("MFA Callback - User expired");
            ctx().flash().put("notice", "Time out!");
            return redirect(routes.Application.index());
        }

        Logger.debug("MFA Callback - Everything OK");

        LocalUser user = LocalUser.findByEmail(userName);
        user.mfa_access_token = accessToken;
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

        Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v6/check")
                .setHeader("Authorization", "Bearer " + user.mfa_access_token)
                .setContentType("application/x-www-form-urlencoded")
                .post("channel=" + channel);

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
```

## Configure routes

Remove the following route:

```
GET     /dashboard                  controllers.Dashboard.index()
```

Add the following routes:

```
# Acceptto MFA
GET     /auth/mfa_check             controllers.Mfa.check()
GET     /auth/mfa/callback          controllers.Mfa.callBack()
GET     /mfa                        controllers.Dashboard.index()
```

## Security

Implement `Authenticator`:

```java
public class Secured extends Security.Authenticator {

    @Override
    public String getUsername(Http.Context ctx) {
        String email = ctx.session().get("email");
        if (email == "" || email == null) {
            return null;
        }

        User user = User.findByEmail(email);
        if (user == null || (user.mfa_access_token != null && user.mfa_access_token != "" && !user.mfa_authenticated)) {
            return null;
        }

        return email;
    }

    @Override
    public Result onUnauthorized(Http.Context ctx) {
        return redirect(routes.Application.index());
    }
}
```

Change `authenticate` method of Application.java:

```java
    public static Promise<Result> authenticate() {
        Form<Login> loginForm = form(Login.class).bindFromRequest();

        Form<Register> registerForm = form(Register.class);

        if (loginForm.hasErrors()) {
            return Promise.pure((Result) badRequest(index.render(registerForm, loginForm)));
        } else {

            session("email", loginForm.get().email);

            final User user = User.findByEmail(loginForm.get().email);
            if (user.mfa_access_token != null) {
                user.mfa_authenticated = false;
                user.save();

                final String mfaSite = Play.application().configuration().getString("mfa.site");

                Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v6/authenticate")
                        .setHeader("Authorization", "Bearer " + user.mfa_access_token)
                        .setQueryParameter("message", "Acceptto is wishing to authorize")
                        .setQueryParameter("type", "Login")
                        .setContentType("application/x-www-form-urlencoded")
                        .post("");

                Promise<Result> resultPromise = responsePromise.map(new Function<WSResponse, Result>() {
                    @Override
                    public Result apply(WSResponse wsResponse) throws Throwable {
                        JsonNode json = wsResponse.asJson();

                        String channel = json.get("channel").asText();
                        ctx().session().put("channel", channel);

                        String callbackUrl = routes.Mfa.callBack().absoluteURL(ctx().request());
                        String redirectUrl = mfaSite + "/mfa/index?channel=" + channel + "&callback_url=" + callbackUrl;

                        return redirect(redirectUrl);
                    }
                });

                return resultPromise;
            }

            return Promise.pure(GO_DASHBOARD);
        }
    }

```
