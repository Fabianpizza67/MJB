package com.UserMC.MJB;

import com.UserMC.MJB.commands.*;
import com.UserMC.MJB.listeners.*;
import com.UserMC.MJB.tabcomplete.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
    private CompanyManager companyManager;
    private CompanyComputerListener companyComputerListener;
    private PropertyManager propertyManager;
    private RealEstateNPCListener realEstateNPCListener;
    private LicenseManager licenseManager;
    private ClothingManager clothingManager;
    private ThirstManager thirstManager;
    private CraftingLicenseManager craftingLicenseManager;
    private WeaponManager weaponManager;
    private BlackMarketListener blackMarketListener;
    private PoliceManager policeManager;
    private CrimeManager crimeManager;
    private PoliceBudgetManager policeBudgetManager;
    private GovernmentManager governmentManager;
    private PhoneManager phoneManager;
    private ProximityChatListener proximityChatListener;
    private NameTagManager nameTagManager;
    private TimeSyncManager timeSyncManager;
    private VehicleManager vehicleManager;
    private VehicleLicenseListener vehicleLicenseListener;
    private TutorialManager tutorialManager;
    private HospitalManager hospitalManager;
    private HospitalBudgetManager hospitalBudgetManager;
    private MedicalRecordManager medicalRecordManager;
    private IDCardManager idCardManager;
    private JailManager jailManager;
    private RadioManager radioManager;
    private SophieManager sophieManager;
    private DrugManager drugManager;
    private StarterStoreNPCListener starterStoreNPCListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1. Database first
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Failed to connect to database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        databaseManager.createTables();
        databaseManager.startKeepAlive();

        // 2. Managers — must come before any listener registration
        cashItemManager = new CashItemManager(this);
        economyManager = new EconomyManager(this);
        plotManager = new PlotManager(this);
        debitCardManager = new DebitCardManager(this);
        terminalManager = new TerminalManager(this);
        supplyOrderManager = new SupplyOrderManager(this);
        bankNPCListener = new BankNPCListener(this);
        companyManager = new CompanyManager(this);
        companyComputerListener = new CompanyComputerListener(this);
        companyManager.startCompanySalaryScheduler();
        propertyManager = new PropertyManager(this);
        realEstateNPCListener = new RealEstateNPCListener(this);
        licenseManager = new LicenseManager(this);
        clothingManager = new ClothingManager(this);
        clothingManager.startDrainScheduler();
        thirstManager = new ThirstManager(this);
        thirstManager.startDrainScheduler();
        craftingLicenseManager = new CraftingLicenseManager(this);
        weaponManager = new WeaponManager(this);
        blackMarketListener = new BlackMarketListener(this);
        blackMarketListener.startTeleportScheduler();
        policeManager = new PoliceManager(this);
        policeManager.startEscortScheduler();
        crimeManager = new CrimeManager(this);
        policeBudgetManager = new PoliceBudgetManager(this);
        policeBudgetManager.startSchedulers();
        supplyOrderManager.recoverPendingOrders();
        policeBudgetManager.recoverPendingRequisitions();
        governmentManager = new GovernmentManager(this);
        governmentManager.init();
        governmentManager.startSessionScheduler();
        governmentManager.startElectionScheduler();
        governmentManager.startCouncilSalaryScheduler();
        phoneManager = new PhoneManager(this);
        proximityChatListener = new ProximityChatListener(this);
        nameTagManager = new NameTagManager(this);
        timeSyncManager = new TimeSyncManager(this);
        timeSyncManager.startSyncScheduler();
        tutorialManager = new TutorialManager(this);
        hospitalManager = new HospitalManager(this);
        hospitalBudgetManager = new HospitalBudgetManager(this);
        hospitalBudgetManager.startSchedulers();
        hospitalManager.startBleedoutScheduler();
        hospitalManager.startCarryScheduler();
        medicalRecordManager = new MedicalRecordManager(this);
        medicalRecordManager.startAddictionScheduler();
        idCardManager = new IDCardManager(this);
        drugManager = new DrugManager(this);
        drugManager.startAddictionScheduler();
        radioManager  = new RadioManager(this);
        sophieManager = new SophieManager(this);
        jailManager = new JailManager(this);
        jailManager.recoverPendingReleases();

        // 3. Listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new HousingNPCListener(this), this);
        getServer().getPluginManager().registerEvents(bankNPCListener, this);
        getServer().getPluginManager().registerEvents(new TerminalListener(this), this);
        getServer().getPluginManager().registerEvents(new ComputerListener(this), this);
        getServer().getPluginManager().registerEvents(new ComputerPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupNPCListener(this), this);
        getServer().getPluginManager().registerEvents(new GovernmentNPCListener(this), this);
        getServer().getPluginManager().registerEvents(companyComputerListener, this);
        getServer().getPluginManager().registerEvents(realEstateNPCListener, this);
        getServer().getPluginManager().registerEvents(new ThirstListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingLicenseListener(this), this);
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);
        getServer().getPluginManager().registerEvents(blackMarketListener, this);
        getServer().getPluginManager().registerEvents(new PoliceListener(this), this);
        getServer().getPluginManager().registerEvents(new CrimeListener(this), this);
        getServer().getPluginManager().registerEvents(new PoliceStationListener(this), this);
        getServer().getPluginManager().registerEvents(new ElectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PhoneListener(this), this);
        getServer().getPluginManager().registerEvents(proximityChatListener, this);
        getServer().getPluginManager().registerEvents(new HospitalListener(this), this);
        getServer().getPluginManager().registerEvents(new HospitalNPCListener(this), this);
        getServer().getPluginManager().registerEvents(new IDCardListener(this), this);
        getServer().getPluginManager().registerEvents(new DrugListener(this), this);
        getServer().getPluginManager().registerEvents(new IDCardListener(this), this);
        getServer().getPluginManager().registerEvents(new StarterStoreNPCListener(this), this);

        // 4. Commands
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
        getCommand("cancelcard").setExecutor(new CancelCardCommand(this));

        getCommand("acceptjob").setExecutor(new AcceptJobCommand(this));

        getCommand("declinejob").setExecutor(new DeclineJobCommand(this));

        getCommand("police").setExecutor(new PoliceCommand(this));
        getCommand("police").setTabCompleter(new PoliceCommand(this));

        getCommand("911").setExecutor(new EmergencyCommand(this));
        getCommand("112").setExecutor(new EmergencyCommand(this));

        getCommand("party").setExecutor(new GovernmentCommand(this));
        getCommand("party").setTabCompleter(new GovernmentCommand(this));

        getCommand("laws").setExecutor(new GovernmentCommand(this));

        getCommand("mayor").setExecutor(new GovernmentCommand(this));
        getCommand("mayor").setTabCompleter(new GovernmentCommand(this));

        getCommand("government").setExecutor(new GovernmentCommand(this));

        getCommand("council").setExecutor(new CouncilCommand(this));
        getCommand("council").setTabCompleter(new CouncilCommand(this));

        getCommand("acceptsale").setExecutor(new AcceptSaleCommand(this));
        getCommand("declinesale").setExecutor(new DeclineSaleCommand(this));

        getCommand("tutorial").setExecutor(new TutorialCommand(this));
        getCommand("tutorial").setTabCompleter(new TutorialCommand(this));

        getCommand("medrecord").setExecutor(new MedRecordCommand(this));
        getCommand("medrecord").setTabCompleter(new MedRecordCommand(this));

        getCommand("buyphone").setExecutor(new BuyPhoneCommand(this));

        getCommand("uncuff").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player officer)) return true;
            if (!getPoliceManager().isOfficer(officer.getUniqueId())) {
                officer.sendMessage("§4You are not a police officer.");
                return true;
            }
            // Find the player this officer has cuffed
            java.util.UUID cuffedUuid = null;
            for (java.util.UUID uuid : getPoliceManager().getCuffedPlayers()) {
                if (officer.getUniqueId().equals(
                        getPoliceManager().getCuffingOfficer(uuid))) {
                    cuffedUuid = uuid;
                    break;
                }
            }
            if (cuffedUuid == null) {
                officer.sendMessage("§4You don't have anyone cuffed.");
                return true;
            }
            org.bukkit.entity.Player target =
                    getServer().getPlayer(cuffedUuid);
            getPoliceManager().uncuff(cuffedUuid);
            officer.sendMessage("§f§l[Police] §fYou released §b" +
                    (target != null ? target.getName() : "the suspect") + "§f.");
            if (target != null) {
                target.sendMessage("§f§l[Police] §fYou have been released by §b" +
                        officer.getName() + "§f.");
            }
            return true;
        });

        getCommand("judge").setExecutor(new JudgeCommand(this));
        getCommand("judge").setTabCompleter(new JudgeCommand(this));

        getCommand("radio").setExecutor(new RadioCommand(this));
        getCommand("radio").setTabCompleter(new RadioCommand(this));

        getCommand("ask").setExecutor(new AskCommand(this));

        getCommand("answercall").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            if (!phoneManager.hasPendingCall(player.getUniqueId())) {
                player.sendMessage("§4You don't have an incoming call.");
                return true;
            }
            phoneManager.acceptCall(player.getUniqueId());
            return true;
        });

        getCommand("declinecall").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            if (!phoneManager.hasPendingCall(player.getUniqueId())) {
                player.sendMessage("§4You don't have an incoming call.");
                return true;
            }
            phoneManager.declineCall(player.getUniqueId());
            player.sendMessage("§7Call declined.");
            return true;
        });

        getCommand("endcall").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            if (!phoneManager.isInCall(player.getUniqueId())) {
                player.sendMessage("§4You are not in a call.");
                return true;
            }
            phoneManager.endCall(player.getUniqueId());
            player.sendMessage("§7Call ended.");
            return true;
        });
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            for (org.bukkit.OfflinePlayer p : getServer().getOfflinePlayers()) {
                medicalRecordManager.assignBloodType(p.getUniqueId());
            }
        });


        getLogger().info("MJB Enabled succesfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("MJB Disabled.");
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
    public CompanyManager getCompanyManager() { return companyManager; }
    public CompanyComputerListener getCompanyComputerListener() { return companyComputerListener; }
    public PropertyManager getPropertyManager() { return propertyManager; }
    public RealEstateNPCListener getRealEstateNPCListener() { return realEstateNPCListener; }
    public LicenseManager getLicenseManager() { return licenseManager; }
    public ClothingManager getClothingManager() { return clothingManager; }
    public ThirstManager getThirstManager() { return thirstManager; }
    public CraftingLicenseManager getCraftingLicenseManager() { return craftingLicenseManager; }
    public WeaponManager getWeaponManager() { return weaponManager; }
    public BlackMarketListener getBlackMarketListener() { return blackMarketListener; }
    public PoliceManager getPoliceManager() { return policeManager; }
    public CrimeManager getCrimeManager() { return crimeManager; }
    public PoliceBudgetManager getPoliceBudgetManager() { return policeBudgetManager; }
    public GovernmentManager getGovernmentManager() { return governmentManager; }
    public PhoneManager getPhoneManager() { return phoneManager; }
    public NameTagManager getNameTagManager() { return nameTagManager; }
    public TimeSyncManager getTimeSyncManager() { return timeSyncManager; }
    public VehicleManager getVehicleManager() { return vehicleManager; }
    public VehicleLicenseListener getVehicleLicenseListener() { return vehicleLicenseListener; }
    public TutorialManager getTutorialManager() { return tutorialManager; }
    public HospitalManager getHospitalManager() { return hospitalManager; }
    public HospitalBudgetManager getHospitalBudgetManager() { return hospitalBudgetManager; }
    public MedicalRecordManager getMedicalRecordManager() { return medicalRecordManager; }
    public IDCardManager getIDCardManager() { return idCardManager; }
    public JailManager getJailManager() { return jailManager; }
    public RadioManager getRadioManager()   { return radioManager; }
    public SophieManager getSophieManager() { return sophieManager; }
    public DrugManager getDrugManager() { return drugManager; }

}