package com.UserMC.MJB;

import com.UserMC.MJB.commands.DepositCommand;
import com.UserMC.MJB.commands.PayCommand;
import com.UserMC.MJB.commands.WithdrawCommand;
import com.UserMC.MJB.listeners.BankNPCListener;
import com.UserMC.MJB.listeners.PlayerJoinListener;
import org.bukkit.plugin.java.JavaPlugin;
import com.UserMC.MJB.commands.AdminCommand;
import com.UserMC.MJB.listeners.PlayerDeathListener;


public class MJB extends JavaPlugin {

    private static MJB instance;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private BankNPCListener bankNPCListener;
    private CashItemManager cashItemManager;

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

        databaseManager.createTables();
        economyManager = new EconomyManager(this);
        bankNPCListener = new BankNPCListener(this);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(bankNPCListener, this);

        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("deposit").setExecutor(new DepositCommand(this));
        getCommand("withdraw").setExecutor(new WithdrawCommand(this));
        getCommand("mjbadmin").setExecutor(new AdminCommand());

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
}