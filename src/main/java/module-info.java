module web.main {
    requires transitive info.picocli;
    requires transitive jakarta.json;
    requires transitive java.net.http;
    exports com.amannmalik.web.cli;
    exports com.amannmalik.web.webdriver.ffi;
    opens com.amannmalik.web.cli to info.picocli;
}
