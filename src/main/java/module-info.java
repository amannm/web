module web.main {
    requires transitive info.picocli;
    requires transitive jakarta.json;
    requires transitive java.net.http;

    opens com.amannmalik.web.cli to info.picocli;
    opens com.amannmalik.web to info.picocli;
}
