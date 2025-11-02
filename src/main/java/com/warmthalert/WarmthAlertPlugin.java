package com.warmthalert;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.GameTick;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = "Warmth Alert",
        description = "Warns you when 'Your Warmth' drops below a threshold",
        tags = {"wintertodt","warmth","notification"}
)
public class WarmthAlertPlugin extends Plugin
{
    private static final Pattern PCT_PATTERN = Pattern.compile("(\\d+)\\s*%");

    @Inject private Client client;
    @Inject private Notifier notifier;
    @Inject private WarmthAlertConfig config;

    private long lastAlertMillis = 0L;

    @Provides
    WarmthAlertConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(WarmthAlertConfig.class);
    }

    @Override
    protected void startUp()
    {
        lastAlertMillis = 0L;
        log.info("Warmth Alert started");
    }

    @Override
    protected void shutDown()
    {
        log.info("Warmth Alert stopped");
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        // Try to find and parse "Your Warmth: xx%" from any visible widget text
        Integer warmth = findWarmthPercentage();
        if (warmth == null)
        {
            return;
        }

        // Show current % in the logs occasionally (optional)
        // log.debug("Warmth: {}%", warmth);

        if (warmth <= config.threshold())
        {
            long now = System.currentTimeMillis();
            if (now - lastAlertMillis >= config.cooldownMs())
            {
                lastAlertMillis = now;
                fireAlerts(warmth);
            }
        }
    }

    private void fireAlerts(int warmth)
    {
        final String msg = String.format("Warmth LOW: %d%% — move to the brazier/camp or eat!", warmth);

        if (config.desktopNotify())
        {
            notifier.notify(msg);
        }

        if (config.playSound())
        {
            // Loud, distinct sound; swap to another SoundEffectID if you prefer
            client.playSoundEffect(SoundEffectID.TUTORIAL_COMPLETE);
        }

        if (config.chatEcho())
        {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", msg, null);
        }
    }

    /**
     * Walk all widgets looking for a text like "Your Warmth: 37%".
     * This avoids hardcoding widget IDs and works with different client layouts.
     */
    private Integer findWarmthPercentage()
    {
        if (client.getWidgetRoots() == null)
        {
            return null;
        }

        for (Widget root : client.getWidgetRoots())
        {
            Integer v = scanWidgetTree(root);
            if (v != null) return v;
        }
        return null;
    }

    private Integer scanWidgetTree(Widget w)
    {
        if (w == null || w.isHidden()) return null;

        // Check this widget's text
        String t = w.getText();
        if (t != null && !t.isEmpty())
        {
            final String norm = t.toLowerCase(Locale.ROOT).trim();
            // Accept both "Your Warmth:" and "Warmth:" just in case
            if (norm.contains("warmth"))
            {
                Matcher m = PCT_PATTERN.matcher(norm);
                if (m.find())
                {
                    try
                    {
                        int pct = Integer.parseInt(m.group(1));
                        if (pct >= 0 && pct <= 100)
                        {
                            return pct;
                        }
                    }
                    catch (NumberFormatException ignored) {}
                }
            }
        }

        // Recurse into children
        Widget[] kids = w.getDynamicChildren();
        if (kids != null)
        {
            for (Widget c : kids)
            {
                Integer v = scanWidgetTree(c);
                if (v != null) return v;
            }
        }

        kids = w.getStaticChildren();
        if (kids != null)
        {
            for (Widget c : kids)
            {
                Integer v = scanWidgetTree(c);
                if (v != null) return v;
            }
        }

        kids = w.getNestedChildren();
        if (kids != null)
        {
            for (Widget c : kids)
            {
                Integer v = scanWidgetTree(c);
                if (v != null) return v;
            }
        }

        return null;
    }
}
