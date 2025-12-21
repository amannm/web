module web {
    requires transitive info.picocli;
    requires transitive jakarta.json;
    requires transitive java.net.http;
    exports com.amannmalik.web;
    opens com.amannmalik.web to info.picocli;
}
