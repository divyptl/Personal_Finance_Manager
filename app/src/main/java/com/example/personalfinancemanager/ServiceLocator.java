package com.example.personalfinancemanager;

import android.app.Application;
import android.content.Context;

/**
 * Lightweight, manual dependency-injection container.
 *
 * <p>Hilt would be the production-grade choice, but it forces an annotation
 * processor + plugin and would require touching every class that participates
 * in the graph. For a single-module app this hand-rolled locator gives us 90 %
 * of the benefits (single source of truth, mockability, no duplicated state)
 * with zero build-time cost.
 *
 * <p>Resolution order matters: collaborators that depend on others must be
 * created later. The current graph is:
 *
 * <pre>
 *   CredentialManager  (no deps)
 *   AngelOneTokenManager (depends on CredentialManager)
 *   OkHttpClient (depends on nothing app-specific)
 *   BrokerApi -> AngelOneBrokerApi (depends on the three above)
 *   TransactionRepository / StockRepository (depend on Application)
 *   SmsTransactionParser (no deps — pure)
 * </pre>
 */
public final class ServiceLocator {

    private static volatile ServiceLocator INSTANCE;

    private final Application application;
    private final CredentialManager credentialManager;
    private final AngelOneTokenManager tokenManager;
    private final NetworkModule networkModule;
    private final BrokerApi brokerApi;
    private final TransactionRepository transactionRepository;
    private final StockRepository stockRepository;
    private final SmsTransactionParser smsParser;

    private ServiceLocator(Application application) {
        this.application = application;
        this.credentialManager = new CredentialManager(application);
        this.tokenManager = new AngelOneTokenManager(credentialManager);
        this.networkModule = new NetworkModule();
        this.brokerApi = new AngelOneBrokerApi(
                credentialManager,
                tokenManager,
                networkModule.okHttpClient()
        );
        this.transactionRepository = new TransactionRepository(application);
        this.stockRepository = new StockRepository(application);
        this.smsParser = new SmsTransactionParser();
    }

    /** Called from {@link WealthFlowApplication#onCreate()}. Idempotent. */
    public static void initialize(Application application) {
        if (INSTANCE == null) {
            synchronized (ServiceLocator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ServiceLocator(application);
                }
            }
        }
    }

    /**
     * Returns the locator. If the Application class wasn't registered (e.g.
     * during a unit test that bypasses {@link WealthFlowApplication}),
     * lazily initializes from the supplied context. Production code should
     * always have called {@link #initialize} first.
     */
    public static ServiceLocator get(Context context) {
        if (INSTANCE == null) {
            Context app = context.getApplicationContext();
            if (app instanceof Application) {
                initialize((Application) app);
            } else {
                throw new IllegalStateException(
                        "ServiceLocator not initialized and context is not an Application");
            }
        }
        return INSTANCE;
    }

    public CredentialManager credentialManager() { return credentialManager; }
    public AngelOneTokenManager tokenManager()   { return tokenManager; }
    public BrokerApi brokerApi()                 { return brokerApi; }
    public TransactionRepository transactionRepository() { return transactionRepository; }
    public StockRepository stockRepository()     { return stockRepository; }
    public SmsTransactionParser smsParser()      { return smsParser; }
}
