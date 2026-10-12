package com.jianzuo.writing;

import org.json.*;
import org.junit.Test;
import java.net.SocketTimeoutException;
import java.io.IOException;
import static org.junit.Assert.*;

public class DeepSeekTest {
    private static final String KEY = "sk-unit-test-placeholder-0000";
    private Document doc(String body) { Document d = new Document(); d.title = "示例"; d.body = body; return d; }
    private JSONObject response(String content, String reason) throws Exception {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", reason).put("message", new JSONObject().put("content", content))))
            .put("usage", new JSONObject().put("prompt_tokens", 12).put("completion_tokens", 8));
    }
    @Test public void insertionPreservesSuffixAndHistoryAndCannotApplyTwice() {
        Document d = doc("前🙂后"); AiContinuation draft = new AiContinuation(d, 3);
        assertTrue(draft.apply(d, "新段\n")); assertEquals("前🙂新段\n后", d.body); assertEquals("前🙂后", d.history.get(0).body()); assertFalse(draft.apply(d, "duplicate"));
    }
    @Test public void clearSentenceBoundariesGetSeparatorsButExistingWhitespaceIsKept() {
        assertEquals("\n\nNext.", new AiContinuation(doc("Done."),5).previewText("Next."));
        assertEquals("\nAlready separated", new AiContinuation(doc("完成。"),3).previewText("\nAlready separated"));
        assertEquals("new", new AiContinuation(doc("unfinished "),11).previewText("new"));
        assertEquals("new. ", new AiContinuation(doc("suffix"),0).previewText("new."));
    }
    @Test public void staleAndUnavailableAnchorsNeverMutate() {
        Document d = doc("原文"); AiContinuation draft = new AiContinuation(d, 2); d.body += "编辑";
        assertFalse(draft.apply(d, "生成")); assertEquals("原文编辑", d.body); d.body = "原文"; d.readOnly = true; assertFalse(draft.apply(d, "生成"));
        d.readOnly = false; d.deletedAt = 1L; assertFalse(draft.apply(d, "生成")); assertFalse(draft.apply(null, "生成"));
        d.deletedAt = null; d.title = "改变标题"; assertFalse(draft.apply(d, "生成")); assertTrue(d.history.isEmpty());
    }
    @Test public void emptyResultCannotCreateRevision() { Document d = doc("原文"); assertFalse(new AiContinuation(d, 2).apply(d, " \n")); assertTrue(d.history.isEmpty()); }
    @Test public void cursorNeverSplitsSupplementaryCharacters() { Document d = doc("a🙂b"); AiContinuation draft = new AiContinuation(d, 2); assertEquals(1, draft.cursor); assertTrue(draft.apply(d, "X")); assertEquals("aX🙂b", d.body); }
    @Test public void contextIsBoundedAndUnicodeSafeAndKeepsSuffix() throws Exception {
        Document d = doc("🙂".repeat(10000)); d.title = "🙂".repeat(300);
        JSONObject c = new AiContinuation(d, 15001).context("方向", 300);
        assertTrue(c.getString("before_cursor").length() <= 12001); assertTrue(c.getString("after_cursor").length() <= 2000); assertEquals(300, c.getString("title").length());
        assertTrue(c.getBoolean("earlier_text_omitted")); assertTrue(c.getBoolean("later_text_omitted"));
        assertFalse(Character.isLowSurrogate(c.getString("before_cursor").charAt(0)));
    }
    @Test public void payloadSeparatesInstructionsFromDataAndDisablesThinking() throws Exception {
        JSONObject p = DeepSeekClient.payload(new AiContinuation(doc("Keep English"), 4), "deepseek-flash", "keep a calm tone", 300);
        assertEquals("disabled", p.getJSONObject("thinking").getString("type")); assertFalse(p.getBoolean("stream")); assertEquals(768, p.getInt("max_tokens"));
        JSONObject user = new JSONObject(p.getJSONArray("messages").getJSONObject(1).getString("content")); assertEquals("Keep", user.getString("before_cursor")); assertEquals(" English", user.getString("after_cursor"));
        assertFalse(p.toString().contains(KEY));
    }
    @Test public void emptyDocumentRequiresDirection() throws Exception {
        var draft = new AiContinuation(doc(""), 0);
        assertThrows(DeepSeekClient.Error.class, () -> DeepSeekClient.payload(draft, "deepseek-flash", "", 300));
        assertNotNull(DeepSeekClient.payload(draft, "deepseek-flash", "Write a diary entry", 300));
    }
    @Test public void invalidModelAndHeaderInjectionAreRejected() {
        assertThrows(DeepSeekClient.Error.class, () -> DeepSeekClient.key(KEY + "\r\nX: bad"));
        assertThrows(DeepSeekClient.Error.class, () -> DeepSeekClient.payload(new AiContinuation(doc("x"), 1), "evil-model", "", 300));
    }
    @Test public void stopAndLengthReturnEditableTextWithoutStrippingSpaces() throws Exception {
        var result = DeepSeekClient.parse(response(" continuation\n", "stop")); assertEquals(" continuation\n", result.text()); assertFalse(result.truncated()); assertEquals(12, result.inputTokens());
        assertTrue(DeepSeekClient.parse(response("part", "length")).truncated());
    }
    @Test public void filteredAbortedAndReasoningOnlyResponsesAreRejected() throws Exception {
        for (String reason : new String[]{"content_filter", "aborted", "tool_calls", "insufficient_system_resource"}) assertThrows(DeepSeekClient.Error.class, () -> DeepSeekClient.parse(response("unsafe", reason)));
        assertThrows(DeepSeekClient.Error.class, () -> DeepSeekClient.parse(response("", "stop")));
        assertThrows(DeepSeekClient.Error.class, () -> DeepSeekClient.parse(new JSONObject("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null,\"reasoning_content\":\"thoughts\"}}]}")));
    }
    @Test public void providerStatusIsTypedAndNeverExposesResponseBody() {
        int[] statuses = {401,403,402,429,400,404,422,500,302};
        DeepSeekClient.Failure[] expected = {DeepSeekClient.Failure.KEY,DeepSeekClient.Failure.KEY,DeepSeekClient.Failure.BALANCE,DeepSeekClient.Failure.RATE,DeepSeekClient.Failure.INPUT,DeepSeekClient.Failure.INPUT,DeepSeekClient.Failure.INPUT,DeepSeekClient.Failure.SERVER,DeepSeekClient.Failure.SERVER};
        for(int i=0;i<statuses.length;i++) { int status=statuses[i]; var client=new DeepSeekClient((p,k,b)->new DeepSeekClient.Reply(status,KEY)); var error=assertThrows(DeepSeekClient.Error.class,()->client.models(KEY)); assertEquals(expected[i],error.failure); assertFalse(error.toString().contains(KEY)); }
    }
    @Test public void networkFailuresAndTimeoutsAreTyped() {
        var timeout = new DeepSeekClient((p,k,b)->{throw new SocketTimeoutException(KEY);}); assertEquals(DeepSeekClient.Failure.TIMEOUT,assertThrows(DeepSeekClient.Error.class,()->timeout.models(KEY)).failure);
        var offline = new DeepSeekClient((p,k,b)->{throw new IOException(KEY);}); assertEquals(DeepSeekClient.Failure.NETWORK,assertThrows(DeepSeekClient.Error.class,()->offline.models(KEY)).failure);
    }
    @Test public void cancellationRejectsLateSuccessfulResponse() {
        DeepSeekClient[] client=new DeepSeekClient[1]; client[0]=new DeepSeekClient((p,k,b)->{client[0].cancel();return new DeepSeekClient.Reply(200,"{\"data\":[{\"id\":\"deepseek-flash\"}]}");});
        assertEquals(DeepSeekClient.Failure.CANCELLED,assertThrows(DeepSeekClient.Error.class,()->client[0].models(KEY)).failure);
    }
    @Test public void modelsAreValidatedAndMalformedResponsesRejected() throws Exception {
        var client=new DeepSeekClient((p,k,b)->new DeepSeekClient.Reply(200,"{\"data\":[{\"id\":\"deepseek-flash\"},{\"id\":\"deepseek-flash\"},{\"id\":\"bad/path\"}]}")); assertEquals(java.util.List.of("deepseek-flash"),client.models(KEY));
        var bad=new DeepSeekClient((p,k,b)->new DeepSeekClient.Reply(200,"not JSON")); assertEquals(DeepSeekClient.Failure.RESPONSE,assertThrows(DeepSeekClient.Error.class,()->bad.models(KEY)).failure);
    }
}
