package com.skps9.packai.logic;

/**
 * Runnable check: {@code <!--packai:items=…-->} survives scrub / damaged form still strips.
 * Payload asserted via {@link ItemResolver#firstMarkerPayload} (no MC bootstrap).
 */
public final class AskMarkerIntegrityCheck {
    private AskMarkerIntegrityCheck() {}

    public static void main(String[] args) {
        String intact = "<!--packai:items=minecraft:dirt|Dirt Block-->";
        String damaged = "<!-packai:items=minecraft:dirt|Dirt Block->";
        String payload = "minecraft:dirt|Dirt Block";

        // 1. Marker before 【来源】 → scrub strips; payload still readable from raw
        String beforeSrc = "推薦用泥土" + intact + "\n\n【来源】JEI";
        String scrubBefore = AskReplyScrub.scrubPromptEcho(beforeSrc);
        assert !scrubBefore.toLowerCase().contains("packai:items") : scrubBefore;
        assert payload.equals(ItemResolver.firstMarkerPayload(beforeSrc))
                : ItemResolver.firstMarkerPayload(beforeSrc);

        // 2. Marker after 【来源】
        String afterSrc = "推薦用泥土\n\n【来源】JEI\n" + intact;
        String scrubAfter = AskReplyScrub.scrubPromptEcho(afterSrc);
        assert !scrubAfter.toLowerCase().contains("packai:items") : scrubAfter;
        assert payload.equals(ItemResolver.firstMarkerPayload(afterSrc));

        // 3. Already-damaged form → strip + payload
        String damagedBody = "見" + damaged + "完";
        assert ItemResolver.stripMarker(damagedBody).equals("見完") : ItemResolver.stripMarker(damagedBody);
        assert payload.equals(ItemResolver.firstMarkerPayload(damagedBody));
        assert !AskReplyScrub.scrubPromptEcho(damagedBody).toLowerCase().contains("packai:items");

        // 4. Negative control — must NOT strip lookalikes
        String neg1 = "keep <!x-packai:items=A--> here";
        String neg2 = "keep <!-- notpackai:items=A --> here";
        assert AskReplyScrub.scrubPromptEcho(neg1).contains("<!x-packai:items=A-->")
                : AskReplyScrub.scrubPromptEcho(neg1);
        assert AskReplyScrub.scrubPromptEcho(neg2).contains("<!-- notpackai:items=A -->")
                : AskReplyScrub.scrubPromptEcho(neg2);
        assert ItemResolver.stripMarker(neg1).contains("<!x-packai:items=A-->");
        assert ItemResolver.stripMarker(neg2).contains("<!-- notpackai:items=A -->");
        assert ItemResolver.firstMarkerPayload(neg1) == null;
        assert ItemResolver.firstMarkerPayload(neg2) == null;

        // 5. Dup seps still collapse; ASCII -- in prose kept (L3)
        String dups = AskReplyScrub.scrubPromptEcho("甲、、乙，，丙");
        assert dups.contains("甲、乙，丙") || dups.contains("甲、乙") : dups;
        String dashes = AskReplyScrub.scrubPromptEcho("range a--b end");
        assert dashes.contains("a--b") : dashes;

        System.out.println("AskMarkerIntegrityCheck OK");
    }
}
