package com.tterrag.iticker.tickers;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tterrag.iticker.util.EmbedCreator;
import com.tterrag.iticker.util.EmbedCreator.Builder;

import lombok.Value;
import reactor.core.publisher.Mono;
import yahoofinance.Stock;

public class TickerEmbedBuilder {
    
    private static final TickerApi API = new TickerApi(System.getProperty("iex.privkey"), System.getProperty("iex.pubkey"));
    
    private final NumberFormat percentFmt = NumberFormat.getPercentInstance();
    {
        percentFmt.setMaximumFractionDigits(2);
    }
    
    private static final Map<Currency, Locale> currencyLocaleMap;
    static {
        currencyLocaleMap = new HashMap<Currency, Locale>();
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                Currency currency = Currency.getInstance(locale);
                currencyLocaleMap.putIfAbsent(currency, locale);
            } catch (Exception e) {
                // SKIP
            }
        }
    }
    
    @Value
    private static class Ticker {
        String symbol;
        boolean advanced;
    }
    
    private final List<Ticker> tickers = new ArrayList<>();
    
    public TickerEmbedBuilder add(String symbol, boolean advanced) {
        this.tickers.add(new Ticker(symbol, advanced));
        return this;
    }
    
    public Mono<EmbedCreator.Builder> build() {
        final Ticker ticker = tickers.get(0); // FIXME
        return Mono.just(ticker)
                .flatMap(t -> API.quickStats(ticker.getSymbol())
                        .flatMap(stock -> t.isAdvanced() ? createAdvancedEmbed(stock) : createEmbed(stock)));
    }

    @SuppressWarnings("serial")
    private Mono<EmbedCreator.Builder> createEmbed(Stock stock) {
        EmbedCreator.Builder embed = EmbedCreator.builder();
        
        NumberFormat currencyFmt;
        try {
            Currency currency = Currency.getInstance(stock.getCurrency());
            currencyFmt = NumberFormat.getCurrencyInstance(currencyLocaleMap.get(currency));
            if (currencyFmt == null) {
                currencyFmt = NumberFormat.getCurrencyInstance();
                currencyFmt.setCurrency(currency);
            }
        } catch (IllegalArgumentException e) { // Unknown currency
            currencyFmt = NumberFormat.getCurrencyInstance();
            DecimalFormatSymbols symbols = ((DecimalFormat)currencyFmt).getDecimalFormatSymbols();
            symbols.setCurrencySymbol(stock.getCurrency());
            ((DecimalFormat)currencyFmt).setDecimalFormatSymbols(symbols);
        }
        if (stock.getQuote().getPrice().abs().compareTo(BigDecimal.ONE) < 0) {
            currencyFmt.setMaximumFractionDigits(6);
        }
        
        int state = stock.getQuote().getChange().signum();
        int color = state < 0 ? 0xef5350 : state > 0 ? 0x26a69a : 0x444444; 
        
        embed
            .color(color)
            .authorName("[" + stock.getSymbol() + "] " + stock.getName())
            .authorUrl("https://finance.yahoo.com/quote/" + stock.getSymbol())
            .title("Current Price")
            .description("**" + currencyFmt.format(stock.getQuote().getPrice()) + "**")
            .field("Change", (state < 0 ? "-" : "") + currencyFmt.format(stock.getQuote().getChange().abs()) + " (" + percentFmt.format(stock.getQuote().getChangeInPercent().divide(new BigDecimal(100))) + ")", true)
            .footerText("as of")
            .timestamp(stock.getQuote().getLastTradeTime().toInstant());
        
        String logoUrl = "https://eodhistoricaldata.com/img/logos/US/" + stock.getSymbol() + ".png";
        return Mono.fromCallable(() -> ((HttpURLConnection) new URL(logoUrl).openConnection()).getResponseCode())
                .map(code -> code >= 400 ? embed : embed.authorIcon(logoUrl))
                .onErrorReturn(embed);
    }
    

    private Mono<Builder> createAdvancedEmbed(Stock stock) {
        return createEmbed(stock)
                .map(embed -> {
                    // TODO what more can we show?
                    return embed;
                });
    }
}
