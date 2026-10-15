package de.frauas.girokonto.market;

import java.util.List;

/**
 * Quelle für Marktplatz-Angebote. Heute gibt es nur {@link SimulatedMarketProvider}; ein echter Anbieter
 * (Vergleichsportal, Bank-API) kann später über diese Schnittstelle angebunden werden – nur mit Vertrag,
 * Zugangsdaten und ausdrücklicher Freigabe.
 */
public interface MarketProvider {

    /** Kennung, z. B. SIMULATED. */
    String id();

    /** true, wenn die Angebote erfunden sind (Simulation). */
    boolean simulated();

    List<MarketProduct> products();
}
