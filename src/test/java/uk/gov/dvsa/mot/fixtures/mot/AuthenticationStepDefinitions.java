package uk.gov.dvsa.mot.fixtures.mot;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;

import cucumber.api.java8.En;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import uk.gov.dvsa.mot.data.DatabaseDataProvider;
import uk.gov.dvsa.mot.framework.WebDriverWrapper;
import uk.gov.dvsa.mot.otp.Generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

/**
 * Step definitions specific to authenticating within the MOT application.
 */
public class AuthenticationStepDefinitions implements En {

    /** The logger to use. */
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationStepDefinitions.class);

    /** The web driver to use. */
    private final WebDriverWrapper driverWrapper;

    /** The data provider to use. */
    private final DatabaseDataProvider dataProvider;

    /**
     * Creates a new instance.
     * @param driverWrapper     The driver wrapper to use
     * @param dataProvider      The data provider to use
     * @param env               The config settings to use
     */
    @Inject
    public AuthenticationStepDefinitions(WebDriverWrapper driverWrapper, DatabaseDataProvider dataProvider,
                                         Environment env) {
        logger.debug("Creating AuthenticationStepDefinitions...");
        this.driverWrapper = driverWrapper;
        this.dataProvider = dataProvider;

        Given("^I login with 2FA using \"([^\"]+)\" as \\{([^\\}]+)\\}, \\{([^\\}]+)\\}$",
                (String dataSetName, String usernameKey, String key2) ->
                    loginWith2fa(dataSetName, usernameKey,
                            env.getRequiredProperty("password"), env.getRequiredProperty("seed"),
                            env.getRequiredProperty("maxLoginRetries", Integer.class), key2));

        Given("^I login with 2FA using \"([^\"]+)\" as \\{([^\\}]+)\\}, \\{([^\\}]+)\\}, \\{([^\\}]+)\\}$",
                (String dataSetName, String usernameKey, String key2, String key3) ->
                    loginWith2fa(dataSetName, usernameKey,
                            env.getRequiredProperty("password"), env.getRequiredProperty("seed"),
                            env.getRequiredProperty("maxLoginRetries", Integer.class), key2, key3));

        Given("^I login with 2FA using \"([^\"]+)\" as \\{([^\\}]+)\\}, \\{([^\\}]+)\\}, \\{([^\\}]+)\\},"
                + " \\{([^\\}]+)\\}$",
                (String dataSetName, String usernameKey, String key2, String key3, String key4) ->
                    loginWith2fa(dataSetName, usernameKey,
                            env.getRequiredProperty("password"), env.getRequiredProperty("seed"),
                            env.getRequiredProperty("maxLoginRetries", Integer.class), key2, key3, key4));

        Given("^I login with 2FA using \"([^\"]+)\" as \\{([^\\}]+)\\}, \\{([^\\}]+)\\}, \\{([^\\}]+)\\},"
                        + " \\{([^\\}]+)\\}, \\{([^\\}]+)\\}$",
                (String dataSetName, String usernameKey, String key2, String key3, String key4, String key5) ->
                    loginWith2fa(dataSetName, usernameKey,
                            env.getRequiredProperty("password"), env.getRequiredProperty("seed"),
                            env.getRequiredProperty("maxLoginRetries", Integer.class), key2, key3, key4, key5));

        Given("^I login with 2FA using \"([^\"]+)\" as \\{([^\\}]+)\\}, \\{([^\\}]+)\\}, \\{([^\\}]+)\\},"
                        + " \\{([^\\}]+)\\}, \\{([^\\}]+)\\}, \\{([^\\}]+)\\}$",
                (String dataSetName, String usernameKey, String key2, String key3, String key4, String key5,
                 String key6) -> loginWith2fa(dataSetName, usernameKey,
                        env.getRequiredProperty("password"), env.getRequiredProperty("seed"),
                        env.getRequiredProperty("maxLoginRetries", Integer.class), key2, key3, key4, key5, key6));

        Given("^I login with 2FA as \\{([^\\}]+)\\}$", (String usernameKey) ->
                loginWith2fa(usernameKey, env.getRequiredProperty("password"),
                    env.getRequiredProperty("seed")));

        Given("^I login without 2FA using \"([^\"]+)\" as \\{([^\\}]+)\\}$",
                (String dataSetName, String usernameKey) ->
                    loginWithout2fa(dataSetName, usernameKey, env.getRequiredProperty("password"),
                            env.getRequiredProperty("maxLoginRetries", Integer.class)));
    }

    /**
     * Logs a user into the application, using 2FA (password and pin).
     * <p>Note: doesn't tolerate failed logins caused by a user password being manually changed.</p>
     * @param usernameKey       The username data key to set
     * @param password          The password to use
     * @param seed              The OTP seed to use
     */
    private void loginWith2fa(String usernameKey, String password, String seed) {
        if (login2fa(driverWrapper.getData(usernameKey), password, seed)) {
            // check if any special notices need clearing down
            if (driverWrapper.hasLink("Read and acknowledge")) {
                clearDownSpecialNotices();
            }
        } else {
            String message = "Login failed";
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Logs a user into the application, using 2FA (password and pin).
     * <p>Handles failed logins (possible if user password has been manually changed) by trying again with another
     * user.</p>
     * @param dataSetName       The data set to get users from
     * @param usernameKey       The username data key to set
     * @param password          The password to use
     * @param seed              The OTP seed to use
     * @param maxLoginRetries   The number of times to retry login with a different user before failing the test
     * @param keys              The extra data keys to set
     */
    private void loginWith2fa(String dataSetName, String usernameKey, String password, String seed,
                              int maxLoginRetries, String... keys) {
        int loginAttempts = 0;
        while (loginAttempts < maxLoginRetries) {
            loginAttempts++;

            // load username from the dataset, populate the data keys and values
            List<String> dataKeys = new ArrayList<>();
            dataKeys.add(usernameKey);
            Collections.addAll(dataKeys, keys);
            loadData(dataSetName, dataKeys);

            // get the loaded username
            String username = driverWrapper.getData(usernameKey);

            // try to login
            boolean loginSuccessful = login2fa(username, password, seed);

            if (loginSuccessful) {
                // check if any special notices need clearing down
                if (driverWrapper.hasLink("Read and acknowledge")) {
                    clearDownSpecialNotices();
                }

                // all successful
                return;
            }

            // login failed, loop around to try again
        }

        String message = "Login failed after trying " + loginAttempts + " users";
        logger.error(message);
        throw new IllegalStateException(message);
    }

    /**
     * Logs a user into the application, using 2FA (password and pin).
     * @param username      The username data key to set
     * @param password      The password to use
     * @param seed          The OTP seed to use
     * @return Whether the login was successful (any errors will have been logged)
     */
    private boolean login2fa(String username, String password, String seed) {
        try {
            // enter username and password
            login(username, password);

            // check we got to the 2FA PIN screen
            driverWrapper.checkCurrentPageTitle("Your security card PIN");

            // seed taken from the test OTP generator, used on all test systems
            String pin = Generator.generatePin(seed);
            logger.debug("Using PIN {}", pin);

            driverWrapper.enterIntoField(pin, "Security card PIN");
            driverWrapper.pressButton("Sign in");

            // check we got to the home page
            driverWrapper.checkCurrentPageTitle("Your home");

            return true;

        } catch (Throwable ex) {
            // login failed (wrong password, wrong PIN, password needs resetting, etc)
            String message = "login failed for user: " + username;
            logger.error(message);
            return false;
        }
    }

    /**
     * Enters the user name and password and submits the form.
     * @param username  The username to use
     * @param password  The password to use
     */
    private void login(String username, String password) {
        logger.debug("Logging in as username {} and password {}", username, password);
        driverWrapper.browseTo("/login");

        // the visible versions of the username and password fields have dynamic ids ending in _tid1 or _tid2
        driverWrapper.enterIntoFieldWithIdSuffix(username, "_tid1");
        driverWrapper.enterIntoFieldWithIdSuffix(password, "_tid2");
        driverWrapper.pressButton("Sign in");
    }

    /**
     * Logins in as a user that does not require 2FA authentication.
     * @param dataSetName       The name of the data set to use
     * @param usernameKey       The key that the username is stored under
     * @param password          The password to use
     * @param maxLoginRetries   The max number of login retries
     */
    private void loginWithout2fa(String dataSetName, String usernameKey, String password, int maxLoginRetries)  {
        int loginAttempts = 0;
        while (loginAttempts < maxLoginRetries) {
            loginAttempts++;

            // load username from the dataset, populate the data keys and values
            List<String> dataKeys = new ArrayList<>();
            dataKeys.add(usernameKey);
            loadData(dataSetName, dataKeys);

            // get the loaded username
            String username = driverWrapper.getData(usernameKey);

            // try to login
            boolean loginSuccessful = non2FaLogin(username, password);

            if (loginSuccessful) {
                // check if any special notices need clearing down
                if (driverWrapper.hasLink("Read and acknowledge")) {
                    clearDownSpecialNotices();
                }

                // all successful
                return;
            }

            // login failed, loop around to try again
        }

        String message = "Login failed after trying " + loginAttempts + " users";
        logger.error(message);
        throw new IllegalStateException(message);
    }

    /**
     * Tries to login without 2FA and returns true or false based on login.
     * @param username  The username to use
     * @param password  The password to use
     * @return          return whether the login is successful or failed
     */
    private boolean non2FaLogin(String username, String password) {
        try {
            // enter username and password
            login(username, password);

            // check the user has logged in successfully and can see the home page
            driverWrapper.checkCurrentPageTitle("Your home");

            return true;

        } catch (Throwable ex) {
            // login failed (wrong password, password needs resetting, etc)
            logger.error("Login failed for user: " + username);
            return false;
        }
    }

    /**
     * Loads a data set, and populates the scenario test data, for the specified keys.
     * @param dataSetName       The name of the data set
     * @param keys              The keys to populate
     */
    private void loadData(String dataSetName, List<String> keys) {
        List<String> dataSet = dataProvider.getDatasetEntry(dataSetName);

        // check the number of items in the data set matches the number of keys in the test step
        assertEquals("Expected data set " + dataSetName + " to contain " + keys.size() + " data items, "
                        + "but it contained " + dataSet.size() + " data items. Please check your scenario",
                keys.size(), dataSet.size());

        for (int i = 0; i < keys.size(); i++) {
            driverWrapper.setData(keys.get(i), dataSet.get(i));
        }
    }

    /**
     * Clears down any special notices, then returns to the home page.
     */
    private void clearDownSpecialNotices() {
        logger.debug("Special notices need clearing down");

        driverWrapper.clickLink("Read and acknowledge");
        driverWrapper.checkCurrentPageTitle("Special Notices");

        // note that the label has two spaces - View--and!
        while (driverWrapper.hasLink(
                "span", "View  and acknowledge", "../../")) {
            logger.debug("Special notice found, acknowledging");

            driverWrapper.clickLink(
                    "span", "View  and acknowledge", "../../");
            driverWrapper.pressFirstButton("Acknowledge");
        }
        driverWrapper.clickLink("Back");

        // back on "Your home"
        driverWrapper.checkCurrentPageTitle("Your home");

        // check special notices warning has gone
        assertFalse("Expected special notices to be cleared down",
                driverWrapper.hasLink("Read and acknowledge"));
    }
}
