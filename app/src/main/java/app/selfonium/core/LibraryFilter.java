package app.selfonium.core;

/** Presentation grouping only: stored source IDs and offline status stay independent. */
public final class LibraryFilter {
    public static final String LOCAL="@local-files";
    private LibraryFilter(){}
    public static boolean local(String source){return "device".equals(source)||"files".equals(source);}
    public static String normalize(String filter){return filter==null?"":local(filter)?LOCAL:filter;}
    public static boolean matches(String filter,String source){String value=normalize(filter);return value.isEmpty()||value.equals(LOCAL)&&local(source)||value.equals(source);}
}
