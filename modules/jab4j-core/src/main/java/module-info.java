module pro.alx4j.jab4j.core {
    requires java.desktop;
    requires org.slf4j;
    requires transitive pro.alx4j.jab4j.api;

    exports pro.alx4j.jab4j.output;
    exports pro.alx4j.jab4j.render.frame;
    exports pro.alx4j.jab4j.render.layout;
    exports pro.alx4j.jab4j.catalog;
    exports pro.alx4j.jab4j.tile;
    exports pro.alx4j.jab4j.render.tile;
    exports pro.alx4j.jab4j.transfer;

}
