module com.alx4j.jab4j.core {
    requires java.desktop;
    requires org.slf4j;
    requires transitive com.alx4j.jab4j.api;

    exports com.alx4j.jab4j.output;
    exports com.alx4j.jab4j.render.frame;
    exports com.alx4j.jab4j.render.layout;
    exports com.alx4j.jab4j.catalog;
    exports com.alx4j.jab4j.tile;
    exports com.alx4j.jab4j.render.tile;
    exports com.alx4j.jab4j.transfer;

}
