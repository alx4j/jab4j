module pro.alx4j.jab4j.writer {
    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires org.slf4j;
    requires transitive pro.alx4j.jab4j.core;
    requires transitive pro.alx4j.jab4j.player;

    exports pro.alx4j.jab4j.writer.config;
    exports pro.alx4j.jab4j.writer;
    exports pro.alx4j.jab4j.writer.app;

    opens pro.alx4j.jab4j.writer.config to com.fasterxml.jackson.databind;
}
