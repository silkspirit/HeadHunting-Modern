package com.headhunting;

import com.headhunting.abilities.AbilityHandler;
import com.headhunting.boosters.BoosterManager;
import com.headhunting.collector.CollectorListener;
import com.headhunting.collector.CollectorManager;
import com.headhunting.commands.*;
import com.headhunting.hooks.ShopGUIPlusHook;
import com.headhunting.listeners.*;
import com.headhunting.managers.*;
import com.headhunting.items.HeadFactory;
import com.headhunting.items.MaskFactory;
import com.headhunting.shop.SpawnerShopManager;
import com.headhunting.utils.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class HeadHunting extends JavaPlugin {

    private static HeadHunting instance;
    
    // Managers
    private ConfigManager configManager;
    private DataManager dataManager;
    private LeaderboardManager leaderboardManager;
    private LevelManager levelManager;
    private MaskManager maskManager;
    private MissionManager missionManager;
    private DailyMissionManager dailyMissionManager;
    private FishingManager fishingManager;
    private BossBarManager bossBarManager;
    private BoosterManager boosterManager;
    
    // Factories
    private HeadFactory headFactory;
    private MaskFactory maskFactory;
    private com.headhunting.items.MysteryMaskFactory mysteryMaskFactory;
    
    // Handlers
    private AbilityHandler abilityHandler;
    
    // Listeners (stored for cross-reference)
    private DebuffListener debuffListener;
    private MissionListener missionListener;
    private MaskStackListener maskStackListener;
    
    // Hooks
    private ShopGUIPlusHook shopGUIPlusHook;
    
    // Economy
    private Economy economy;

    // Head Collector
    private CollectorManager collectorManager;

    // Spawner Shop
    private SpawnerShopManager spawnerShopManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize message util
        MessageUtil.init(this);
        
        // Initialize NBT utilities (PersistentDataContainer API)
        com.headhunting.utils.NBTUtil.init(this);
        
        // Apply skull anti-stack fix (v2.1.9: per-item NBT approach, no global skull mods)
        com.headhunting.utils.SkullStackFix.apply(getLogger());
        
        // Save default configs
        saveDefaultConfigs();
        
        // Setup economy
        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize head collector manager (needs plugin folder, so early)
        collectorManager = new CollectorManager(this);

        // Initialize spawner shop manager
        spawnerShopManager = new SpawnerShopManager(this);

        // Initialize managers
        initializeManagers();
        
        // Initialize factories
        initializeFactories();
        
        // Initialize handlers
        abilityHandler = new AbilityHandler(this);
        
        // Register listeners
        registerListeners();
        
        // Register commands
        registerCommands();
        
        // Setup integrations
        setupIntegrations();
        
        // Start background tasks
        startTasks();
        
        getLogger().info("========================================");
        getLogger().info("HeadHunting v" + getDescription().getVersion() + " enabled!");
        getLogger().info("Loaded " + configManager.getHeadConfigs().size() + " head types");
        getLogger().info("Loaded " + configManager.getMaskConfigs().size() + " masks");
        getLogger().info("Loaded " + missionManager.getAllMissions().size() + " divine missions");
        getLogger().info("Loaded " + fishingManager.getRewardCount() + " fishing rewards");
        getLogger().info("========================================");
    }
    
    @Override
    public void onDisable() {
        // Clean up boss bars
        if (bossBarManager != null) {
            bossBarManager.cleanup();
        }
        
        // Shutdown leaderboard cache
        if (leaderboardManager != null) {
            leaderboardManager.shutdown();
        }
        
        // Clean up booster manager
        if (boosterManager != null) {
            boosterManager.shutdown();
        }
        
        // Save daily mission state
        if (dailyMissionManager != null) {
            dailyMissionManager.shutdown();
        }
        
        // Save all player data and close database connection (also closes SQLite for dropped_masks)
        if (dataManager != null) {
            dataManager.shutdown();
        }
        
        getLogger().info("HeadHunting has been disabled!");
    }
    
    private void saveDefaultConfigs() {
        saveDefaultConfig();
        
        // Save other config files if they don't exist
        if (!new java.io.File(getDataFolder(), "heads.yml").exists()) {
            saveResource("heads.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "levels.yml").exists()) {
            saveResource("levels.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "masks.yml").exists()) {
            saveResource("masks.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "missions.yml").exists()) {
            saveResource("missions.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "daily-missions.yml").exists()) {
            saveResource("daily-missions.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "fishing.yml").exists()) {
            saveResource("fishing.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "spawner-shop.yml").exists()) {
            saveResource("spawner-shop.yml", false);
        }
    }
    
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
    
    private void initializeManagers() {
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);
        leaderboardManager = new LeaderboardManager(this);
        levelManager = new LevelManager(this);
        maskManager = new MaskManager(this);
        missionManager = new MissionManager(this);
        dailyMissionManager = new DailyMissionManager(this);
        fishingManager = new FishingManager(this);
        bossBarManager = new BossBarManager(this);
        boosterManager = new BoosterManager(this);
    }
    
    private void initializeFactories() {
        headFactory = new HeadFactory(this);
        maskFactory = new MaskFactory(this);
        mysteryMaskFactory = new com.headhunting.items.MysteryMaskFactory(this);
    }
    
    private void registerListeners() {
        // Core listeners
        getServer().getPluginManager().registerEvents(new MobDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new HeadInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MaskEquipListener(this), this);
        getServer().getPluginManager().registerEvents(new MaskAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        
        // Ability listeners
        getServer().getPluginManager().registerEvents(new PearlListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsumableListener(this), this);
        getServer().getPluginManager().registerEvents(new HealthListener(this), this);
        
        debuffListener = new DebuffListener(this);
        getServer().getPluginManager().registerEvents(debuffListener, this);
        
        // Mission listener
        missionListener = new MissionListener(this);
        getServer().getPluginManager().registerEvents(missionListener, this);
        
        // Mystery mask listener
        getServer().getPluginManager().registerEvents(new com.headhunting.listeners.MysteryMaskListener(this), this);
        
        // Warzone fishing listener
        getServer().getPluginManager().registerEvents(new WarzoneFishingListener(this), this);
        
        // Mask stacking prevention listener (v2.1.15: chunk-aware + SQLite persistence)
        maskStackListener = new MaskStackListener(this);
        getServer().getPluginManager().registerEvents(maskStackListener, this);
        // Protect any mask items already on the ground
        maskStackListener.protectExistingMasks();
        
        // Booster voucher listener
        getServer().getPluginManager().registerEvents(new BoosterListener(this), this);
        
        // Duel block listener - prevents /duel with mask equipped
        getServer().getPluginManager().registerEvents(new DuelBlockListener(this), this);
        
        // Boss head listener - handles selling/consuming boss heads
        getServer().getPluginManager().registerEvents(new BossHeadListener(this), this);
        
        // Darkzone drop filter - suppresses vanilla drops for FKore mobs
        getServer().getPluginManager().registerEvents(new DarkzoneDropFilter(this), this);

        // Head Collector listener
        getServer().getPluginManager().registerEvents(new CollectorListener(this), this);

        // Spirit Essence listener
        getServer().getPluginManager().registerEvents(new com.headhunting.listeners.SpiritEssenceListener(this), this);
    }
    
    private BulkCommand bulkCommand;
    
    private void registerCommands() {
        getCommand("level").setExecutor(new LevelCommand(this));
        getCommand("masks").setExecutor(new MasksCommand(this));
        getCommand("deposit").setExecutor(new DepositCommand(this));
        getCommand("missions").setExecutor(new MissionsCommand(this));
        getCommand("hhbar").setExecutor(new BossBarCommand(this));
        getCommand("hunter").setExecutor(new HunterCommand(this));
        
        // Bulk commands
        bulkCommand = new BulkCommand(this);
        getCommand("bulksell").setExecutor(bulkCommand);
        getCommand("bulkdeposit").setExecutor(bulkCommand);
        
        // Register bulk chest listener
        getServer().getPluginManager().registerEvents(
            new com.headhunting.listeners.BulkChestListener(this, bulkCommand), this);
        
        // Spawner shop commands
        SpawnerShopCommand spawnerShopCmd = new SpawnerShopCommand(this);
        getCommand("spawners").setExecutor(spawnerShopCmd);
        getCommand("spawnershop").setExecutor(spawnerShopCmd);

        AdminCommand adminCmd = new AdminCommand(this);
        getCommand("hunt").setExecutor(adminCmd);
        getCommand("hunt").setTabCompleter(adminCmd);

        HeadCollectorCommand hcCmd = new HeadCollectorCommand(this);
        getCommand("headcollector").setExecutor(hcCmd);
        getCommand("headcollector").setTabCompleter(hcCmd);
    }
    
    private void setupIntegrations() {
        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new HeadHuntingExpansion(this).register();
            getLogger().info("PlaceholderAPI integration enabled.");
        }
        
        // LuckPerms
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            getLogger().info("LuckPerms integration enabled.");
        }
        
        // Factions
        if (Bukkit.getPluginManager().getPlugin("Factions") != null ||
            Bukkit.getPluginManager().getPlugin("FactionsUUID") != null ||
            Bukkit.getPluginManager().getPlugin("FactionsKore") != null) {
            getLogger().info("Factions integration enabled (warzone fishing).");
        }
        
        // WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            getLogger().info("WorldGuard integration enabled (warzone fishing).");
        }
        
        // ShopGUIPlus
        shopGUIPlusHook = new ShopGUIPlusHook(this);
        if (shopGUIPlusHook.hook()) {
            getLogger().info("ShopGUIPlus integration enabled.");
        }
    }
    
    private void startTasks() {
        // Debuff immunity removal task
        debuffListener.startDebuffRemovalTask();
        
        // Passive ability refresh task (every 5 seconds)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                // Refresh passive abilities
                maskManager.checkAndUpdateAbilities(player);
                
                // Check farming haste
                abilityHandler.checkFarmingHaste(player);
            }
        }, 100L, 100L);
    }
    
    public void reload() {
        // Reload all configs
        reloadConfig();
        configManager.reload();
        missionManager.reload();
        dailyMissionManager.reload();
        fishingManager.reload();
        MessageUtil.reload();
        
        // Refresh leaderboard cache after config reload
        if (leaderboardManager != null) {
            leaderboardManager.refreshAsync();
        }
        
        // Reload spawner shop config
        if (spawnerShopManager != null) {
            spawnerShopManager.load();
        }

        getLogger().info("HeadHunting configuration reloaded!");
    }
    
    // Getters
    public static HeadHunting getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DataManager getDataManager() {
        return dataManager;
    }
    
    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }
    
    public LevelManager getLevelManager() {
        return levelManager;
    }
    
    public MaskManager getMaskManager() {
        return maskManager;
    }
    
    public MissionManager getMissionManager() {
        return missionManager;
    }
    
    public DailyMissionManager getDailyMissionManager() {
        return dailyMissionManager;
    }
    
    public FishingManager getFishingManager() {
        return fishingManager;
    }
    
    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }
    
    public BoosterManager getBoosterManager() {
        return boosterManager;
    }
    
    public HeadFactory getHeadFactory() {
        return headFactory;
    }
    
    public MaskFactory getMaskFactory() {
        return maskFactory;
    }
    
    public com.headhunting.items.MysteryMaskFactory getMysteryMaskFactory() {
        return mysteryMaskFactory;
    }
    
    public AbilityHandler getAbilityHandler() {
        return abilityHandler;
    }
    
    public MissionListener getMissionListener() {
        return missionListener;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    public ShopGUIPlusHook getShopGUIPlusHook() {
        return shopGUIPlusHook;
    }

    public CollectorManager getCollectorManager() {
        return collectorManager;
    }

    public SpawnerShopManager getSpawnerShopManager() {
        return spawnerShopManager;
    }
}
