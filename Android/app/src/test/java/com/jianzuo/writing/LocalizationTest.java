package com.jianzuo.writing;

import org.junit.Test;
import java.util.*;
import java.nio.file.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.util.regex.*;
import static org.junit.Assert.*;

public class LocalizationTest {
    @Test public void languagePolicyUsesSimplifiedChineseAndEnglishFallback() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.resolve("system", Locale.forLanguageTag("zh-Hant-TW")));
        assertEquals(Locale.ENGLISH, AppLanguage.resolve("system", Locale.FRENCH));
        assertEquals(Locale.ENGLISH, AppLanguage.resolve("en", Locale.SIMPLIFIED_CHINESE));
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.resolve("zh-CN", Locale.ENGLISH));
        assertEquals("system", AppLanguage.normalize("corrupt"));
    }
    private Map<String, List<String>> strings(String folder) throws Exception {
        var dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("src/main/res", folder, "strings.xml").toFile());
        Map<String, List<String>> result = new TreeMap<>();
        NodeList nodes = dom.getDocumentElement().getChildNodes();
        for (int i=0;i<nodes.getLength();i++) if (nodes.item(i) instanceof Element e) {
            List<String> values = new ArrayList<>();
            if (e.getTagName().equals("string")) values.add(e.getTextContent());
            else { NodeList items = e.getElementsByTagName("item"); for (int k=0;k<items.getLength();k++) values.add(items.item(k).getTextContent()); }
            assertNull("Duplicate resource: "+e.getAttribute("name"), result.put(e.getAttribute("name"), values));
        }
        return result;
    }
    private List<String> placeholders(String value) {
        Matcher m = Pattern.compile("%[1-9]\\d*\\$[dsf]").matcher(value); List<String> tokens = new ArrayList<>(); while(m.find()) tokens.add(m.group()); Collections.sort(tokens); return tokens;
    }
    @Test public void bothLanguagesCoverEveryResourceAndKeepFormatArguments() throws Exception {
        var en=strings("values"); var zh=strings("values-zh-rCN"); assertEquals(en.keySet(),zh.keySet());
        for(String name:en.keySet()) {
            assertEquals(name, en.get(name).size(), zh.get(name).size());
            for(int i=0;i<en.get(name).size();i++) assertEquals(name, placeholders(en.get(name).get(i)), placeholders(zh.get(name).get(i)));
        }
    }
    @Test public void englishResourcesContainNoChineseTextExceptNativeLanguageOption() throws Exception {
        for(var e:strings("values").entrySet()) for(String value:e.getValue()) {
            assertFalse(e.getKey()+" is blank", value.equals("\"\""));
            if (!e.getKey().equals("language_options")) assertFalse(e.getKey()+": "+value, value.codePoints().anyMatch(c -> Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN));
        }
    }
    @Test public void domainErrorsKeepTypeDiagnosticAndCause() {
        Exception cause=new Exception("disk"); var io=new UserError.Io("key", "原始诊断", cause);
        assertTrue(io instanceof java.io.IOException); assertEquals("原始诊断",io.getMessage()); assertSame(cause,io.getCause()); assertEquals("key",io.key());
        assertTrue(new UserError.Cancelled("key","取消") instanceof java.io.InterruptedIOException);
    }
}
