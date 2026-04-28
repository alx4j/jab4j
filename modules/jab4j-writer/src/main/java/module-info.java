module com.alx4j.jab4j.writer {
    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires org.slf4j;
    requires transitive com.alx4j.jab4j.core;
    requires transitive com.alx4j.jab4j.player;

    exports com.alx4j.jab4j.writer.config;
    exports com.alx4j.jab4j.writer;
    exports com.alx4j.jab4j.writer.app;

    opens com.alx4j.jab4j.writer.config to com.fasterxml.jackson.databind;
}
