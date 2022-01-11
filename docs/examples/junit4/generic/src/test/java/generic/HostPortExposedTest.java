package generic;

import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;

import static org.junit.Assert.assertTrue;

public class HostPortExposedTest {

    private static HttpServer server;
    private static int localServerPort;

    @BeforeClass
    public static void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] content = "Hello World!".getBytes();
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(content);
                responseBody.flush();
            }
        });

        server.start();
        localServerPort = server.getAddress().getPort();

        // exposePort {
        Testcontainers.exposeHostPorts(localServerPort);
        // }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stop(0);
    }

    @Rule
    public BrowserWebDriverContainer<?> browser = new BrowserWebDriverContainer<>("selenium/standalone-chrome:4")
        .withCapabilities(new ChromeOptions());

    @Test
    public void testContainerRunningAgainstExposedHostPort() {
        // useHostExposedPort {
        final String rootUrl =
            String.format("http://host.testcontainers.internal:%d/", localServerPort);

        URL seleniumAddress = browser.getSeleniumAddress();
        RemoteWebDriver webDriver = new RemoteWebDriver(seleniumAddress, new ChromeOptions());
        webDriver.get(rootUrl);
        // }

        final String pageSource = webDriver.getPageSource();
        assertTrue(pageSource.contains("Hello World!"));
    }
}
