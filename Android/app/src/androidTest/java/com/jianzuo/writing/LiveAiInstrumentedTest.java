package com.jianzuo.writing;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Explicit opt-in only. Credential is supplied through private cache stdin, never runner arguments. */
@RunWith(AndroidJUnit4.class)
public class LiveAiInstrumentedTest {
    @Test public void realDeepSeekRoundTripOnAndroid() throws Exception {
        Assume.assumeTrue("Live API must be explicitly enabled", "true".equals(InstrumentationRegistry.getArguments().getString("deepseek_live")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();File file=new File(context.getCacheDir(),"deepseek-live-key");
        try {
            String key=Files.readString(file.toPath()).trim(); assertTrue(file.delete());
            assertTrue(new DeepSeekClient().models(key).contains(DeepSeekClient.DEFAULT_MODEL));
            for(boolean chinese:new boolean[]{true,false}) {
                Document doc=new Document();doc.title=chinese?"雨夜的信":"The old station";
                doc.body=chinese?"雨一直没有停。林舟推开旧书店的门，看见柜台上放着一封没有署名的信。他认出了信封上的字迹，却不敢立刻拆开。":"Mara stood beside the empty platform.\n\nAt dawn, she would finally understand why the train had never arrived.";
                AiContinuation draft=new AiContinuation(doc,chinese?doc.body.length():doc.body.indexOf("\n\n"));
                var result=new DeepSeekClient().continueText(key,DeepSeekClient.DEFAULT_MODEL,draft,chinese?"续写一个自然段，保持悬念，不重复原文":"Continue in English with a short paragraph connecting to the next paragraph.",100);
                assertFalse(result.text().trim().isEmpty());assertFalse(result.truncated());assertTrue(result.inputTokens()>0);assertTrue(result.outputTokens()>0);
                assertTrue(result.text().startsWith("\n"));assertTrue(draft.apply(doc,result.text()));assertEquals(draft.body,doc.history.get(0).body());assertTrue(doc.body.endsWith(draft.body.substring(draft.cursor)));
                System.out.println("LIVE_AI "+(chinese?"zh":"en")+" inputTokens="+result.inputTokens()+" outputTokens="+result.outputTokens()+" truncated="+result.truncated()+" generated="+result.text());
            }
        } finally { Files.deleteIfExists(file.toPath()); }
    }
}
