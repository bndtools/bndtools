package bndtools.preferences;

public enum BndWorkSpaceLocationMode {
    STRICT("permit only project sibling of cnf"),

    BND("permit use of cnf file to redirect"),

    ECLIPSE("work with eclipse opened project");

    String desc;

    private BndWorkSpaceLocationMode(String desc) {
        this.desc = desc;
    }
}
