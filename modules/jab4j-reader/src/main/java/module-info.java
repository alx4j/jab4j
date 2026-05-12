module com.alx4j.jab4j.reader {
    requires java.desktop;
    requires org.slf4j;
    requires transitive com.alx4j.jab4j.api;
    requires com.alx4j.jab4j.core;

    exports com.alx4j.jab4j.reader.app;
    exports com.alx4j.jab4j.reader.capture;
    exports com.alx4j.jab4j.reader.capture.media;
    exports com.alx4j.jab4j.reader.content;
    exports com.alx4j.jab4j.reader.frame;
    exports com.alx4j.jab4j.reader.restore;
}
