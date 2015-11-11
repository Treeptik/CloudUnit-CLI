/*
 * LICENCE : CloudUnit is available under the GNU Affero General Public License : https://gnu.org/licenses/agpl.html
 *     but CloudUnit is licensed too under a standard commercial license.
 *     Please contact our sales team if you would like to discuss the specifics of our Enterprise license.
 *     If you are not sure whether the GPL is right for you,
 *     you can always test our software under the GPL and inspect the source code before you contact us
 *     about purchasing a commercial license.
 *
 *     LEGAL TERMS : "CloudUnit" is a registered trademark of Treeptik and can't be used to endorse
 *     or promote products derived from this project without prior written permission from Treeptik.
 *     Products or services derived from this software may not be called "CloudUnit"
 *     nor may "Treeptik" or similar confusing terms appear in their names without prior written permission.
 *     For any questions, contact us : contact@treeptik.fr
 */

package fr.treeptik.cloudunit.cli.utils;

import fr.treeptik.cloudunit.cli.commands.ShellStatusCommand;
import fr.treeptik.cloudunit.cli.exception.ManagerResponseException;
import fr.treeptik.cloudunit.cli.model.Application;
import fr.treeptik.cloudunit.cli.processor.InjectLogger;
import fr.treeptik.cloudunit.cli.rest.JsonConverter;
import fr.treeptik.cloudunit.cli.rest.RestUtils;
import fr.treeptik.cloudunit.cli.shell.CloudUnitPromptProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class ApplicationUtils {

    @InjectLogger
    private Logger log;

    @Autowired
    private UrlLoader urlLoader;

    @Autowired
    private AuthentificationUtils authentificationUtils;

    @Autowired
    private ShellStatusCommand statusCommand;

    @Autowired
    private RestUtils restUtils;

    @Autowired
    private CheckUtils checkUtils;

    @Autowired
    private ModuleUtils moduleUtils;

    @Autowired
    private CloudUnitPromptProvider clPromptProvider;

    @Autowired
    private FileUtils fileUtils;

    private Application application;

    private Integer loop = 0;

    public String getInformations() {
        String checkResponse = checkAndRejectIfError(null);
        if (checkResponse != null) {
            return checkResponse;
        }

        useApplication(application.getName());
        String dockerManagerIP = application.getManagerIp();
        statusCommand.setExitStatut(0);

        MessageConverter.buildApplicationMessage(application,
                dockerManagerIP);
        return "Terminated";
    }

    public String useApplication(String applicationName) {
        String json = null;

        if (authentificationUtils.getMap().isEmpty()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are not connected to CloudUnit host! Please use connect command"
                    + ANSIConstants.ANSI_RESET;
        }

        if (fileUtils.isInFileExplorer()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are currently in a container file explorer. Please exit it with close-explorer command"
                    + ANSIConstants.ANSI_RESET;
        }

        try {
            json = restUtils.sendGetCommand(
                    authentificationUtils.finalHost
                            + urlLoader.actionApplication + applicationName,
                    authentificationUtils.getMap()).get("body");
        } catch (ManagerResponseException e) {
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }
        statusCommand.setExitStatut(0);

        moduleUtils.setApplicationName(applicationName);
        setApplication(JsonConverter.getApplication(json));
        clPromptProvider.setPrompt("cloudunit-" + applicationName + "> ");
        return "Current application : " + getApplication().getName();
    }

    public String createApp(String applicationName, String serverName) {
        String response = null;
        if (authentificationUtils.getMap().isEmpty()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are not connected to CloudUnit host! Please use connect command"
                    + ANSIConstants.ANSI_RESET;
        }

        if (fileUtils.isInFileExplorer()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are currently in a container file explorer. Please exit it with close-explorer command"
                    + ANSIConstants.ANSI_RESET;
        }


        try {
            if (checkUtils.checkImageNoExist(serverName)) {
                statusCommand.setExitStatut(1);
                return ANSIConstants.ANSI_RED
                        + "This server image does not exist"
                        + ANSIConstants.ANSI_RESET;
            }
            Map<String, String> parameters = new HashMap<>();
            parameters.put("applicationName", applicationName);
            parameters.put("serverName", serverName);

            restUtils.sendPostCommand(
                    authentificationUtils.finalHost
                            + urlLoader.actionApplication,
                    authentificationUtils.getMap(), parameters).get("body");

            statusCommand.setExitStatut(0);

            response = "Your application " + applicationName
                    + " is currently being installed";


        } catch (ManagerResponseException e) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }

        useApplication(applicationName);

        return response;
    }

    public String rmApp(String applicationName, Boolean scriptUsage) {

        String response = null;
        String confirmation = "";

        if (loop == 0) {
            String checkResponse = checkAndRejectIfError(applicationName);
            if (checkResponse != null) {
                return checkResponse;
            }
            if (application == null) {

                statusCommand.setExitStatut(1);
                return ANSIConstants.ANSI_RED
                        + "No application is currently selected by the following command line : use <application name>"
                        + ANSIConstants.ANSI_RESET;
            }
        }

        // Enter the non interactive mode (for script)
        if (scriptUsage) {
            try {
                restUtils.sendDeleteCommand(
                        authentificationUtils.finalHost
                                + urlLoader.actionApplication
                                + application.getName(),
                        authentificationUtils.getMap()).get("body");
            } catch (ManagerResponseException e) {
                statusCommand.setExitStatut(1);
                return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
            }
            response = "Your application " + application.getName()
                    + " is currently being removed";
            resetPrompt();
            statusCommand.setExitStatut(0);
            setApplication(null);
            return response;
        }

        // for human users

        if (loop <= 3) {
            loop++;
            Scanner scanner = new Scanner(System.in);
            log.log(Level.WARNING,
                    "Confirm the suppression of your application : "
                            + application.getName()
                            + " - (yes/y or no/n)");
            confirmation = scanner.nextLine();
            try {

                switch (confirmation.toLowerCase()) {
                    case "yes":
                    case "y":
                        restUtils.sendDeleteCommand(
                                authentificationUtils.finalHost
                                        + urlLoader.actionApplication
                                        + application.getName(),
                                authentificationUtils.getMap()).get("body");

                        response = "Your application "
                                + application.getName()
                                + " is currently being removed";
                        resetPrompt();
                        statusCommand.setExitStatut(0);
                        break;

                    case "no":
                    case "n":
                        setApplication(null);
                        resetPrompt();
                        statusCommand.setExitStatut(0);

                        break;

                    default:

                        if (loop >= 3) {
                            setApplication(null);
                            resetPrompt();
                            loop = 0;
                            scanner.close();
                            return ANSIConstants.ANSI_PURPLE +
                                    "sorry 3 tries is the limit, you seem too tired to take a decision so important as delete an application, take a break !!!"
                                    + ANSIConstants.ANSI_RESET;
                        }
                        log.log(Level.SEVERE,
                                "confirmation response are yes/y or no/n ");
                        scanner.close();
                        return rmApp(applicationName, scriptUsage);
                }
            } catch (ResourceAccessException e) {
                statusCommand.setExitStatut(1);
                scanner.close();
                return ANSIConstants.ANSI_RED +
                        "The CLI can't etablished connexion with host servers. Please try later or contact an admin"
                        + ANSIConstants.ANSI_RESET;
            } catch (ManagerResponseException e) {
                statusCommand.setExitStatut(1);
                scanner.close();
                return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
            }

            setApplication(null);
            scanner.close();
            loop = 0;
        }

        return response;

    }

    public String startApp(String applicationName) {
        String response = null;

        String checkResponse = checkAndRejectIfError(applicationName);
        if (checkResponse != null) {
            return checkResponse;
        }

        try {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("applicationName", application.getName());
            restUtils.sendPostCommand(
                    authentificationUtils.finalHost
                            + urlLoader.actionApplication + urlLoader.start,
                    authentificationUtils.getMap(), parameters).get("body");
            response = "Your application " + application.getName().toLowerCase()
                    + " is currently being started";
            statusCommand.setExitStatut(0);

        } catch (ManagerResponseException e) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }

        return response;
    }

    public String stopApp(String applicationName) {

        String response = null;

        String checkResponse = checkAndRejectIfError(applicationName);
        if (checkResponse != null) {
            return checkResponse;
        }


        Map<String, String> parameters = new HashMap<>();
        parameters.put("applicationName", application.getName());

        try {
            restUtils.sendPostCommand(
                    authentificationUtils.finalHost
                            + urlLoader.actionApplication + urlLoader.stop,
                    authentificationUtils.getMap(), parameters).get("body");
        } catch (ManagerResponseException e) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }
        response = "Your application " + application.getName().toLowerCase()
                + " is currently being stopped";
        statusCommand.setExitStatut(0);


        return response;
    }

    public List<Application> listAllApps() throws ManagerResponseException {
        List<Application> listApplications;
        String json = null;

        try {
            json = (String) restUtils.sendGetCommand(
                    authentificationUtils.finalHost
                            + urlLoader.listAllApplications,
                    authentificationUtils.getMap()).get("body");
        } catch (ManagerResponseException e) {
            throw new ManagerResponseException(e.getMessage(), e);
        }

        listApplications = JsonConverter.getApplications(json);
        statusCommand.setExitStatut(0);
        return listApplications;
    }

    public String listAll() {
        if (authentificationUtils.getMap().isEmpty()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are not connected to CloudUnit host! Please use connect command"
                    + ANSIConstants.ANSI_RESET;
        }

        if (fileUtils.isInFileExplorer()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are currently in a container file explorer. Please exit it with close-explorer command"
                    + ANSIConstants.ANSI_RESET;
        }

        List<Application> listApplications = null;
        try {
            listApplications = listAllApps();
        } catch (ManagerResponseException e) {
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }
        if (listApplications != null) {
            MessageConverter.buildListApplications(listApplications);
        }
        return listApplications.size() + " found !";
    }

    public String deployFromAWar(File path, boolean openBrowser)
            throws MalformedURLException, URISyntaxException {

        String checkResponse = checkAndRejectIfError(null);
        String body = "";
        if (checkResponse != null) {
            return checkResponse;
        }

        if (path == null) {

            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You must precise the file path with -p option"
                    + ANSIConstants.ANSI_RESET;

        } else {
            // refresh application informations
            useApplication(application.getName());

            try {
                File file = path;
                FileInputStream fileInputStream = new FileInputStream(file);
                fileInputStream.available();
                fileInputStream.close();
                FileSystemResource resource = new FileSystemResource(file);
                Map<String, Object> params = new HashMap<>();
                params.put("file", resource);
                params.putAll(authentificationUtils.getMap());
                body = (String) restUtils.sendPostForUpload(
                        authentificationUtils.finalHost
                                + urlLoader.actionApplication
                                + application.getName() + "/deploy",
                        params).get("body");
                statusCommand.setExitStatut(0);


            } catch (IOException e) {

                return ANSIConstants.ANSI_RED
                        + "File not found! Check the path file"
                        + ANSIConstants.ANSI_RESET;
            }
        }

        if (!body.equalsIgnoreCase("") && openBrowser) {
            DesktopAPI.browse(new URL(application.getLocation())
                    .toURI());
        }

        return "War deployed - Access on "
                + application.getLocation();
    }

    public String addNewAlias(String applicationName, String alias) {
        String response = null;

        String checkResponse = checkAndRejectIfError(applicationName);
        if (checkResponse != null) {
            return checkResponse;
        }


        Map<String, String> parameters = new HashMap<>();
        parameters.put("applicationName", application.getName());
        parameters.put("alias", alias);
        try {
            restUtils.sendPostCommand(
                    authentificationUtils.finalHost
                            + urlLoader.actionApplication + "/alias",
                    authentificationUtils.getMap(), parameters).get("body");
        } catch (ManagerResponseException e) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }
        statusCommand.setExitStatut(0);
        response = "An alias has been successfully added to " + application.getName();


        return response;
    }

    public String listAllAliases(String applicationName) {
        String response = null;

        String checkResponse = checkAndRejectIfError(applicationName);
        if (checkResponse != null) {
            return checkResponse;
        }


        try {
            response = restUtils.sendGetCommand(
                    authentificationUtils.finalHost
                            + urlLoader.actionApplication + application.getName()
                            + "/alias", authentificationUtils.getMap()).get(
                    "body");
        } catch (ManagerResponseException e) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }

        MessageConverter.buildListAliases(JsonConverter
                .getAliases(response));

        statusCommand.setExitStatut(0);


        return JsonConverter
                .getAliases(response).size() + " aliases found!";
    }

    public String removeAlias(String applicationName, String alias) {

        String checkResponse = checkAndRejectIfError(applicationName);
        if (checkResponse != null) {
            return checkResponse;
        }

        try {

            restUtils
                    .sendDeleteCommand(
                            authentificationUtils.finalHost
                                    + urlLoader.actionApplication
                                    + application.getName() + "/alias/" + alias,
                            authentificationUtils.getMap()).get("body");
        } catch (ManagerResponseException e) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED + e.getMessage() + ANSIConstants.ANSI_RESET;
        }

        statusCommand.setExitStatut(0);


        return "This alias has successful been deleted";
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public void resetPrompt() {
        clPromptProvider.setPrompt("cloudunit> ");
    }

    public String checkAndRejectIfError(String applicationName) {
        if (authentificationUtils.getMap().isEmpty()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are not connected to CloudUnit host! Please use connect command"
                    + ANSIConstants.ANSI_RESET;
        }

        if (fileUtils.isInFileExplorer()) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "You are currently in a container file explorer. Please exit it with close-explorer command"
                    + ANSIConstants.ANSI_RESET;
        }

        if (application == null && applicationName == null) {
            statusCommand.setExitStatut(1);
            return ANSIConstants.ANSI_RED
                    + "No application is currently selected by the following command line : use <application name>"
                    + ANSIConstants.ANSI_RESET;

        }
        String result = "";
        if (applicationName != null) {
            log.log(Level.INFO, applicationName);
            result = useApplication(applicationName);
            if (result.contains("This application does not exist on this account")) {
                return result;
            }
        }

        return null;
    }
}
