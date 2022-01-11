package org.testcontainsers.examples;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.lifecycle.TestDescription;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertEquals;
import static org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL;

public class Stepdefs {

    private final BrowserWebDriverContainer<?> container = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4")
            .withCapabilities(new ChromeOptions())
            .withRecordingMode(RECORD_ALL, new File("build"));

    private String location;
    private String answer;

    @Before
    public void beforeScenario() {
        container.start();
    }

    @After
    public void afterScenario(Scenario scenario) {
        container.afterTest(new TestDescription() {
            @Override
            public String getTestId() {
                return scenario.getId();
            }

            @Override
            public String getFilesystemFriendlyName() {
                return scenario.getName();
            }
        }, Optional.of(scenario).filter(Scenario::isFailed).map(__ -> new RuntimeException()));
    }

    @Given("^location is \"([^\"]*)\"$")
    public void locationIs(String location) {
        this.location = location;
    }

    @When("^I ask is it possible to search here$")
    public void iAskIsItPossibleToSearchHere() {
        RemoteWebDriver driver = new RemoteWebDriver(container.getSeleniumAddress(), new ChromeOptions());
        driver.get(location);
        List<WebElement> searchInputs = driver.findElements(By.tagName("input"));
        answer = searchInputs != null && searchInputs.size() > 0 ? "YES" : "NOPE";
    }

    @Then("^I should be told \"([^\"]*)\"$")
    public void iShouldBeTold(String expected) {
        assertEquals(expected, answer);
    }


}
