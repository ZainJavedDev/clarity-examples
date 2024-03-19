package skadistats.clarity.examples.combatlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.wire.dota.common.proto.DOTAUserMessages;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private final Logger log = LoggerFactory.getLogger(Main.class.getPackage().getClass());

    private final DateTimeFormatter GAMETIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private String compileName(String attackerName, boolean isIllusion) {
        return attackerName != null ? attackerName + (isIllusion ? " (illusion)" : "") : "UNKNOWN";
    }

    private String getAttackerNameCompiled(CombatLogEntry cle) {
        return compileName(cle.getAttackerName(), cle.isAttackerIllusion());
    }

    private String getTargetNameCompiled(CombatLogEntry cle) {
        return compileName(cle.getTargetName(), cle.isTargetIllusion());
    }

    private Map<String, SpellInfo> spellInfoMap = new HashMap<>();

    private static class SpellInfo {
        List<String> casts = new ArrayList<>();
        List<Map<String, String>> targets = new ArrayList<>();

        @Override
        public String toString() {
        return "SpellInfo{casts=" + casts + ", targets=" + targets + "}";
        }
    }

    @OnCombatLogEntry
    public void onCombatLogEntry(CombatLogEntry cle) {
        switch (cle.getType()) {
            case DOTA_COMBATLOG_DAMAGE:
                if (getTargetNameCompiled(cle).contains("npc_dota_hero")) {

                    if ("earthshaker_echo_slam".equals(cle.getInflictorName())
                            || "tidehunter_ravage".equals(cle.getInflictorName())
                            || "magnataur_reverse_polarity".equals(cle.getInflictorName())) {

                        SpellInfo spellInfo = spellInfoMap.get(cle.getInflictorName());
                        if (spellInfo == null) {
                            spellInfo = new SpellInfo();
                            spellInfoMap.put(cle.getInflictorName(), spellInfo);
                        }

                        Map<String, String> target = new HashMap<>();
                        target.put(getTargetNameCompiled(cle), String.valueOf(cle.getTimestamp()));
                        spellInfo.targets.add(target);

                        log.info("{} {} hits {}{} for {} damage{}",
                                cle.getTimestamp(),
                                getAttackerNameCompiled(cle),
                                getTargetNameCompiled(cle),
                                cle.getInflictorName() != null ? String.format(" with %s", cle.getInflictorName()) : "",
                                cle.getValue(),
                                cle.getHealth() != 0
                                        ? String.format(" (%s->%s)", cle.getHealth() + cle.getValue(), cle.getHealth())
                                        : "");
                    }
                }
                break;
            // case DOTA_COMBATLOG_MODIFIER_ADD:
            // log.info("{} {} receives {} buff/debuff from {}",
            // time,
            // getTargetNameCompiled(cle),
            // cle.getInflictorName(),
            // getAttackerNameCompiled(cle)
            // );
            // break;
            // case DOTA_COMBATLOG_MODIFIER_REMOVE:
            // log.info("{} {} loses {} buff/debuff",
            // time,
            // getTargetNameCompiled(cle),
            // cle.getInflictorName()
            // );
            // break;
            case DOTA_COMBATLOG_ABILITY:
                if ("earthshaker_echo_slam".equals(cle.getInflictorName()) || "tidehunter_ravage".equals(
                        cle.getInflictorName() || "magnataur_reverse_polarity".equals(cle.getInflictorName()))) { 
                    SpellInfo spellInfo = spellInfoMap.get(cle.getInflictorName());
                    if (spellInfo == null) {
                        spellInfo = new SpellInfo();
                        spellInfoMap.put(cle.getInflictorName(), spellInfo);
                    }

                    // Append the timestamp to echo casts
                    spellInfo.casts.add(String.valueOf(cle.getTimestamp()));

                    log.info("{} {} {} ability {} (lvl {}){}{}",
                            cle.getTimestamp(),
                            getAttackerNameCompiled(cle),
                            cle.isAbilityToggleOn() || cle.isAbilityToggleOff() ? "toggles" : "casts",
                            cle.getInflictorName(),
                            cle.getAbilityLevel(),
                            cle.isAbilityToggleOn() ? " on" : cle.isAbilityToggleOff() ? " off" : "",
                            cle.getTargetName() != null ? " on " + getTargetNameCompiled(cle) : "",
                            cle.getAttackerName() != null ? "" : "");
                }
                break;

        }
    }

    public void run(String[] args) throws Exception {
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new MappedFileSource(args[0])).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;
        log.info("total time taken: {}s", (tMatch) / 1000.0);
        System.out.println(spellInfoMap);
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

}
