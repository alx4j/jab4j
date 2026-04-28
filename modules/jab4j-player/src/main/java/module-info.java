module com.alx4j.jab4j.player {
    requires java.desktop;
    requires org.slf4j;
    requires transitive com.alx4j.jab4j.core;

    exports com.alx4j.jab4j.player;
    exports com.alx4j.jab4j.player.core;
}
