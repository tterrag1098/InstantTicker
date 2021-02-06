package com.tterrag.iticker.tickers;

import reactor.core.publisher.Mono;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

public class TickerApi {
    
//    final IEXCloudClient cloudClient;

    public TickerApi(String privkey, String pubkey) {
//        this.cloudClient = IEXTradingClient.create(privkey.startsWith("Tsk_") ? IEXTradingApiVersion.IEX_CLOUD_STABLE_SANDBOX : IEXTradingApiVersion.IEX_CLOUD_STABLE,
//                new IEXCloudToken(privkey, pubkey));
    }
    
    public Mono<Stock> quickStats(String ticker) {
        return Mono.fromCallable(() -> YahooFinance.get(ticker));
//        RestRequest<Quote> request = new QuoteRequestBuilder()
//                .withSymbol(ticker)
//                .build();        
//        return Mono.fromCallable(() -> cloudClient.executeRequest(request));
    }
}
