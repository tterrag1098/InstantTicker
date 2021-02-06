package com.tterrag.iticker.listeners;

import java.util.regex.Matcher;

import com.tterrag.iticker.tickers.TickerEmbedBuilder;
import com.tterrag.iticker.util.BakedMessage;
import com.tterrag.iticker.util.Patterns;

import discord4j.core.event.domain.message.MessageCreateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public enum TickerListener {
    
    INSTANCE;
    
    public Mono<MessageCreateEvent> onMessage(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getGuildId())
             .flatMap($ -> Mono.justOrEmpty(event.getMessage().getContent()))
             .map(Patterns.TICKER::matcher)
             .filter(Matcher::find)
             .flatMap(matcher -> {
                 String ticker = matcher.group(1);
                 return new TickerEmbedBuilder().add(ticker, matcher.group(2).equals("+")).build()
                         .flatMap(embed -> event.getMessage().getChannel()
                                 .flatMap(chan -> new BakedMessage().withEmbed(embed).send(chan)));
             })
             .doOnError(t -> log.error("Exception processing ticker:", t))
             .onErrorResume(e -> event.getMessage().getChannel().flatMap(c -> c.createMessage("Exception processing ticker: " + e.toString())))
             .onErrorResume($ -> Mono.empty()) // Ignore errors from posting errors
             .thenReturn(event);
    }
}
