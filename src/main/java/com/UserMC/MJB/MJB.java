package com.UserMC.MJB;

import com.UserMC.MJB.commands.*;
import com.UserMC.MJB.listeners.*;
import com.UserMC.MJB.tabcomplete.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.UserMC.MJB.listeners.ComputerListener;
import com.UserMC.MJB.listeners.PickupNPCListener;

public class MJB extends JavaPlugin {

    private static MJB instance;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private BankNPCListener bankNPCListener;
    private CashItemManager cashItemManager;
    private PlotManager plotManager;
    private DebitCardManager debitCardManager;
    private TerminalManager terminalManager;
    private SupplyOrderManager supplyOrderManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Failed to connect to database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        cashItemManager = new CashItemManager(this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new HousingNPCListener(this), this);
        getServer().getPluginManager().registerEvents(new ComputerListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupNPCListener(this), this);
        getServer().getPluginManager().registerEvents(new ComputerPlaceListener(this), this);

        databaseManager.createTables();
        economyManager = new EconomyManager(this);
        bankNPCListener = new BankNPCListener(this);
        plotManager = new PlotManager(this);
        debitCardManager = new DebitCardManager(this);
        terminalManager = new TerminalManager(this);
        supplyOrderManager = new SupplyOrderManager(this);



        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(bankNPCListener, this);

        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("pay").setTabCompleter(new PayTabCompleter());

        getCommand("deposit").setExecutor(new DepositCommand(this));
        getCommand("deposit").setTabCompleter(new DepositWithdrawTabCompleter());

        getCommand("withdraw").setExecutor(new WithdrawCommand(this));
        getCommand("withdraw").setTabCompleter(new DepositWithdrawTabCompleter());

        getCommand("terminal").setExecutor(new TerminalCommand(this));
        getCommand("terminal").setTabCompleter(new TerminalTabCompleter());

        getCommand("plotinfo").setExecutor(new PlotInfoCommand(this));
        getCommand("plotinfo").setTabCompleter(new PlotInfoTabCompleter());

        getCommand("mjbadmin").setExecutor(new AdminCommand());
        getCommand("mjbadmin").setTabCompleter(new AdminTabCompleter());

        getCommand("buycard").setExecutor(new BuyCardCommand(this));
        getLogger().info("CityLife Core enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("CityLife Core disabled.");
    }

    public static MJB getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public BankNPCListener getBankNPCListener() { return bankNPCListener; }
    public CashItemManager getCashItemManager() { return cashItemManager; }
    public PlotManager getPlotManager() { return plotManager; }
    public DebitCardManager getDebitCardManager() { return debitCardManager; }
    public TerminalManager getTerminalManager() { return terminalManager; }
    public SupplyOrderManager getSupplyOrderManager() { return supplyOrderManager; }
}