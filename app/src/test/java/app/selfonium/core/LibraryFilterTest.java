package app.selfonium.core;

/** Source grouping must not treat cached/downloaded server tracks as local music. */
public final class LibraryFilterTest {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Filter check "+checks);checks++;}
    public static void main(String[] args){
        check(LibraryFilter.matches(LibraryFilter.LOCAL,"device"));check(LibraryFilter.matches(LibraryFilter.LOCAL,"files"));
        check(!LibraryFilter.matches(LibraryFilter.LOCAL,"server-one"));check(!LibraryFilter.matches(LibraryFilter.LOCAL,"radio"));
        check(LibraryFilter.matches("server-one","server-one"));check(!LibraryFilter.matches("server-one","server-two"));
        check(!LibraryFilter.matches("server-one","device"));check(!LibraryFilter.matches("server-one","files"));
        check(LibraryFilter.matches("","device"));check(LibraryFilter.matches("","files"));check(LibraryFilter.matches("","server-one"));check(LibraryFilter.matches(null,"radio"));
        check(LibraryFilter.normalize("device").equals(LibraryFilter.LOCAL));check(LibraryFilter.normalize("files").equals(LibraryFilter.LOCAL));check(LibraryFilter.normalize("server-one").equals("server-one"));
        check(LibraryFilter.matches("device","files"));check(LibraryFilter.matches("files","device"));check(!LibraryFilter.matches("files","server-one"));
        System.out.println("PASS: "+checks+" library source filter checks");
    }
}
