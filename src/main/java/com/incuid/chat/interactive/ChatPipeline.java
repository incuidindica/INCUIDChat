package com.incuid.chat.interactive;

import com.incuid.chat.config.ChatSettings;
import com.incuid.chat.mentions.MentionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.*;
import java.util.regex.Pattern;

/** Single-pass processing preserves hoverable tokens and highlighted mentions simultaneously. */
public final class ChatPipeline {
    private ChatPipeline() { }
    private static final Pattern TOKEN=Pattern.compile("\\[(item|coords)]",Pattern.CASE_INSENSITIVE);
    public static MentionService.Output render(Component original,InteractiveTags.Snapshot tags,
            List<MentionService.Target> online,boolean everyoneAllowed,ChatSettings cfg,
            int itemLimit,int coordLimit) {
        String source=PlainTextComponentSerializer.plainText().serialize(original);
        var matcher=TOKEN.matcher(source);
        Component result=Component.empty();
        Set<UUID> mentioned=new HashSet<>(); boolean everyone=false;
        int pos=0,items=0,coords=0;
        while(matcher.find()) {
            MentionService.Output part=mention(source.substring(pos,matcher.start()),online,everyoneAllowed,cfg);
            result=result.append(part.component());mentioned.addAll(part.mentioned());everyone|=part.everyone();
            String token=matcher.group(1);
            if(token.equalsIgnoreCase("item") && tags.itemAllowed() && items++<itemLimit)
                result=result.append(tags.item());
            else if(token.equalsIgnoreCase("coords") && tags.coordsAllowed() && coords++<coordLimit)
                result=result.append(tags.coordinates());
            else result=result.append(Component.text(matcher.group()));
            pos=matcher.end();
        }
        if(pos==0) return mention(source,online,everyoneAllowed,cfg);
        MentionService.Output tail=mention(source.substring(pos),online,everyoneAllowed,cfg);
        result=result.append(tail.component());mentioned.addAll(tail.mentioned());everyone|=tail.everyone();
        return new MentionService.Output(result,Set.copyOf(mentioned),everyone);
    }
    private static MentionService.Output mention(String text,List<MentionService.Target> online,boolean everyone,ChatSettings cfg) {
        if (!cfg.mentionEnabled()) return new MentionService.Output(Component.text(text),Set.of(),false);
        return MentionService.highlight(Component.text(text),online,everyone,cfg);
    }
}
