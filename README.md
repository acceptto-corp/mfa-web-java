# Introduction

This is a demo project that demonstrates how to integrate your account system (users) with Acceptto multi-factor authentication/authorization system.

# Requirement

Before being able to use this sample you need to obtain an Acceptto MFA Application UID and Secret:

1. [Singup](https://acceptto.com/users/sign_up) for a new Acceptto acount or [Login](https://acceptto.com/users/sign_in) to your Accpetto dashboard
1. Navigate to **Applications** through the side menu
1. Click on the **New Application** button to create a new application, and then
	1. Choose a **Name** for your application which you're going to enable the multi-factor authentication for
	1. Set the **Redirect URL** to `http://localhost:9000/auth/mfa/callback`
	1. Set the **Color** to whatever you like, this is the color band user will see next to your application name in Acceptto mobile app
1. Find the new create application in the list and click on **Details** button
2. Copy and keep the **UID** and **Secret**. You will need them in the next steps

# Run the sample project

1. Install [sbt](https://www.scala-sbt.org/download.html)
2. `git clone git@github.com:acceptto-corp/mfa-web-java.git` and go to `mfa-web-java` directory
4. Update `mfa.app.uid`, `mfa.app.secret` values in `conf/application.conf` as obtained in the previous section
4. Run `sbt run` and browse to `localhost:9000`
5. Apply evolution (You'll be asked only for the first time in order to setup the database)

# How it works

Although you can go ahead and look a the code to see how it works, for better understanding we explain the key components that are needed in order to enable MFA on an existing Java application.

This guide uses Play Framework (Java version) but the concepts are the same for any other Java framework. You can use most part of the provided code as it is in any other framework.

## Data model

The following fields are added to the normal `User` model in `LocalUser.java`:

```java
    public String mfa_email;

    public Boolean mfa_authenticated;
```

* `mfa_email` represents the corresponding email which is used to register with Acceptto
* `mfa_authenticated` indicates whether the user second factor authentication was successful or not

## Authentication

The user token is stored in session, but in addition to that when `mfa_email` is set (which means MFA is enabled for the account), user is not considered authenticated unless `mfa_authenticated` is `true`. Here is the piece of code which retrieves the current authenticated user in `app/controllers/Secured.java`:

```java
    public String getUsername(Http.Context ctx) {
        String email = ctx.session().get("email");
        if (email == "" || email == null) {
            return null;
        }

        LocalUser user = LocalUser.findByEmail(email);
        if (user == null || (user.mfa_email != null && user.mfa_email != "" && (user.mfa_authenticated == null || !user.mfa_authenticated))) {
            return null;
        }

        return email;
    }
```

## More about MFA email

In this sample app, `email` (in `models/LocalUser.java`) is basically the username, but we also have `mfa_email` to be able to connect Acceptto It'sMe accounts to the accounts in this sample app.

Here is an example to make it easier to understand the relationship between `mfa_email` and `email`:

#### Scenario 1

1. You've created an account in Acceptto It'sMe application using `A@email.com`
2. You run this sample Java app and use the same email `A@email.com` in the sign up form
3. As this email has already been registered with It'sMe, you'll be asked for MFA in your next login in this sample app
4. You receive MFA confirmations on your It'sMe app which is logged in with `A@email.com`

In this scenario, both `email` and `mfa_email` was set to `A@email.com`.

#### Scenario 2

1. You run this sample Java app and use the same email `B@email.com` in the sign up form
2. You'll be able to login to the sample app only with email and password
3. You'll see `Enable MFA` link after login and you click on the link
4. You enter `A@email.com` as `Acceptto's MFA email` and click on `Enable`
5. This enables the MFA, connects `B@email.com` in this sample app to `A@email.com` in It'sMe
6. When you login using `B@email.com` in this sample app, you receive MFA confirmations on your It'sMe app which is logged in with `A@email.com`

In this scenario, `email` was set to `B@email.com` and `mfa_email` was set to `A@email.com`.

## Enabling the MFA during the sign up

We'll check the registered email (by calling `/api/v9/is_user_valid` API) to see if an Acceptto account has already been created using that email. If that was the case by setting `mfa_email` to the same email as `LocalUser.email`, we'll be able to use that in the further API calls and basically we've enabled the MFA automatically. If the email wasn't registered in Acceptto we allow the user to it later.

Here is the block of the code in `Signup.java` that checks the sign up email:

```java
    LocalUser user = new LocalUser();
    user.email = register.email;
    user.fullname = register.fullname;
    user.passwordHash = Hash.createPassword(register.inputPassword);
    user.confirmationToken = UUID.randomUUID().toString();

    // Temporary confirm user
    user.validated = true;

    final String mfaSite = Play.application().configuration().getString("mfa.site");

    F.Promise<WSResponse> responsePromise = WS.url(mfaSite + "/api/v9/is_user_valid")
            .setQueryParameter("email", register.email)
            .setQueryParameter("uid", Application.appUID)
            .setQueryParameter("secret", Application.appSecret)
            .setContentType("application/x-www-form-urlencoded")
            .post("");

    F.Promise<Result> resultPromise = responsePromise.map(new F.Function<WSResponse, Result>() {
        @Override
        public Result apply(WSResponse wsResponse) throws Throwable {
            JsonNode json = wsResponse.asJson();

            if (json.has("valid") && json.get("valid").asBoolean()) {
                if (json.has("registration_state") && json.get("registration_state").asText().equals("finished")) {
                    // Using the same email as Acceptto one.
                    user.mfa_email = register.email;
                    Logger.debug("MFA email has set to " + register.email);
                } else {
                    Logger.warn("User has started registration in Acceptto but hasn't finished");
                }
            }

            user.save();

            return ok(created.render());
        }
    });
```

## Add enabling MFA link to view

When `mfa_email` is null, it shows that the email used for sign up hasn't been registered in Acceptto's It'sMe App. So we need to show the user how he/she can enable the MFA by creating a new Acceptto account (using the existing email) or use and existing one and connect them to their account in this Java sample application.

We do this by showing a link to `/auth/mfa/enable` (which simply renders `mfa/enablemfa.scala.html`) in dashboard when `mfa_email` is null. In Enable MFA page we've provided links to Acceptto apps and a text box to get the Acceptto's account email through which the user wants to enable the MFA.

## MFA authentication process

Here is what happens when the user logs in, while MFA is enabled:

1. `mfa_authenticated` will be set to `false` (`authenticate` method in `app/controllers/Application.java`)
2. `/api/v9/authenticate_with_options` will be called (`accepttoAuthenticate` method in `app/controllers/Mfa.java`)
3. Returned `channel` will be stored in session
4. The user will be redirected to Acceptto's `/mfa/index` page
5. The callback will post the data to `/auth/mfa_check` (`check` method in `app/controllers/Mfa.java`)
6. `/api/v9/check` will be called to validate the callback
7. If successful, the user will be marked as authenticated by setting `mfa_authenticated` to `true`
