package com.tterrag.iticker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.function.Function;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.tterrag.iticker.listeners.TickerListener;
import com.tterrag.iticker.util.ServiceManager;
import com.tterrag.iticker.util.Threads;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.ReplayingEventDispatcher;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.shard.GatewayBootstrap;
import discord4j.gateway.GatewayOptions;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class Main {

    public static void main(String[] argv) {
        String protocol = Main.class.getResource("").getProtocol();
        if (!"jar".equals(protocol)) { // Only enable this in IDEs
            Hooks.onOperatorDebug();
        }
        
        new Main().start().block();
    }
    
    @Getter
    private final DiscordClient client;
    @Getter
    private final ServiceManager services;

    private static long initialConnectionTime;
    
    public Main() {
        this.client = DiscordClientBuilder.create(System.getProperty("discord.apitoken"))
                .build();
        
        this.services = new ServiceManager();
    }
    
    public Mono<Void> start() {
        GatewayBootstrap<GatewayOptions> gateway = client.gateway()
        .setInitialStatus(si -> Presence.online(Activity.playing("Stonks")))
        .setEventDispatcher(ReplayingEventDispatcher.builder()
                .replayEventFilter(e -> e instanceof ReadyEvent)
        		.eventScheduler(Schedulers.boundedElastic())
        		.build())
        .setEnabledIntents(IntentSet.of(Intent.GUILDS, Intent.GUILD_MESSAGES, Intent.DIRECT_MESSAGES));
        
        Function<EventDispatcher, Mono<Void>> onInitialReady = events -> events.on(ReadyEvent.class)
                .next()
                .doOnNext($ -> initialConnectionTime = System.currentTimeMillis())
                .then();
        
        services
            .eventService("Setup", ReadyEvent.class, events -> events
                .doOnNext(e -> {
                    log.info("Bot connected, starting up...");
                    log.info("Connected to {} guilds.", e.getGuilds().size());
                })
                .map(e -> e.getClient())
                .flatMap(c -> c.getGuilds() // Print all connected guilds
                    .collectList()
                    .doOnNext(guilds -> guilds.forEach(g -> log.info("\t" + g.getName())))
                )
            .then())
            
            .eventService("Tickers", MessageCreateEvent.class, events -> events.flatMap(TickerListener.INSTANCE::onMessage));
        
        return gateway.login()
                .flatMap(c -> Mono.when(onInitialReady.apply(c.getEventDispatcher()), services.start(c)).thenReturn(c))
                .flatMap(this::teardown);
    }

    private boolean isUser(MessageCreateEvent evt) {
        return evt.getMessage().getAuthor().map(u -> !u.isBot()).orElse(true);
    }

    private Mono<Void> teardown(GatewayDiscordClient gatewayClient) {
        
        // Handle "stop" and any future commands
        Mono<Void> consoleHandler = Mono.<Void>fromCallable(() -> {
            Scanner scan = new Scanner(System.in);
            while (true) {
                while (scan.hasNextLine()) {
                    if (scan.nextLine().equals("stop")) {
                        scan.close();
                        System.exit(0);
                    }
                }
                Threads.sleep(100);
            }
        }).subscribeOn(Schedulers.newSingle("Console Listener", true));
        
        // Make sure shutdown things are run, regardless of where shutdown came from
        // The above System.exit(0) will trigger this hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gatewayClient.logout().block();
        }));
        
        return Mono.zip(consoleHandler, gatewayClient.onDisconnect())
                   .then()
                   .doOnTerminate(() -> log.error("Unexpected completion of main bot subscriber!"));
    }

    public static String getVersion() {
        String ver = Main.class.getPackage().getImplementationVersion();
        if (ver == null) {
            File head = Paths.get(".git", "HEAD").toFile();
            if (head.exists()) {
                try {
                    String refpath = Files.asCharSource(head, Charsets.UTF_8).readFirstLine().replace("ref: ", "");
                    File ref = head.toPath().getParent().resolve(refpath).toFile();
                    String hash = Files.asCharSource(ref, Charsets.UTF_8).readFirstLine();
                    ver = "DEV " + hash.substring(0, 8);
                } catch (IOException e) {
                    log.error("Could not load version from git data: ", e);
                    ver = "DEV";
                }
            } else {
                ver = "DEV (no HEAD)";
            }
        }
        return ver;
    }

    public static long getConnectionTimestamp() {
        return initialConnectionTime;
    }
}
