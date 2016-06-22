package fr.treeptik.cloudunit.cli.integration.files;

import fr.treeptik.cloudunit.cli.integration.AbstractShellIntegrationTest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.shell.core.CommandResult;

import java.util.Random;

/**
 * Created by Nicolas
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FileCommandsIT extends AbstractShellIntegrationTest {

    private static String applicationName;
    protected String serverType = "tomcat-8";

    @BeforeClass
    public static void generateApplication() {
        applicationName = "app" + new Random().nextInt(10000);
    }

    @Test
    public void test_enter_into_container() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015 ");
        cr = getShell().executeCommand("create-app --name " + applicationName + " --type " + serverType);
        Assert.assertTrue("Create Application", cr.isSuccess());

        cr = getShell().executeCommand("use " + applicationName);
        Assert.assertTrue("Use Application", cr.isSuccess());

        cr = getShell().executeCommand("open-explorer --containerName dev-johndoe-" + applicationName + "-tomcat-8");
        Assert.assertTrue("Entering into containerFS", cr.isSuccess());

        cr = getShell().executeCommand("upload-file --path " + "/Users/nicolas/software/cloudunit-cli/src/test/java/fr/treeptik/cloudunit/cli/integration/files/my-beautiful-file.txt");
        Assert.assertTrue("Entering into containerFS", cr.isSuccess());

    }
}
