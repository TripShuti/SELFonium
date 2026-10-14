package app.selfonium.core;

public final class LanguagePolicyTest {
    private static int checks;
    private static void expect(String wanted,String override,String...system){checks++;String actual=LanguagePolicy.choose(override,system);if(!wanted.equals(actual))throw new AssertionError(wanted+" != "+actual);}
    public static void main(String[] args){
        expect("en","");expect("uk-UA","","uk-UA","en-US");expect("pl-PL","","ja-JP","pl-PL","de-DE");
        expect("fr","fr","uk-UA");expect("es-MX","es-MX","en");expect("en","unsupported","ja-JP");
        expect("de-DE","","de-DE");expect("en-US",null,null,"en-US");expect("en","","und","xx");
        expect("de","de","fr");expect("en","en","uk");expect("uk","uk","en");
        for(String language:LanguagePolicy.TAGS)expect(language,language,"ja");
        System.out.println("PASS: "+checks+" language selection checks");
    }
}
