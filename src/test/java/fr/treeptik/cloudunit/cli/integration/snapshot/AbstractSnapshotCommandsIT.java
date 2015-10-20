package fr.treeptik.cloudunit.cli.integration.snapshot;

import fr.treeptik.cloudunit.cli.integration.AbstractShellIntegrationTest;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.shell.core.CommandResult;

import java.util.Random;

/**
 * Created by guillaume on 16/10/15.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractSnapshotCommandsIT extends AbstractShellIntegrationTest {

    private static String applicationName;
    protected String serverType;
    private String tagName = "myTag";

    @BeforeClass
    public static void generateApplication() {
        applicationName = "App" + new Random().nextInt(10000);
    }

    @Test
    public void test00_shouldSnapshotAnApp() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("create-app --name " + applicationName + " --type " + serverType);
        cr = getShell().executeCommand("use " + applicationName);
        cr = getShell().executeCommand("create-snapshot --tag " + tagName);
        String result = cr.getResult().toString();
        String expectedResult = "A new snapshot called " + tagName + " was successfully created.";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void test01_shouldNotSnapshotAnAppBecauseUserIsNotLogged() {
        CommandResult cr = getShell().executeCommand("use " + applicationName);
        cr = getShell().executeCommand("create-snapshot --tag " + tagName);
        String result = cr.getResult().toString();
        String expectedResult = "You are not connected to CloudUnit host! Please use connect command";
        Assert.assertTrue(result.contains(expectedResult));
    }

    @Test
    public void test02_shouldNotSnapshotAnAppBecauseApplicationNotSelected() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("create-snapshot --tag " + tagName);
        String result = cr.getResult().toString();
        String expectedResult = "No application is currently selected by the following command line : use <application name>";
        Assert.assertTrue(result.contains(expectedResult));
    }

    @Test
    public void test03_shouldNotSnapshotAnAppBecauseTagNameAlreadyExists() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("use " + applicationName);
        cr = getShell().executeCommand("create-snapshot --tag " + tagName);
        String result = cr.getResult().toString();
        String expectedResult = "this tag already exists";
        Assert.assertTrue(result.contains(expectedResult));
    }

    @Test
    public void test10_shouldCloneAnApp() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("clone --tag " + tagName + " --applicationName " + applicationName + "cloned");
        String result = cr.getResult().toString();
        String expectedResult = "Your application " + applicationName + "cloned was successfully created.";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void test11_shouldNotCloneAnAppBecauseTagNameDoesNotExist() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("clone --tag " + tagName + "1 --applicationName " + applicationName + "cloned2");
        String result = cr.getResult().toString();
        String expectedResult = "This tag does not exist yet";
        Assert.assertTrue(result.contains(expectedResult));
    }

    @Test
    public void test12_shouldNotCloneAnAppBecauseUserIsNotLogged() {
        CommandResult cr = getShell().executeCommand("clone --tag " + tagName + "1 --applicationName " + applicationName + "cloned2");
        String result = cr.getResult().toString();
        String expectedResult = "You are not connected to CloudUnit host! Please use connect command";
        Assert.assertTrue(result.contains(expectedResult));
    }

    @Test
    public void test20_shouldListSnapshots() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("list-snapshot");
        String result = cr.getResult().toString();
        String expectedResult = "1 snapshots found";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void test21_shouldNotListSnapshotsBecauseUserIsNotLogged() {
        CommandResult cr = getShell().executeCommand("list-snapshot");
        String result = cr.getResult().toString();
        String expectedResult = "You are not connected to CloudUnit host! Please use connect command";
        Assert.assertTrue(result.contains(expectedResult));
    }

    @Test
    public void test30_shouldRemoveSnapshot() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("rm-snapshot --tag " + tagName);
        String result = cr.getResult().toString();
        String expectedResult = "The snapshot myTag was successfully deleted.";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void test90_cleanEnv() {
        CommandResult cr = getShell().executeCommand("connect --login johndoe --password abc2015");
        cr = getShell().executeCommand("rm-app --name " + applicationName + " --scriptUsage");
        String result = cr.getResult().toString();
        String expectedResult = "Your application " + applicationName.toLowerCase() + " is currently being removed";
        Assert.assertEquals(expectedResult, result);
    }


}
